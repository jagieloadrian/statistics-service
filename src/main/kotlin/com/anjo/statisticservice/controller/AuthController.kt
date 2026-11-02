package com.anjo.statisticservice.controller

import com.anjo.statisticservice.model.Prefs
import com.anjo.statisticservice.model.dto.LoginRequest
import com.anjo.statisticservice.model.dto.OidcLoginRequest
import com.anjo.statisticservice.model.dto.OidcStartRequest
import com.anjo.statisticservice.model.dto.PasswordChange
import com.anjo.statisticservice.service.AuthService
import com.anjo.statisticservice.service.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put

val LOGIN = RateLimitName("login")

fun Route.signInRoutes(auth: AuthService) {
    get("/auth/config") { call.respond(auth.config) }
        .describe { summary = "Enabled sign-in methods: local passwords and/or the sign-in provider's name" }
    rateLimit(LOGIN) {
        post("/auth/login") {
            val req = call.receive<LoginRequest>()
            call.respond(auth.login(req.email, req.password))
        }.describe { summary = "Sign in; returns a bearer token (5 attempts/min per client)" }
        post("/auth/oidc/start") {
            call.respond(auth.oidcStart(call.receive<OidcStartRequest>().redirectUri))
        }.describe { summary = "Start provider sign-in; returns the provider URL plus state and PKCE verifier for the client to keep" }
        post("/auth/oidc") {
            val req = call.receive<OidcLoginRequest>()
            call.respond(auth.oidcLogin(req.code, req.codeVerifier, req.redirectUri))
        }.describe { summary = "Finish provider sign-in with the authorization code; returns a bearer token like /auth/login" }
    }
}

fun Route.accountRoutes(
    auth: AuthService,
    users: UserService,
) {
    post("/auth/logout") {
        auth.logout(call.user().token)
        call.respond(HttpStatusCode.NoContent)
    }.describe { summary = "Sign out; deletes the current token" }
    get("/me") { call.respond(users.user(call.user().id)) }
        .describe { summary = "Current user with preferences" }
    put("/me/prefs") { call.respond(users.setPrefs(call.user().id, call.receive<Prefs>())) }
        .describe { summary = "Save own preferences (time zone, units, theme, accent colour, home dashboard, favourites)" }
    put("/me/password") {
        val req = call.receive<PasswordChange>()
        auth.changePassword(call.user(), req.current, req.new)
        call.respond(HttpStatusCode.NoContent)
    }.describe { summary = "Change own password; other sessions are signed out" }
}
