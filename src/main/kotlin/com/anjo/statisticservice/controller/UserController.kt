package com.anjo.statisticservice.controller

import com.anjo.statisticservice.model.ADMIN
import com.anjo.statisticservice.model.EDITOR
import com.anjo.statisticservice.model.dto.NewUser
import com.anjo.statisticservice.model.dto.UserUpdate
import com.anjo.statisticservice.service.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.util.getOrFail

fun Route.userRoutes(users: UserService) {
    get("/users") {
        call.requireRole(ADMIN, EDITOR)
        call.respond(users.users())
    }.describe { summary = "List users (admin; editor for the share picker)" }
    post("/users") {
        call.requireRole(ADMIN)
        val user = call.receive<NewUser>()
        call.respond(HttpStatusCode.Created, users.createUser(user.email, user.name, user.role, user.password))
    }.describe { summary = "Create a user with an initial password" }
    put("/users/{id}") {
        call.requireRole(ADMIN)
        val update = call.receive<UserUpdate>()
        call.respond(users.updateUser(call.parameters.getOrFail<Long>("id"), update.name, update.role))
    }.describe { summary = "Change a user's name or role; the last admin stays admin" }
    delete("/users/{id}") {
        call.requireRole(ADMIN)
        users.deleteUser(call.parameters.getOrFail<Long>("id"))
        call.respond(HttpStatusCode.NoContent)
    }.describe { summary = "Delete a user; the last admin cannot be deleted" }
}
