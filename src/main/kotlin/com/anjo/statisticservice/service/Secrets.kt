package com.anjo.statisticservice.service

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Passwords {
    private const val SCHEME = "pbkdf2"
    private const val ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val FIELD_SEPARATOR = '$'
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    private val timingDecoyHash by lazy { hash("timing-decoy-password") }

    fun hash(password: String): String {
        val salt = randomBytes(SALT_BYTES)
        val key = derive(password, salt, ITERATIONS)
        return listOf(
            SCHEME,
            ITERATIONS.toString(),
            encoder.encodeToString(salt),
            encoder.encodeToString(key),
        ).joinToString(FIELD_SEPARATOR.toString())
    }

    fun verify(
        password: String,
        storedHash: String?,
    ): Boolean {
        val fields = (storedHash ?: timingDecoyHash).split(FIELD_SEPARATOR)
        if (fields.size != 4 || fields[0] != SCHEME) return false
        val (_, iterations, salt, key) = fields
        val matches = MessageDigest.isEqual(decoder.decode(key), derive(password, decoder.decode(salt), iterations.toInt()))
        return matches && storedHash != null
    }

    private fun derive(
        password: String,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray =
        SecretKeyFactory
            .getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS))
            .encoded
}

private const val TOKEN_BYTES = 32

fun newToken(): String = base64Url(randomBytes(TOKEN_BYTES))

fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

private fun randomBytes(count: Int) = ByteArray(count).also(SecureRandom()::nextBytes)
