package com.anjo.statisticservice.db

import com.anjo.statisticservice.model.Prefs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

private val prefsJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val orgId = short("org_id")
    val email = text("email")
    val name = text("name")
    val passwordHash = text("password_hash").nullable()
    val oidcIssuer = text("oidc_issuer").nullable()
    val oidcSubject = text("oidc_subject").nullable()
    val role = text("role")
    val prefs = jsonb("prefs", prefsJson, Prefs.serializer())
    override val primaryKey = PrimaryKey(id)
}

object AuthTokens : Table("auth_tokens") {
    val tokenHash = binary("token_hash")
    val userId = long("user_id").references(Users.id)
    val lastSeen = timestamp("last_seen")
    override val primaryKey = PrimaryKey(tokenHash)
}

object Definitions : Table("definitions") {
    val orgId = short("org_id")
    val kind = text("kind")
    val id = text("id")
    val body = jsonb<String>("body", { it }, { it })
    val ownerId = long("owner_id").nullable()
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(orgId, kind, id)
}

object DashboardShares : Table("dashboard_shares") {
    val orgId = short("org_id")
    val dashboardId = text("dashboard_id")
    val userId = long("user_id")
    override val primaryKey = PrimaryKey(orgId, dashboardId, userId)
}

object SourceCredentials : Table("source_credentials") {
    val orgId = short("org_id")
    val sourceId = text("source_id")
    val tokenHash = binary("token_hash")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(orgId, sourceId)
}

object Records : Table("records") {
    val orgId = short("org_id")
    val datasetId = text("dataset_id")
    val ts = timestamp("ts")
    val dims = jsonb<Map<String, String>>("dims", Json)
    val vals = jsonb<Map<String, JsonPrimitive>>("vals", Json)
    val ident = text("ident").nullable()
    val tsEstimated = bool("ts_estimated")
}
