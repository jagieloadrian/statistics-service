package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.SourceCredentials
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

object SourceCredentialRepository {
    fun upsert(sourceId: String, tokenHash: ByteArray) {
        SourceCredentials.upsert {
            it[orgId] = Db.ORG
            it[this.sourceId] = sourceId
            it[this.tokenHash] = tokenHash
            it[createdAt] = CurrentTimestamp
        }
    }

    fun tokenHash(sourceId: String): ByteArray? =
        SourceCredentials.select(SourceCredentials.tokenHash).where { of(sourceId) }.singleOrNull()?.get(SourceCredentials.tokenHash)

    fun delete(sourceId: String) {
        SourceCredentials.deleteWhere { of(sourceId) }
    }

    private fun of(sourceId: String) = (SourceCredentials.orgId eq Db.ORG) and (SourceCredentials.sourceId eq sourceId)
}
