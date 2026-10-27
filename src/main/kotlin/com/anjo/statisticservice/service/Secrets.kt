package com.anjo.statisticservice.service

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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

/** 32 random bytes, URL-safe; stored only as [sha256]. */
fun newToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))

fun sha256(s: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
