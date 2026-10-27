package com.anjo.statisticservice.repository

import com.anjo.statisticservice.db.Db
import com.anjo.statisticservice.db.query
import com.anjo.statisticservice.db.update
import java.sql.Connection

object SourceCredentialRepository {

    fun upsert(c: Connection, sourceId: String, tokenHash: ByteArray) {
        c.update(
            """INSERT INTO source_credentials (org_id, source_id, token_hash) VALUES (?, ?, ?)
               ON CONFLICT (org_id, source_id) DO UPDATE SET token_hash = EXCLUDED.token_hash, created_at = now()""",
            Db.ORG, sourceId, tokenHash,
        )
    }

    fun tokenHash(c: Connection, sourceId: String): ByteArray? =
        c.query("SELECT token_hash FROM source_credentials WHERE org_id = ? AND source_id = ?", Db.ORG, sourceId) { it.getBytes(1) }.firstOrNull()

    fun delete(c: Connection, sourceId: String) {
        c.update("DELETE FROM source_credentials WHERE org_id = ? AND source_id = ?", Db.ORG, sourceId)
    }
}
