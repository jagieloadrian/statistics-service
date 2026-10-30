package com.anjo.statisticservice.service

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.exception.ApiException
import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.EDITOR
import com.anjo.statisticservice.model.Prefs
import com.anjo.statisticservice.model.VIEWER
import com.anjo.statisticservice.testutils.Pg
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

class UserServiceTest :
    FunSpec({
        val db = Db(Pg.container.jdbcUrl, Pg.container.username, Pg.container.password)
        val users = UserService(db)

        beforeTest { Pg.truncateAll() }

        afterSpec { db.close() }

        suspend fun failure(block: suspend () -> Unit) = shouldThrow<ApiException> { block() }

        test("emails are stored normalized and are unique regardless of case") {
            val ann = users.createUser("  Ann@Example.COM ", "Ann", EDITOR, "password-123")
            ann.email shouldBe "ann@example.com"
            failure { users.createUser("ANN@example.com", "Other", VIEWER, "password-123") }.message shouldBe "email: already in use"
            users.setPrefs(ann.id, Prefs(theme = "dark")).prefs.theme shouldBe "dark"
            users.users().map { it.email } shouldBe listOf("ann@example.com")
        }

        test("updates keep what is not given and unknown users are not found") {
            val ann = users.createUser("ann@example.com", "Ann", EDITOR, "password-123")
            users.updateUser(ann.id, null, null).let {
                it.name shouldBe "Ann"
                it.role shouldBe EDITOR
            }
            users.updateUser(ann.id, "Annie", VIEWER).let {
                it.name shouldBe "Annie"
                it.role shouldBe VIEWER
            }
            failure { users.user(404) }.status shouldBe HttpStatusCode.NotFound
            failure { users.updateUser(404, "x", null) }.message shouldBe "Unknown user 404"
            failure { users.deleteUser(404) }.message shouldBe "Unknown user 404"
            users.deleteUser(ann.id)
            failure { users.user(ann.id) }.status shouldBe HttpStatusCode.NotFound
        }

        test("the last admin can be renamed but not demoted or deleted") {
            val root = users.createUser("root@example.com", "Root", ADMIN, "password-123")
            users.updateUser(root.id, "Boss", null).name shouldBe "Boss"
            users.updateUser(root.id, null, ADMIN).role shouldBe ADMIN
            failure { users.updateUser(root.id, null, EDITOR) }.message shouldBe "The last admin cannot be demoted or deleted"
            failure { users.deleteUser(root.id) }.status shouldBe HttpStatusCode.Conflict
            val second = users.createUser("second@example.com", "Second", ADMIN, "password-123")
            users.updateUser(root.id, null, VIEWER).role shouldBe VIEWER
            failure { users.deleteUser(second.id) }.status shouldBe HttpStatusCode.Conflict
            users.updateUser(root.id, null, ADMIN)
            users.deleteUser(second.id)
            users.users().map { it.id } shouldBe listOf(root.id)
        }
    })
