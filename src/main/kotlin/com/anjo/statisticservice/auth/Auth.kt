package com.anjo.statisticservice.auth

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.query
import com.anjo.statisticservice.db.update
import com.anjo.statisticservice.definitions.sha256
import com.anjo.statisticservice.routing.ApiException
import com.anjo.statisticservice.routing.badRequest
import com.anjo.statisticservice.routing.conflict
import com.anjo.statisticservice.routing.notFound
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.time.ZoneId
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

const val ADMIN = "admin"
const val EDITOR = "editor"
const val VIEWER = "viewer"
private val ROLES = setOf(ADMIN, EDITOR, VIEWER)

@Serializable
data class Prefs(
    val timeZone: String? = null,
    val units: String = "metric",
    val theme: String = "system",
    val homeDashboard: String? = null,
    val favourites: List<String> = emptyList(),
)

@Serializable
data class User(val id: Long, val email: String, val name: String, val role: String, val prefs: Prefs = Prefs())

data class UserPrincipal(val id: Long, val role: String, val token: String)

fun RoutingCall.user(): UserPrincipal = principal<UserPrincipal>()!!

fun RoutingCall.requireRole(vararg roles: String): UserPrincipal =
    user().also { if (it.role !in roles) throw forbidden() }

fun forbidden(message: String = "Not allowed for your role") = ApiException(HttpStatusCode.Forbidden, "forbidden", message)

/** PBKDF2-HMAC-SHA256, stored as `pbkdf2$<iterations>$<salt b64>$<hash b64>` */
object Passwords {
    private const val ITERATIONS = 600_000
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    /** Verified against when the email is unknown, so both cases take the same time. */
    private val dummy by lazy { hash("dummy-password") }

    fun hash(password: String): String {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        return "pbkdf2\$$ITERATIONS\$${b64.encodeToString(salt)}\$${b64.encodeToString(derive(password, salt, ITERATIONS))}"
    }

    fun verify(password: String, stored: String?): Boolean {
        val parts = (stored ?: dummy).split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val ok = MessageDigest.isEqual(unb64.decode(parts[3]), derive(password, unb64.decode(parts[2]), parts[1].toInt()))
        return ok && stored != null
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, 256)).encoded
}

private fun requirePassword(p: String) {
    if (p.length < 10) throw badRequest("password: at least 10 characters")
}

private val prefsJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

class AuthStore(private val db: Db) {

    /** Creates the first admin from config, only while there are no users at all. */
    suspend fun bootstrapAdmin(email: String, password: String): Boolean {
        if (email.isBlank() || password.isBlank()) return false
        return db.tx { c ->
            val empty = c.query("SELECT count(*) FROM users WHERE org_id = ?", Db.ORG) { it.getLong(1) }.single() == 0L
            if (empty) c.insertUser(email, "Admin", ADMIN, password)
            empty
        }
    }

    suspend fun login(email: String, password: String): Pair<String, User>? {
        val row = db.tx { c ->
            c.query("SELECT id, password_hash FROM users WHERE org_id = ? AND email = ?", Db.ORG, normalize(email)) { it.getLong(1) to it.getString(2) }
        }.firstOrNull()
        if (!Passwords.verify(password, row?.second) || row == null) return null
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        db.tx { c -> c.update("INSERT INTO auth_tokens (token_hash, user_id) VALUES (?, ?)", sha256(token), row.first) }
        return token to user(row.first)
    }

    /** Valid while used within 7 days; `last_seen` refreshed at most once a minute. */
    suspend fun principal(token: String): UserPrincipal? = db.tx { c ->
        val h = sha256(token)
        c.query(
            """SELECT u.id, u.role, t.last_seen < now() - interval '1 minute' FROM auth_tokens t JOIN users u ON u.id = t.user_id
               WHERE t.token_hash = ? AND t.last_seen > now() - interval '7 days'""",
            h,
        ) { UserPrincipal(it.getLong(1), it.getString(2), token) to it.getBoolean(3) }
            .firstOrNull()
            ?.let { (p, stale) -> p.also { if (stale) c.update("UPDATE auth_tokens SET last_seen = now() WHERE token_hash = ?", h) } }
    }

