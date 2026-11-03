package com.anjo.statisticservice.service

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val TOKEN_BYTES = 32

fun newToken(): String = base64Url(randomBytes(TOKEN_BYTES))

fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

internal fun randomBytes(count: Int) = ByteArray(count).also(SecureRandom()::nextBytes)
