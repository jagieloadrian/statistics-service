package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.query
import com.anjo.statisticservice.db.update
import com.anjo.statisticservice.model.Prefs
import com.anjo.statisticservice.model.User
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet

private val prefsJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

object UserRepository {

    fun count(c: Connection, role: String? = null): Long =
        if (role == null) c.query("SELECT count(*) FROM users WHERE org_id = ?", Db.ORG) { it.getLong(1) }.single()
        else c.query("SELECT count(*) FROM users WHERE org_id = ? AND role = ?", Db.ORG, role) { it.getLong(1) }.single()

    fun find(c: Connection, id: Long): User? =
        c.query("SELECT id, email, name, role, prefs::text FROM users WHERE org_id = ? AND id = ?", Db.ORG, id, map = ::toUser).firstOrNull()

    fun list(c: Connection): List<User> =
        c.query("SELECT id, email, name, role, prefs::text FROM users WHERE org_id = ? ORDER BY id", Db.ORG, map = ::toUser)

    /** Id and password hash of the user with this (normalized) email. */
    fun credentials(c: Connection, email: String): Pair<Long, String>? =
        c.query("SELECT id, password_hash FROM users WHERE org_id = ? AND email = ?", Db.ORG, email) { it.getLong(1) to it.getString(2) }.firstOrNull()

    fun passwordHash(c: Connection, id: Long): String =
        c.query("SELECT password_hash FROM users WHERE id = ?", id) { it.getString(1) }.single()

    fun emailTaken(c: Connection, email: String): Boolean =
        c.query("SELECT 1 FROM users WHERE email = ?", email) { 1 }.isNotEmpty()

    fun insert(c: Connection, email: String, name: String, role: String, passwordHash: String): Long =
        c.query(
            "INSERT INTO users (org_id, email, name, password_hash, role) VALUES (?, ?, ?, ?, ?) RETURNING id",
            Db.ORG, email, name, passwordHash, role,
        ) { it.getLong(1) }.single()

    fun update(c: Connection, id: Long, name: String, role: String) {
        c.update("UPDATE users SET name = ?, role = ? WHERE id = ?", name, role, id)
    }

    fun setPrefs(c: Connection, id: Long, prefs: Prefs) {
        c.update("UPDATE users SET prefs = ?::jsonb WHERE id = ?", prefsJson.encodeToString(Prefs.serializer(), prefs), id)
    }

    fun setPasswordHash(c: Connection, id: Long, hash: String) {
        c.update("UPDATE users SET password_hash = ? WHERE id = ?", hash, id)
    }

    fun delete(c: Connection, id: Long) {
        c.update("DELETE FROM users WHERE id = ?", id)
    }

    private fun toUser(rs: ResultSet) =
        User(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), prefsJson.decodeFromString(Prefs.serializer(), rs.getString(5)))
}
