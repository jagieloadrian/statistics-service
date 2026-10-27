package com.anjo.statisticservice.service

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.exception.conflict
import com.anjo.statisticservice.exception.notFound
import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.Prefs
import com.anjo.statisticservice.model.ROLES
import com.anjo.statisticservice.model.User
import com.anjo.statisticservice.repository.UserRepository

class UserService(private val db: Db) {

    suspend fun user(id: Long): User = db.tx { UserRepository.find(id) } ?: throw notFound("Unknown user $id")

    suspend fun users(): List<User> = db.tx { UserRepository.list() }

    suspend fun setPrefs(id: Long, p: Prefs): User {
        validatePrefs(p)
        db.tx { UserRepository.setPrefs(id, p) }
        return user(id)
    }

    suspend fun createUser(email: String, name: String, role: String, password: String): User {
        requirePassword(password)
        if (role !in ROLES) throw badRequest("role: admin | editor | viewer")
        if (!email.contains('@')) throw badRequest("email: not an email address")
        val id = db.tx {
            if (UserRepository.emailTaken(normalizeEmail(email))) throw conflict("email: already in use")
            UserRepository.insert(normalizeEmail(email), name, role, Passwords.hash(password))
        }
        return user(id)
    }

    suspend fun updateUser(id: Long, name: String?, role: String?): User {
        if (role != null && role !in ROLES) throw badRequest("role: admin | editor | viewer")
        db.tx {
            val current = UserRepository.find(id) ?: throw notFound("Unknown user $id")
            if (current.role == ADMIN && role != null && role != ADMIN) requireAnotherAdmin()
            UserRepository.update(id, name ?: current.name, role ?: current.role)
        }
        return user(id)
    }

    suspend fun deleteUser(id: Long) = db.tx {
        val current = UserRepository.find(id) ?: throw notFound("Unknown user $id")
        if (current.role == ADMIN) requireAnotherAdmin()
        UserRepository.delete(id)
    }

    private fun requireAnotherAdmin() {
        if (UserRepository.count(ADMIN) <= 1) throw conflict("The last admin cannot be demoted or deleted")
    }
}
