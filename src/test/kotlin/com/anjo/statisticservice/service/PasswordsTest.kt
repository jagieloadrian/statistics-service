package com.anjo.statisticservice.service

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PasswordsTest :
    FunSpec({
        test("a hash verifies only its own password and is salted") {
            val hash = Passwords.hash("correct horse")
            Passwords.verify("correct horse", hash) shouldBe true
            Passwords.verify("wrong horse", hash) shouldBe false
            Passwords.hash("correct horse") shouldNotBe hash
        }

        test("a missing hash never matches, not even the timing decoy's password") {
            Passwords.verify("anything", null) shouldBe false
            Passwords.verify("timing-decoy-password", null) shouldBe false
        }

        test("hashes of another scheme or shape are refused") {
            val (_, iterations, salt, key) = Passwords.hash("pw").split('$')
            listOf("garbage", "bcrypt\$$iterations\$$salt\$$key", "pbkdf2\$$iterations\$$salt").forEach { stored ->
                withClue(stored) { Passwords.verify("pw", stored) shouldBe false }
            }
        }
    })
