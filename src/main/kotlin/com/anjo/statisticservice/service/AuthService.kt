package com.anjo.statisticservice.service

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.exception.forbidden
import com.anjo.statisticservice.exception.notFound
import com.anjo.statisticservice.exception.unauthorized
import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.UserPrincipal
import com.anjo.statisticservice.model.VIEWER
import com.anjo.statisticservice.model.dto.AuthConfig
import com.anjo.statisticservice.model.dto.LoginResponse
import com.anjo.statisticservice.model.dto.OidcInfo
import com.anjo.statisticservice.model.dto.OidcStartResponse
import com.anjo.statisticservice.model.dto.PubSubInfo
import com.anjo.statisticservice.repository.TokenRepository
import com.anjo.statisticservice.repository.UserRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class AuthService(
    private val db: Db,
    private val localSignIn: Boolean = true,
    private val adminEmail: String = "",
    private val oidc: Oidc? = null,
    pubsub: PubSubInfo? = null,
) {
    val config = AuthConfig(localSignIn, oidc?.let { OidcInfo(it.name) }, pubsub)

    suspend fun bootstrapAdmin(
        email: String,
        password: String,
    ): Boolean {
        if (email.isBlank() || password.isBlank()) return false
        return db.tx {
            val noUsersYet = UserRepository.count() == 0L
            if (noUsersYet) UserRepository.insert(normalizeEmail(email), "Admin", ADMIN, Passwords.hash(password))
            noUsersYet
        }
    }

    suspend fun login(
        email: String,
        password: String,
    ): LoginResponse {
        requireLocalSignIn()
        val credentials = db.tx { UserRepository.credentials(normalizeEmail(email)) }
        val passwordMatches = Passwords.verify(password, credentials?.passwordHash)
        if (credentials == null || !passwordMatches) throw unauthorized("Wrong email or password")
        return issueSession(credentials.userId)
    }

    suspend fun oidcStart(redirectUri: String): OidcStartResponse = provider().start(redirectUri)

    suspend fun oidcLogin(
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): LoginResponse {
        val provider = provider()
        val claims = provider.redeem(code, codeVerifier, redirectUri)
        val email = claims.email?.takeIf { it.isNotBlank() }?.let(::normalizeEmail) ?: throw unauthorized("Provider sent no email")
        val userId =
            db.tx {
                UserRepository.findByOidc(provider.issuer, claims.subject)
                    ?: UserRepository.findByEmail(email)?.let { account ->
                        if (!claims.emailVerified) throw unauthorized("Email not verified by the provider")
                        if (account.linkedToProvider) throw unauthorized("Account is linked to another sign-in")
                        UserRepository.linkOidc(account.userId, provider.issuer, claims.subject)
                        account.userId
                    }
                    ?: run {
                        val role = if (claims.emailVerified && email == normalizeEmail(adminEmail)) ADMIN else VIEWER
                        UserRepository.insert(email, claims.name ?: email, role, null, provider.issuer, claims.subject)
                    }
            }
        return issueSession(userId)
    }

    suspend fun principal(token: String): UserPrincipal? =
        db.tx {
            val tokenHash = sha256(token)
            val now = Clock.System.now()
            TokenRepository.findOwner(tokenHash, seenAfter = now - SESSION_IDLE_TIMEOUT)?.let { owner ->
                if (owner.lastSeen < now - LAST_SEEN_RESOLUTION) TokenRepository.touch(tokenHash)
                UserPrincipal(owner.userId, owner.role, token)
            }
        }

    suspend fun logout(token: String) {
        db.tx { TokenRepository.delete(sha256(token)) }
    }

    suspend fun changePassword(
        user: UserPrincipal,
        current: String,
        new: String,
    ) {
        requireLocalSignIn()
        val stored = db.tx { UserRepository.passwordHash(user.id) } ?: throw forbidden("Account has no password")
        if (!Passwords.verify(current, stored)) throw badRequest("current: wrong password")
        val hash = Passwords.hash(new)
        db.tx {
            UserRepository.setPasswordHash(user.id, hash)
            TokenRepository.deleteOthers(user.id, sha256(user.token))
        }
    }

    private fun provider() = oidc ?: throw notFound("No sign-in provider configured")

    private fun requireLocalSignIn() {
        if (!localSignIn) throw forbidden("Password sign-in is disabled")
    }

    private suspend fun issueSession(userId: Long): LoginResponse {
        val token = newToken()
        val user =
            db.tx {
                TokenRepository.insert(sha256(token), userId)
                UserRepository.find(userId) ?: throw notFound("Unknown user $userId")
            }
        return LoginResponse(token, user)
    }

    private companion object {
        val SESSION_IDLE_TIMEOUT = 7.days
        val LAST_SEEN_RESOLUTION = 1.minutes
    }
}
