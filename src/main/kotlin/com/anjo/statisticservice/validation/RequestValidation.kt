package com.anjo.statisticservice.validation

import com.anjo.statisticservice.model.BUCKETS
import com.anjo.statisticservice.model.Dashboard
import com.anjo.statisticservice.model.Dataset
import com.anjo.statisticservice.model.MAX_RAW_ROWS
import com.anjo.statisticservice.model.Prefs
import com.anjo.statisticservice.model.Query
import com.anjo.statisticservice.model.ROLES
import com.anjo.statisticservice.model.Source
import com.anjo.statisticservice.model.dto.LoginRequest
import com.anjo.statisticservice.model.dto.NewUser
import com.anjo.statisticservice.model.dto.OidcLoginRequest
import com.anjo.statisticservice.model.dto.OidcStartRequest
import com.anjo.statisticservice.model.dto.PasswordChange
import com.anjo.statisticservice.model.dto.Shares
import com.anjo.statisticservice.model.dto.UserUpdate
import com.anjo.statisticservice.service.timeRange
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlinx.datetime.TimeZone
import java.net.URI
import kotlin.time.Clock

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+$")
private val UNITS = setOf("metric", "imperial")
private val THEMES = setOf("system", "light", "dark")

private val ACCENTS =
    setOf(
        "red",
        "deep-orange",
        "orange",
        "amber",
        "yellow",
        "lime",
        "light-green",
        "green",
        "teal",
        "cyan",
        "light-blue",
        "blue",
        "indigo",
        "deep-purple",
        "purple",
        "pink",
        "brown",
        "blue-grey",
    )
private const val MAX_EMAIL_LENGTH = 254
private const val MAX_NAME_LENGTH = 200
private const val MIN_PASSWORD_LENGTH = 10
private const val MAX_PASSWORD_LENGTH = 1024
private const val MAX_URI_LENGTH = 2048
private const val MAX_CODE_LENGTH = 2048
private const val MAX_CODE_VERIFIER_LENGTH = 128
private const val MAX_ID_LENGTH = 63
private const val MAX_FAVOURITES = 200

fun Application.configureRequestValidation() {
    install(RequestValidation) {
        validate<LoginRequest> { it.violations().toValidationResult() }
        validate<OidcStartRequest> { violations { redirectUri(it.redirectUri) }.toValidationResult() }
        validate<OidcLoginRequest> { it.violations().toValidationResult() }
        validate<PasswordChange> { it.violations().toValidationResult() }
        validate<NewUser> { it.violations().toValidationResult() }
        validate<UserUpdate> { it.violations().toValidationResult() }
        validate<Prefs> { it.violations().toValidationResult() }
        validate<Shares> { it.violations().toValidationResult() }
        validate<Query> { it.violations().toValidationResult() }
        validate<Dataset> { datasetViolations(it).toValidationResult() }
        validate<Source> { sourceViolations(it).toValidationResult() }
        validate<Dashboard> { dashboardViolations(it).toValidationResult() }
    }
}

private fun LoginRequest.violations() =
    violations {
        ensure(email.isNotBlank() && email.length <= MAX_EMAIL_LENGTH) { "email: required, at most $MAX_EMAIL_LENGTH characters" }
        ensure(
            password.isNotEmpty() && password.length <= MAX_PASSWORD_LENGTH,
        ) { "password: required, at most $MAX_PASSWORD_LENGTH characters" }
    }

private fun OidcLoginRequest.violations() =
    violations {
        ensure(code.isNotBlank() && code.length <= MAX_CODE_LENGTH) { "code: required, at most $MAX_CODE_LENGTH characters" }
        ensure(codeVerifier.isNotBlank() && codeVerifier.length <= MAX_CODE_VERIFIER_LENGTH) {
            "codeVerifier: required, at most $MAX_CODE_VERIFIER_LENGTH characters"
        }
        redirectUri(redirectUri)
    }

private fun PasswordChange.violations() =
    violations {
        ensure(current.isNotEmpty()) { "current: required" }
        newPassword(new)
    }

private fun NewUser.violations() =
    violations {
        ensure(EMAIL.matches(email) && email.length <= MAX_EMAIL_LENGTH) { "email: not an email address" }
        ensure(name.length <= MAX_NAME_LENGTH) { "name: at most $MAX_NAME_LENGTH characters" }
        role(role)
        newPassword(password)
    }

private fun UserUpdate.violations() =
    violations {
        ensure(name == null || name.length <= MAX_NAME_LENGTH) { "name: at most $MAX_NAME_LENGTH characters" }
        role?.let { role(it) }
    }

private fun Prefs.violations() =
    violations {
        ensure(units in UNITS) { "units: ${UNITS.joinToString(" | ")}" }
        ensure(theme in THEMES) { "theme: ${THEMES.joinToString(" | ")}" }
        ensure(accent in ACCENTS) { "accent: ${ACCENTS.joinToString(" | ")}" }
        ensure(timeZone == null || runCatching { TimeZone.of(timeZone) }.isSuccess) { "timeZone: unknown zone '$timeZone'" }
        ensure(homeDashboard == null || homeDashboard.length <= MAX_ID_LENGTH) { "homeDashboard: at most $MAX_ID_LENGTH characters" }
        ensure(favourites.size <= MAX_FAVOURITES) { "favourites: at most $MAX_FAVOURITES" }
        ensure(favourites.all { it.length <= MAX_ID_LENGTH }) { "favourites: ids of at most $MAX_ID_LENGTH characters" }
    }

private fun Shares.violations() =
    violations {
        ensure(userIds.all { it > 0 }) { "userIds: must be positive" }
    }

private fun Query.violations() =
    violations {
        ensure(dataset.isNotBlank()) { "dataset: required" }
        ensure(measures.isNotEmpty()) { "measures: at least one is required" }
        ensure(bucket in BUCKETS) { "bucket: ${BUCKETS.joinToString(" | ")}" }
        ensure(maxPoints in 1..MAX_RAW_ROWS) { "maxPoints: 1..$MAX_RAW_ROWS" }
        val range = runCatching { timeRange(Clock.System.now()) }.getOrNull()
        if (range == null) {
            add("from/to must be ISO-8601 instants, range.last an ISO-8601 duration")
        } else {
            ensure(range.to > range.from) { "'to' must be after 'from'" }
        }
    }

private fun MutableList<String>.newPassword(password: String) =
    ensure(password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
        "password: at least $MIN_PASSWORD_LENGTH characters, at most $MAX_PASSWORD_LENGTH"
    }

private fun MutableList<String>.role(role: String) = ensure(role in ROLES) { "role: ${ROLES.joinToString(" | ")}" }

private fun MutableList<String>.redirectUri(uri: String) =
    ensure(uri.length <= MAX_URI_LENGTH && uri.isAbsoluteUri()) { "redirectUri: absolute URI of at most $MAX_URI_LENGTH characters" }

private fun String.isAbsoluteUri() = runCatching { URI(this) }.getOrNull()?.isAbsolute == true

private fun List<String>.toValidationResult(): ValidationResult = if (isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(this)
