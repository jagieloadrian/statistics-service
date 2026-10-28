package com.anjo.statisticservice.validation

import com.anjo.statisticservice.exception.badRequest

fun violations(rules: MutableList<String>.() -> Unit): List<String> = buildList(rules)

fun MutableList<String>.ensure(
    ok: Boolean,
    reason: () -> String,
) {
    if (!ok) add(reason())
}

fun throwIfAny(
    what: String,
    reasons: List<String>,
) {
    if (reasons.isNotEmpty()) throw badRequest("Invalid $what", reasons)
}
