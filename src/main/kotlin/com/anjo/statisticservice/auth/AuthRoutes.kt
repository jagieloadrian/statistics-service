package com.anjo.statisticservice.auth

import com.anjo.statisticservice.routing.ApiException
import com.anjo.statisticservice.routing.badRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable

val LOGIN = RateLimitName("login")

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val user: User)

@Serializable
data class PasswordChange(val current: String, val new: String)

@Serializable
data class NewUser(val email: String, val name: String = "", val role: String, val password: String)

@Serializable
data class UserUpdate(val name: String? = null, val role: String? = null)

/** Public: login only. Mount inside the API base route. */
fun Route.loginRoute(auth: AuthStore) = rateLimit(LOGIN) {
    post("/auth/login") {
        val req = call.receive<LoginRequest>()
        val (token, user) = auth.login(req.email, req.password)
            ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Wrong email or password")
        call.respond(LoginResponse(token, user))
    }.describe { summary = "Sign in; returns a bearer token (5 attempts/min per client)" }
}

/** Signed-in routes: own account for everybody, user management for admins. Mount inside `authenticate("user")`. */
fun Route.accountRoutes(auth: AuthStore) {
    post("/auth/logout") {
        auth.logout(call.user().token)
        call.respond(HttpStatusCode.NoContent)
    }.describe { summary = "Sign out; deletes the current token" }
    get("/me") { call.respond(auth.user(call.user().id)) }
        .describe { summary = "Current user with preferences" }
    put("/me/prefs") { call.respond(auth.setPrefs(call.user().id, call.receive<Prefs>())) }
        .describe { summary = "Save own preferences (time zone, units, theme, home dashboard, favourites)" }
    put("/me/password") {
        val req = call.receive<PasswordChange>()
        auth.changePassword(call.user(), req.current, req.new)
        call.respond(HttpStatusCode.NoContent)
    }.describe { summary = "Change own password; other sessions are signed out" }

    get("/users") {
        call.requireRole(ADMIN, EDITOR) // editors pick users when sharing a dashboard
        call.respond(auth.users())
    }.describe { summary = "List users (admin; editor for the share picker)" }
    post("/users") {
        call.requireRole(ADMIN)
        val u = call.receive<NewUser>()
        call.respond(HttpStatusCode.Created, auth.createUser(u.email, u.name, u.role, u.password))
    }.describe { summary = "Create a user with an initial password" }
    put("/users/{id}") {
        call.requireRole(ADMIN)
        val u = call.receive<UserUpdate>()
        call.respond(auth.updateUser(call.userId(), u.name, u.role))
    }.describe { summary = "Change a user's name or role; the last admin stays admin" }
    delete("/users/{id}") {
        call.requireRole(ADMIN)
        auth.deleteUser(call.userId())
        call.respond(HttpStatusCode.NoContent)
    }.describe { summary = "Delete a user; the last admin cannot be deleted" }
}

private fun RoutingCall.userId() = parameters["id"]!!.toLongOrNull() ?: throw badRequest("id: must be a number")
