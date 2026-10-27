package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.Users
import com.anjo.statisticservice.model.Prefs
import com.anjo.statisticservice.model.User
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

object UserRepository {

    fun count(role: String? = null): Long =
        Users.selectAll().where { Users.orgId eq Db.ORG }.apply { if (role != null) andWhere { Users.role eq role } }.count()

    fun find(id: Long): User? = Users.selectAll().where { (Users.orgId eq Db.ORG) and (Users.id eq id) }.singleOrNull()?.let(::toUser)

    fun list(): List<User> = Users.selectAll().where { Users.orgId eq Db.ORG }.orderBy(Users.id).map(::toUser)

    /** Id and password hash of the user with this (normalized) email. */
    fun credentials(email: String): Pair<Long, String>? =
        Users.select(Users.id, Users.passwordHash).where { (Users.orgId eq Db.ORG) and (Users.email eq email) }.singleOrNull()
            ?.let { it[Users.id] to it[Users.passwordHash] }

    fun passwordHash(id: Long): String = Users.select(Users.passwordHash).where { Users.id eq id }.single()[Users.passwordHash]

    fun emailTaken(email: String): Boolean = !Users.selectAll().where { Users.email eq email }.empty()

    fun insert(email: String, name: String, role: String, passwordHash: String): Long =
        Users.insert {
            it[orgId] = Db.ORG
            it[this.email] = email
            it[this.name] = name
            it[this.passwordHash] = passwordHash
            it[this.role] = role
        }[Users.id]

    fun update(id: Long, name: String, role: String) {
        Users.update({ Users.id eq id }) {
            it[this.name] = name
            it[this.role] = role
        }
    }

    fun setPrefs(id: Long, prefs: Prefs) {
        Users.update({ Users.id eq id }) { it[this.prefs] = prefs }
    }

    fun setPasswordHash(id: Long, hash: String) {
        Users.update({ Users.id eq id }) { it[passwordHash] = hash }
    }

    fun delete(id: Long) {
        Users.deleteWhere { Users.id eq id }
    }

    private fun toUser(r: ResultRow) = User(r[Users.id], r[Users.email], r[Users.name], r[Users.role], r[Users.prefs])
}
