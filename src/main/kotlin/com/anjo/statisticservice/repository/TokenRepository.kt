package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.AuthTokens
import com.anjo.statisticservice.db.Users
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

object TokenRepository {

    fun insert(tokenHash: ByteArray, userId: Long) {
        AuthTokens.insert {
            it[this.tokenHash] = tokenHash
            it[this.userId] = userId
        }
    }

    /** User id, role and `last_seen` behind a token last used after [seenAfter]. */
    fun find(tokenHash: ByteArray, seenAfter: OffsetDateTime): Triple<Long, String, OffsetDateTime>? =
        (AuthTokens innerJoin Users).select(Users.id, Users.role, AuthTokens.lastSeen)
            .where { (AuthTokens.tokenHash eq tokenHash) and (AuthTokens.lastSeen greater seenAfter) }
            .singleOrNull()?.let { Triple(it[Users.id], it[Users.role], it[AuthTokens.lastSeen]) }

    fun touch(tokenHash: ByteArray) {
        AuthTokens.update({ AuthTokens.tokenHash eq tokenHash }) { it[lastSeen] = CurrentTimestampWithTimeZone }
    }

    fun delete(tokenHash: ByteArray) {
        AuthTokens.deleteWhere { AuthTokens.tokenHash eq tokenHash }
    }

    fun deleteOthers(userId: Long, keepHash: ByteArray) {
        AuthTokens.deleteWhere { (AuthTokens.userId eq userId) and (AuthTokens.tokenHash neq keepHash) }
    }
}
