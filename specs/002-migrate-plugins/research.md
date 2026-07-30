# Phase 0 Research: Migrate Epidemic & Temperature onto the Plugin Contract

No `NEEDS CLARIFICATION` markers in Technical Context. Decisions below resolve
how the spec's requirements map onto the actual existing codebase.

## Decision: plugins delegate to existing services, don't reimplement them

**Decision**: `EpidemicPlugin`/`TemperaturePlugin` hold references to the
existing, unchanged `StatsCollectorService` and `EpidemicStatsExposerService`/
`TemperatureStatsExposerService`, and simply call their existing methods.

**Rationale**: FR-001/FR-002 require wrapping today's behavior "without
changing its inputs, outputs, or validation rules" — the only reliable way to
guarantee that (and satisfy SC-001) is to not touch the business logic at all.
Ladder rung 2 (ponytail): reuse what's already there.

**Alternatives considered**: rewriting collect/expose logic directly inside the
plugin classes — rejected, pure risk (re-deriving already-correct, already-tested
logic) for zero benefit; the existing service classes stay as the tested,
trusted implementation until FR-008 deletes only the *dead* hardcoded
dispatch methods, not the logic itself (which lives on inside the plugin via
delegation, or — for `StatsCollectorService` — is called as-is since its
`saveEpidemicStats`/`saveTemperatureStats` methods are the real logic, not
dead code; "dead" in FR-008 refers to `StatsExposerFacade`'s domain-specific
methods, which are dispatch-only wrappers).

## Decision: validation moves by relocating the *call site*, not the logic

**Decision**: `EpidemicPlugin.collect(raw)` decodes `raw` to `EpidemicDto`,
calls the existing `isEpidemicValid()` function directly, and throws a new
`PluginValidationException(reasons)` if invalid — before calling
`StatsCollectorService.saveEpidemicStats()`. Same pattern for temperature with
`isTemperatureDtoValid()`. The global `install(RequestValidation)` block
(`validationStatsRequestBody()`) is removed since there's no longer a single
typed `call.receive<EpidemicDto>()` at the generic route — the route receives
raw JSON and the plugin decides how to decode/validate it.

**Rationale**: FR-005 requires identical validation results — reusing the
exact existing validation functions is the only zero-risk way to guarantee
"same fields checked, same error reasons, same status code." The trigger
mechanism changes (Ktor's `RequestValidation` plugin → explicit in-plugin
check) because Ktor's typed `RequestValidation` is keyed by static DTO type at
the route/content-negotiation layer, which doesn't fit one generic route
serving multiple DTO shapes by `pluginId`.

**Alternatives considered**: keep `install(RequestValidation)` and have it
inspect `pluginId` to pick a validator — rejected, more complex than moving
the one-line validation call into `collect()`, and doesn't match the roadmap's
explicit instruction ("Validation moves into each plugin's collect()").

## Decision: new `PluginValidationException` mapped identically to today's 400+reasons shape

**Decision**: add `PluginValidationException(val reasons: List<String>) : Exception()`
to `exception/Exceptions.kt`, and add a `StatusPages` handler in
`GlobalExceptionHandler.kt`: `call.respond(HttpStatusCode.BadRequest, cause.reasons)`
— identical to today's `RequestValidationException` handler.

**Rationale**: today's handler already does exactly `respond(BadRequest, cause.reasons)`
for `RequestValidationException`; the new exception just carries the same shape
so FR-005's "same status code, same reasons list" holds without inventing a new
response format.

**Alternatives considered**: reuse `RequestValidationException` itself by
constructing it manually in the plugin — rejected, that type is
Ktor-plugin-internal machinery (constructed by the `RequestValidation`
feature), not meant for direct application throwing; a small dedicated
exception is simpler and clearer.

## Decision: `PluginRouteSet` becomes a `Route.() -> Unit` holder, routes mounted at startup

**Decision**: `PluginRouteSet(val configure: Route.() -> Unit)`. At startup,
for every plugin in the `PluginRegistry`, mount
`route("${API_BASE_PATH}/stats/expose/${plugin.id}") { plugin.expose().configure(this) }`.
Since `expose()` is `suspend` (fixed by the already-merged Phase 1 contract)
but startup route registration is synchronous, it's called once via
`runBlocking` during application module setup — acceptable since it does no
real async work (just returns a route-building lambda), not a per-request cost.

**Rationale**: per Clarifications, each plugin needs to define its own
divergent sub-route shape (epidemic's nested `/device/{id}/run/{id}` vs
temperature's `/devices/{id}`) with zero URL change from today. A
`Route.() -> Unit` lambda is the natural Ktor-idiomatic way to let a plugin
own arbitrary routing, and reusing the existing per-domain route path
segments (`epidemic`, `temperature` = the plugin ids) inside that lambda
means the resulting URLs are byte-identical to today's, satisfying FR-010.

**Alternatives considered**: resolve the plugin per-request inside one
`route("{pluginId}/{...}")` catch-all and dispatch on sub-path manually —
rejected, reinvents routing dispatch that Ktor's `Route` DSL already does; more
code, more risk of subtly different path-matching behavior than today
(e.g. Ktor's own precedence rules for `/devices` vs `/devices/{id}` vs
`/devices/{id}/summary`).

**Unknown-`pluginId` fallback (fixes FR-006/SC-004 gap)**: per-plugin routes
are literal path segments (`expose/epidemic`, `expose/temperature`), which
Ktor always matches ahead of a parameterized route. So one additional
catch-all is mounted *after* the per-plugin routes:
`route("${API_BASE_PATH}/stats/expose/{pluginId}") { get("{...}") { registry.resolve(call.parameters["pluginId"]!!) } }`.
It only gets hit when no known plugin's literal prefix matched, and
`registry.resolve()` throws the same `EmptyDataException` collect already
uses — reusing the existing Phase 1 registry + `StatusPages` mapping, zero new
exception type. This is a required part of the design, not optional — without
it, FR-006/SC-004 don't hold for expose.

## Decision: collect route dispatch — plugin id resolved per request

**Decision**: `POST /api/v1/stats/collect/{pluginId}` reads `pluginId` from
the path per request, resolves the plugin via `PluginRegistry.resolve()`
(already 404-mapping unknown ids, from Phase 1), reads the raw body as
`JsonElement`, and calls `plugin.collect(raw)`.

**Rationale**: collect is push-based and per-request by nature (unlike expose,
which is about mounting a set of GET routes once) — resolving per request is
the direct, minimal implementation; matches FR-003/FR-006 exactly as specified.

**Alternatives considered**: none meaningfully different — this is the
straightforward reading of FR-003.
