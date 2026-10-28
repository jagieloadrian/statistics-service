package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.AuthTokens
import com.anjo.statisticservice.db.Users
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant

data class TokenOwner(val userId: Long, val role: String, val lastSeen: Instant)

object TokenRepository {
    fun insert(tokenHash: ByteArray, userId: Long) {
        AuthTokens.insert {
            it[this.tokenHash] = tokenHash
            it[this.userId] = userId
        }
    }

    fun findOwner(tokenHash: ByteArray, seenAfter: Instant): TokenOwner? =
        (AuthTokens innerJoin Users).select(Users.id, Users.role, AuthTokens.lastSeen)
            .where { (AuthTokens.tokenHash eq tokenHash) and (AuthTokens.lastSeen greater seenAfter) }
            .singleOrNull()?.let { TokenOwner(it[Users.id], it[Users.role], it[AuthTokens.lastSeen]) }

    fun touch(tokenHash: ByteArray) {
        AuthTokens.update({ AuthTokens.tokenHash eq tokenHash }) { it[lastSeen] = CurrentTimestamp }
    }

    fun delete(tokenHash: ByteArray) {
        AuthTokens.deleteWhere { AuthTokens.tokenHash eq tokenHash }
    }

    fun deleteOthers(userId: Long, keepHash: ByteArray) {
        AuthTokens.deleteWhere { (AuthTokens.userId eq userId) and (AuthTokens.tokenHash neq keepHash) }
    }
}