    suspend fun logout(token: String) {
        db.tx { c -> c.update("DELETE FROM auth_tokens WHERE token_hash = ?", sha256(token)) }
    }

    suspend fun user(id: Long): User = db.tx { c -> c.user(id) } ?: throw notFound("Unknown user $id")

    suspend fun users(): List<User> = db.tx { c ->
        c.query("SELECT id, email, name, role, prefs::text FROM users WHERE org_id = ? ORDER BY id", Db.ORG, map = ::toUser)
    }

    suspend fun setPrefs(id: Long, p: Prefs): User {
        if (p.units !in setOf("metric", "imperial")) throw badRequest("units: metric | imperial")
        if (p.theme !in setOf("system", "light", "dark")) throw badRequest("theme: system | light | dark")
        if (p.timeZone != null && runCatching { ZoneId.of(p.timeZone) }.isFailure) throw badRequest("timeZone: unknown zone '${p.timeZone}'")
        db.tx { c -> c.update("UPDATE users SET prefs = ?::jsonb WHERE id = ?", prefsJson.encodeToString(Prefs.serializer(), p), id) }
        return user(id)
    }

    /** Other tokens of the user are revoked; the one making the change stays valid. */
    suspend fun changePassword(u: UserPrincipal, current: String, new: String) {
        requirePassword(new)
        val stored = db.tx { c -> c.query("SELECT password_hash FROM users WHERE id = ?", u.id) { it.getString(1) } }.single()
        if (!Passwords.verify(current, stored)) throw badRequest("current: wrong password")
        val hash = Passwords.hash(new)
        db.tx { c ->
            c.update("UPDATE users SET password_hash = ? WHERE id = ?", hash, u.id)
            c.update("DELETE FROM auth_tokens WHERE user_id = ? AND token_hash <> ?", u.id, sha256(u.token))
        }
    }

    suspend fun createUser(email: String, name: String, role: String, password: String): User {
        requirePassword(password)
        if (role !in ROLES) throw badRequest("role: admin | editor | viewer")
        if (!email.contains('@')) throw badRequest("email: not an email address")
        val id = db.tx { c ->
            if (c.query("SELECT 1 FROM users WHERE email = ?", normalize(email)) { 1 }.isNotEmpty()) throw conflict("email: already in use")
            c.insertUser(email, name, role, password)
        }
        return user(id)
    }

    suspend fun updateUser(id: Long, name: String?, role: String?): User {
        if (role != null && role !in ROLES) throw badRequest("role: admin | editor | viewer")
        db.tx { c ->
            val current = c.user(id) ?: throw notFound("Unknown user $id")
            if (current.role == ADMIN && role != null && role != ADMIN) c.requireAnotherAdmin()
            c.update("UPDATE users SET name = ?, role = ? WHERE id = ?", name ?: current.name, role ?: current.role, id)
        }
        return user(id)
    }

    suspend fun deleteUser(id: Long) = db.tx { c ->
        val current = c.user(id) ?: throw notFound("Unknown user $id")
        if (current.role == ADMIN) c.requireAnotherAdmin()
        c.update("DELETE FROM users WHERE id = ?", id)
    }

    private fun Connection.requireAnotherAdmin() {
        val admins = query("SELECT count(*) FROM users WHERE org_id = ? AND role = ?", Db.ORG, ADMIN) { it.getLong(1) }.single()
        if (admins <= 1) throw conflict("The last admin cannot be demoted or deleted")
    }

    private fun Connection.insertUser(email: String, name: String, role: String, password: String): Long =
        query(
            "INSERT INTO users (org_id, email, name, password_hash, role) VALUES (?, ?, ?, ?, ?) RETURNING id",
            Db.ORG, normalize(email), name, Passwords.hash(password), role,
        ) { it.getLong(1) }.single()

    private fun Connection.user(id: Long): User? =
        query("SELECT id, email, name, role, prefs::text FROM users WHERE org_id = ? AND id = ?", Db.ORG, id, map = ::toUser).firstOrNull()

    private fun toUser(rs: java.sql.ResultSet) =
        User(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), prefsJson.decodeFromString(Prefs.serializer(), rs.getString(5)))

    private fun normalize(email: String) = email.trim().lowercase()
}
