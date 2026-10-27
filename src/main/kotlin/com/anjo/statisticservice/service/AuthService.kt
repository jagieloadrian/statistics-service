package com.anjo.statisticservice.service

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.exception.forbidden
import com.anjo.statisticservice.exception.notFound
import com.anjo.statisticservice.exception.unauthorized
import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.VIEWER
import com.anjo.statisticservice.model.UserPrincipal
import com.anjo.statisticservice.model.dto.AuthConfig
import com.anjo.statisticservice.model.dto.LoginResponse
import com.anjo.statisticservice.model.dto.OidcInfo
import com.anjo.statisticservice.repository.TokenRepository
import com.anjo.statisticservice.repository.UserRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** [local] = password sign-in on; [oidc] = the sign-in provider, if configured. */
class AuthService(private val db: Db, private val local: Boolean = true, private val adminEmail: String = "", private val oidc: Oidc? = null) {

    val config = AuthConfig(local, oidc?.let { OidcInfo(it.name) })

    /**
     * Creates the first admin from config, only while there are no users at all. Without a password nothing is
     * created; the first provider sign-in with a verified [email] becomes admin instead.
     */
    suspend fun bootstrapAdmin(email: String, password: String): Boolean {
        if (email.isBlank() || password.isBlank()) return false
        return db.tx {
            val empty = UserRepository.count() == 0L
            if (empty) UserRepository.insert(normalizeEmail(email), "Admin", ADMIN, Passwords.hash(password))
            empty
        }
    }

    suspend fun login(email: String, password: String): LoginResponse {
        requireLocal()
        val row = db.tx { UserRepository.credentials(normalizeEmail(email)) }
        if (!Passwords.verify(password, row?.second) || row == null) throw unauthorized("Wrong email or password")
        return issueSession(row.first)
    }

    suspend fun oidcStart(redirectUri: String) = provider().start(redirectUri)

    /**
     * Redeems the provider's [code] and signs in the account linked to that identity. Otherwise links the account with
     * the same email if the provider verified it, or creates one: admin for a verified `ADMIN_EMAIL`, else viewer.
     */
    suspend fun oidcLogin(code: String, codeVerifier: String, redirectUri: String): LoginResponse {
        val p = provider()
        val c = p.redeem(code, codeVerifier, redirectUri)
        val email = c.email?.takeIf { it.isNotBlank() }?.let(::normalizeEmail) ?: throw unauthorized("Provider sent no email")
        val id = db.tx {
            UserRepository.findByOidc(p.issuer, c.subject) ?: UserRepository.findByEmail(email)?.let { (id, linked) ->
                if (!c.emailVerified) throw unauthorized("Email not verified by the provider")
                if (linked) throw unauthorized("Account is linked to another sign-in")
                UserRepository.linkOidc(id, p.issuer, c.subject)
                id
            } ?: run {
                val role = if (c.emailVerified && email == normalizeEmail(adminEmail)) ADMIN else VIEWER
                // ponytail: email/name not synced after creation
                UserRepository.insert(email, c.name ?: email, role, null, p.issuer, c.subject)
            }
        }
        return issueSession(id)
    }

    private fun provider() = oidc ?: throw notFound("No sign-in provider configured")

    private fun requireLocal() {
        if (!local) throw forbidden("Password sign-in is disabled")
    }

    private suspend fun issueSession(userId: Long): LoginResponse {
        val token = newToken()
        val user = db.tx {
            TokenRepository.insert(sha256(token), userId)
            UserRepository.find(userId) ?: throw notFound("Unknown user $userId")
        }
        return LoginResponse(token, user)
    }

    /** Valid while used within 7 days; `last_seen` refreshed at most once a minute. */
    suspend fun principal(token: String): UserPrincipal? = db.tx {
        val h = sha256(token)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        TokenRepository.find(h, seenAfter = now.minusDays(7))?.let { (id, role, lastSeen) ->
            if (lastSeen < now.minusMinutes(1)) TokenRepository.touch(h)
            UserPrincipal(id, role, token)
        }
    }

    suspend fun logout(token: String) {
        db.tx { TokenRepository.delete(sha256(token)) }
    }

    /** Other tokens of the user are revoked; the one making the change stays valid. */
    suspend fun changePassword(u: UserPrincipal, current: String, new: String) {
        requireLocal()
        requirePassword(new)
        val stored = db.tx { UserRepository.passwordHash(u.id) } ?: throw forbidden("Account has no password")
        if (!Passwords.verify(current, stored)) throw badRequest("current: wrong password")
        val hash = Passwords.hash(new)
        db.tx {
            UserRepository.setPasswordHash(u.id, hash)
            TokenRepository.deleteOthers(u.id, sha256(u.token))
        }
    }
}
