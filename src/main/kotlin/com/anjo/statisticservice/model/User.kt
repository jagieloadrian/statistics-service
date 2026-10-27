package com.anjo.statisticservice.model

import kotlinx.serialization.Serializable

const val ADMIN = "admin"
const val EDITOR = "editor"
const val VIEWER = "viewer"
val ROLES = setOf(ADMIN, EDITOR, VIEWER)

@Serializable
data class Prefs(
    val timeZone: String? = null,
    val units: String = "metric",
    val theme: String = "system",
    val homeDashboard: String? = null,
    val favourites: List<String> = emptyList(),
)

@Serializable
data class User(val id: Long, val email: String, val name: String, val role: String, val prefs: Prefs = Prefs())

data class UserPrincipal(val id: Long, val role: String, val token: String)
