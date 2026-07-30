# Contract: Generic collect/expose routing

External HTTP contract — URLs and response shapes MUST stay byte-identical to
today (FR-010). This documents the *generic dispatch layer* replacing the two
hardcoded route files; per-payload/response shapes are unchanged and already
documented in `.docs/Epidemic_UI_API_Contract.md`,
`.docs/RPI_Epidemic_Contract.md`, `.docs/Temperature_UI_API_Contract.md`,
`.docs/RPI_Temperature_Contract.md` — not duplicated here.

## Collect

```text
POST /api/v1/stats/collect/{pluginId}
```

- `pluginId` = `epidemic` | `temperature` (today) — matches `PluginRegistry` ids.
- Body: raw JSON, decoded by the resolved plugin to its own DTO type.
- 200 OK: identical to today's `/collect/epidemic` / `/collect/temperature`.
- 400 Bad Request + reasons list: same validation failure shape as today.
- 404: unregistered `pluginId` (Phase 1 `EmptyDataException`, unchanged).

## Expose

```text
GET /api/v1/stats/expose/{pluginId}/**
```

- Each plugin's `expose()` registers its own sub-routes under
  `/api/v1/stats/expose/{plugin.id}/...` — for `epidemic` and `temperature`,
  these sub-routes are byte-identical to today's:
  - `epidemic`: `/runs`, `/device/{deviceId}/run/{runId}`, `/device/{deviceId}/run/{runId}/summary`
  - `temperature`: `/devices`, `/devices/{deviceId}`, `/devices/{deviceId}/summary`
- 404 for an unregistered `pluginId` prefix — a catch-all route mounted after
  the known plugins' routes resolves the id via `PluginRegistry`, throwing the
  same `EmptyDataException` collect uses for unknown ids (FR-006/SC-004: same
  error shape as collect, not a bare unhandled 404).

## Kotlin interface shapes touched

```kotlin
package com.anjo.statisticservice.plugin

class PluginRouteSet(val configure: Route.() -> Unit)

class EpidemicPlugin(
    private val collector: StatsCollectorService,
    private val exposer: EpidemicStatsExposerService,
) : StatPlugin {
    override val id = "epidemic"
    override suspend fun collect(raw: JsonElement) { /* decode, validate, delegate */ }
    override suspend fun expose(): PluginRouteSet = PluginRouteSet { /* get("/runs") {...}, ... */ }
}

class TemperaturePlugin(
    private val collector: StatsCollectorService,
    private val exposer: TemperatureStatsExposerService,
) : StatPlugin {
    override val id = "temperature"
    override suspend fun collect(raw: JsonElement) { /* decode, validate, delegate */ }
    override suspend fun expose(): PluginRouteSet = PluginRouteSet { /* get("/devices") {...}, ... */ }
}
```

```kotlin
package com.anjo.statisticservice.exception

class PluginValidationException(val reasons: List<String>) : Exception()
```
