package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.query
import com.anjo.statisticservice.db.update
import java.sql.Connection

object TokenRepository {

    fun insert(c: Connection, tokenHash: ByteArray, userId: Long) {
        c.update("INSERT INTO auth_tokens (token_hash, user_id) VALUES (?, ?)", tokenHash, userId)
    }

    /** User id and role behind a token used within 7 days, and whether `last_seen` is over a minute old. */
    fun find(c: Connection, tokenHash: ByteArray): Triple<Long, String, Boolean>? =
        c.query(
            """SELECT u.id, u.role, t.last_seen < now() - interval '1 minute' FROM auth_tokens t JOIN users u ON u.id = t.user_id
               WHERE t.token_hash = ? AND t.last_seen > now() - interval '7 days'""",
            tokenHash,
        ) { Triple(it.getLong(1), it.getString(2), it.getBoolean(3)) }.firstOrNull()

    fun touch(c: Connection, tokenHash: ByteArray) {
        c.update("UPDATE auth_tokens SET last_seen = now() WHERE token_hash = ?", tokenHash)
    }

    fun delete(c: Connection, tokenHash: ByteArray) {
        c.update("DELETE FROM auth_tokens WHERE token_hash = ?", tokenHash)
    }

    fun deleteOthers(c: Connection, userId: Long, keepHash: ByteArray) {
        c.update("DELETE FROM auth_tokens WHERE user_id = ? AND token_hash <> ?", userId, keepHash)
    }
}
