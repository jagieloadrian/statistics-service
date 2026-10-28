package com.anjo.statisticservice.service

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.exception.conflict
import com.anjo.statisticservice.exception.notFound
import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.Prefs
import com.anjo.statisticservice.model.User
import com.anjo.statisticservice.repository.UserRepository

class UserService(
    private val db: Db,
) {
    suspend fun user(id: Long): User = db.tx { UserRepository.find(id) } ?: throw unknownUser(id)

    suspend fun users(): List<User> = db.tx { UserRepository.list() }

    suspend fun setPrefs(
        id: Long,
        prefs: Prefs,
    ): User {
        db.tx { UserRepository.setPrefs(id, prefs) }
        return user(id)
    }

    suspend fun createUser(
        email: String,
        name: String,
        role: String,
        password: String,
    ): User {
        val normalized = normalizeEmail(email)
        val hash = Passwords.hash(password)
        val id =
            db.tx {
                if (UserRepository.emailTaken(normalized)) throw conflict("email: already in use")
                UserRepository.insert(normalized, name, role, hash)
            }
        return user(id)
    }

    suspend fun updateUser(
        id: Long,
        name: String?,
        role: String?,
    ): User {
        db.tx {
            val current = UserRepository.find(id) ?: throw unknownUser(id)
            if (current.role == ADMIN && role != null && role != ADMIN) requireAnotherAdmin()
            UserRepository.update(id, name ?: current.name, role ?: current.role)
        }
        return user(id)
    }

    suspend fun deleteUser(id: Long) =
        db.tx {
            val current = UserRepository.find(id) ?: throw unknownUser(id)
            if (current.role == ADMIN) requireAnotherAdmin()
            UserRepository.delete(id)
        }

    private fun requireAnotherAdmin() {
        if (UserRepository.count(ADMIN) <= 1) throw conflict("The last admin cannot be demoted or deleted")
    }

    private fun unknownUser(id: Long) = notFound("Unknown user $id")
}
