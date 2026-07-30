# Phase 1 Data Model: Extract Plugins into Separate `/plugin` Packages

No persisted schema changes — Redis keys/data shapes are unchanged (FR-006).
No DTO shapes change either; this documents which module each type now lives
in and the two genuinely new types this phase introduces (the split
collector services + the stub plugin).

## `plugin-api` module (shared contract — no core dependency)

| Type | Kind | Notes |
|---|---|---|
| `StatPlugin` | interface | unchanged — `id`, `collect(raw)`, `expose()` |
| `PluginRouteSet` | class | unchanged — `Route.() -> Unit` holder |
| `StatsRepository` | interface | unchanged signature, moved from core; core's `StatsRepositoryRedisImpl` implements it |
| `ApplicationConstants` | object | trimmed to `API_BASE_PATH`, `getKey(pluginId, deviceId, runId?)` |
| `DbKeyConstants` (common subset) | object | trimmed to `DEVICE_ID_KEY`, `RUN_ID_KEY`, `TIMESTAMP_KEY` |

## `rpi_epidemic_api` module

| Type | Kind | Notes |
|---|---|---|
| `EpidemicPlugin` | class, implements `StatPlugin` | unchanged behavior; now constructs against its own module's collector/exposer types instead of core's |
| `EpidemicStatsCollectorService` | class (**new**, carved out) | `saveEpidemicStats(dto)` — identical body to today's `StatsCollectorService.saveEpidemicStats` + its private helpers, now epidemic-only |
| `EpidemicStatsExposerService` | class | unchanged, moved |
| `EpidemicDto`, `EpidemicResponseDto` family | data classes | unchanged, moved |
| `isEpidemicValid` | function | unchanged, moved |
| `EpidemicKeyConstants` (**new**, carved out) | object | `EPIDEMIC_KEYS`, `getEpidemicKey(deviceId, runId)` — identical to today's `ApplicationConstants` members |
| `EpidemicDbKeyConstants` (**new**, carved out) | object | the epidemic-only fields from today's `DbKeyConstants` (`STARTED_AT_KEY`, `POPULATION_KEY`, `GENERATION_KEY`, `INFECTED_KEY`, `SUSCEPTIBLE_KEY`, `RECOVERED_KEY`, `DEAD_KEY`, `EXPOSED_KEY`, `LOCKDOWN_KEY`, `MOBILITY_MULTIPLICATION_KEY`, `BY_TYPE_KEY`, `ENDED_AT_KEY`) |

## `rpi_temperature_api` module

| Type | Kind | Notes |
|---|---|---|
| `TemperaturePlugin` | class, implements `StatPlugin` | unchanged behavior, same move pattern as epidemic |
| `TemperatureStatsCollectorService` | class (**new**, carved out) | `saveTemperatureStats(dto)` — identical body to today's `StatsCollectorService.saveTemperatureStats` + its private helper |
| `TemperatureStatsExposerService` | class | unchanged, moved |
| `Resolution` | enum/class | unchanged, moved |
| `TemperatureDto`, `TemperatureResponseDto` family | data classes | unchanged, moved |
| `isTemperatureDtoValid` | function | unchanged, moved |
| `TemperatureKeyConstants` (**new**, carved out) | object | `TEMPERATURE_KEYS`, `getTemperatureKey(deviceId)` |
| `TemperatureDbKeyConstants` (**new**, carved out) | object | `STATUS_KEY`, `TEMPERATURE_KEY`, `HUMIDITY_KEY` |

## `home_assistant_api` module (stub)

| Type | Kind | Notes |
|---|---|---|
| `HomeAssistantPlugin` | class, implements `StatPlugin` (**new**) | `id = "home-assistant"`; no `collect()` override; `expose()` returns a `PluginRouteSet` with one placeholder `GET "/"` returning `200 OK` + empty body |

## Core (`src`) — unchanged behavior, reduced scope

| Type | Kind | Notes |
|---|---|---|
| `PluginRegistry` | class | unchanged logic, now imports `StatPlugin` from `plugin-api` |
| `StatsRepositoryRedisImpl` | class | unchanged — implements the `plugin-api`-hosted `StatsRepository` interface |
| `DependencyInjection.kt` | wiring | modified to construct `EpidemicStatsCollectorService`/`EpidemicStatsExposerService`/`EpidemicPlugin`, the temperature equivalents, and `HomeAssistantPlugin`, then `PluginRegistry(listOf(...))` — the sole place naming concrete plugin/service types |

## Deleted (no replacement, logic fully relocated)

- `StatsCollectorService` (core) — split per-domain into the two new collector services above.
- `ApplicationConstants.getEpidemicKey` / `.getTemperatureKey` (core) — relocated as `EpidemicKeyConstants.getEpidemicKey` / `TemperatureKeyConstants.getTemperatureKey`.
- `DbKeyConstants`'s domain-specific fields (core) — relocated into `EpidemicDbKeyConstants` / `TemperatureDbKeyConstants`.
