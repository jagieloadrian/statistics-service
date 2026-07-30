# Phase 1 Data Model: Migrate Epidemic & Temperature onto the Plugin Contract

No persisted schema changes — Redis keys/data shapes are unchanged (FR-010).
This documents the new/changed in-process types only; existing DTOs
(`EpidemicDto`, `TemperatureDto`, response DTOs) are unchanged.

## EpidemicPlugin (implements StatPlugin)

| Member | Behavior |
|---|---|
| `id` | `"epidemic"` — matches today's URL segment and Redis key prefix |
| `collect(raw: JsonElement)` | decode to `EpidemicDto` → `isEpidemicValid()` (existing function) → throw `PluginValidationException(reasons)` if invalid → `StatsCollectorService.saveEpidemicStats(dto)` (existing method, unchanged) |
| `expose(): PluginRouteSet` | returns a route block registering the existing 3 GET operations (`/runs`, `/device/{deviceId}/run/{runId}`, `/device/{deviceId}/run/{runId}/summary`) delegating to `EpidemicStatsExposerService` (unchanged) |

## TemperaturePlugin (implements StatPlugin)

| Member | Behavior |
|---|---|
| `id` | `"temperature"` — matches today's URL segment and Redis key prefix |
| `collect(raw: JsonElement)` | decode to `TemperatureDto` → `isTemperatureDtoValid()` (existing function) → throw `PluginValidationException(reasons)` if invalid → `StatsCollectorService.saveTemperatureStats(dto)` (existing method, unchanged) |
| `expose(): PluginRouteSet` | returns a route block registering the existing 3 GET operations (`/devices`, `/devices/{deviceId}`, `/devices/{deviceId}/summary`) delegating to `TemperatureStatsExposerService` (unchanged) |

## PluginRouteSet (modified from Phase 1 placeholder)

| Member | Type | Notes |
|---|---|---|
| `configure` | `Route.() -> Unit` | a Ktor route-building lambda the plugin owns; mounted under `route("${API_BASE_PATH}/stats/expose/${plugin.id}")` once at startup |

**Validation rule**: none beyond what Kotlin's type system enforces — this is
a structural holder, not a data entity with invariants.

## PluginValidationException (new)

| Field | Type | Notes |
|---|---|---|
| `reasons` | `List<String>` | same shape as today's `RequestValidationException.reasons` — mapped to `HttpStatusCode.BadRequest` with `reasons` as the response body, identical to today's behavior (FR-005) |

## Generic collect/expose routes

Not entities — pure routing:
- `POST /api/v1/stats/collect/{pluginId}`: resolves plugin via `PluginRegistry` (Phase 1, unchanged), reads raw JSON body, calls `plugin.collect(raw)`.
- `GET /api/v1/stats/expose/{pluginId}/**`: for each registered plugin, `plugin.expose().configure` is mounted under its own `{pluginId}` prefix at startup — the `**` wildcard is fulfilled by whatever sub-routes each plugin's `configure` block registers. A catch-all fallback mounted after the known plugins' routes resolves any unmatched `{pluginId}` via `PluginRegistry`, throwing the same `EmptyDataException` collect uses (satisfies FR-006/SC-004 for expose too).
