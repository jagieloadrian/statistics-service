package com.anjo.statisticservice.controller

import com.anjo.statisticservice.exception.badRequest
import com.anjo.statisticservice.exception.forbidden
import com.anjo.statisticservice.exception.unauthorized
import com.anjo.statisticservice.model.UserPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingCall
import io.ktor.server.util.getOrFail

fun RoutingCall.user(): UserPrincipal = principal<UserPrincipal>() ?: throw unauthorized("Sign in first")

fun RoutingCall.requireRole(vararg roles: String): UserPrincipal = user().also { if (it.role !in roles) throw forbidden() }

val RoutingCall.id: String get() = parameters.getOrFail("id")

val RoutingCall.numericId: Long get() = parameters.getOrFail<Long>("id")

val RoutingCall.force: Boolean get() = request.queryParameters["force"] == "true"

fun requireSameId(
    pathId: String,
    bodyId: String,
) {
    if (pathId != bodyId) throw badRequest("id in path '$pathId' differs from body '$bodyId'")
}
