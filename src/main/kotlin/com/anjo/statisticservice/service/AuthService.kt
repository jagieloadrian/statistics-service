package com.anjo.statisticservice.service

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.exception.notFound
import com.anjo.statisticservice.exception.unauthorized
import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.UserPrincipal
import com.anjo.statisticservice.model.dto.LoginResponse
import com.anjo.statisticservice.repository.TokenRepository
import com.anjo.statisticservice.repository.UserRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset

class AuthService(private val db: Db) {

    /** Creates the first admin from config, only while there are no users at all. */
    suspend fun bootstrapAdmin(email: String, password: String): Boolean {
        if (email.isBlank() || password.isBlank()) return false
        return db.tx {
            val empty = UserRepository.count() == 0L
            if (empty) UserRepository.insert(normalizeEmail(email), "Admin", ADMIN, Passwords.hash(password))
            empty
        }
    }

    suspend fun login(email: String, password: String): LoginResponse {
        val row = db.tx { UserRepository.credentials(normalizeEmail(email)) }
        if (!Passwords.verify(password, row?.second) || row == null) throw unauthorized("Wrong email or password")
        val token = newToken()
        val user = db.tx {
            TokenRepository.insert(sha256(token), row.first)
            UserRepository.find(row.first) ?: throw notFound("Unknown user ${row.first}")
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
        requirePassword(new)
        val stored = db.tx { UserRepository.passwordHash(u.id) }
        if (!Passwords.verify(current, stored)) throw badRequest("current: wrong password")
        val hash = Passwords.hash(new)
        db.tx {
            UserRepository.setPasswordHash(u.id, hash)
            TokenRepository.deleteOthers(u.id, sha256(u.token))
        }
    }
}
