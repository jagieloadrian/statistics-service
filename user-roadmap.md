# StatisticsService — Roadmap to Universal Plugin-Based Stats Service

> This file is now the single source of truth for product direction. `agent-os/product/*.md` was the previous home for mission/roadmap/tech-stack, but Agent OS stopped being free — its content relevant to this project is folded in below and that folder should be treated as legacy/frozen going forward.

## Mission

StatisticsService is a lightweight, **pluggable** backend that ingests telemetry from arbitrary sources, persists raw time-series/run data to Redis Streams, computes chart-ready statistics, and exposes stable UI-facing APIs for web/desktop clients. Each data domain — epidemic, temperature, Home Assistant, whatever's next — is a plugin behind one shared collect/expose contract, so adding a source doesn't mean touching the core service. Targeted at local, low-friction, in-network deployments (hobbyist microcontrollers, home lab), not cloud SaaS.

## Tech stack

- Kotlin + Ktor (HTTP server), Gradle Kotlin DSL.
- Redis Streams for ingestion/time-series storage — already domain-agnostic (`StatsRepository`), reused unchanged by every plugin.
- JSON over HTTP; DTOs documented as contracts in `.docs/`.
- docker-compose for local Redis + RedisInsight (`.devops/redis-compose`) — backend itself still run directly, no compose/k8s service for it.
- Testing: JUnit unit + integration tests against the Redis contract.

## Status (verified against code, 2026-07-30)

Shipped:
- Ingestion API — `POST /api/v1/stats/collect/epidemic` and `/collect/temperature`, validated per `.docs`, with tests.
- Redis Streams storage & indexing — `StatsRepository` (streams + set indices per run/device).
- UI-facing endpoints — runs list, run timeline, run summary, temperature series, temperature summary.
- Local Redis deployment via docker-compose.

Deferred, unbuilt, not currently planned (carried over from an earlier generic discovery round, not aligned with the plugin-platform direction — resurrect only if a concrete need shows up):
- WebSocket live feeds, CSV/JSON export & CLI tooling, ClickHouse-scale analytics.
- Enterprise SSO/RBAC, PostgreSQL archival, minikube/k8s orchestration.

---

## Plugins

The plugin catalog — this is where each domain's contract lives, instead of scattered across services/routing/DI.

### Epidemic (built-in, pre-plugin-refactor)
- Contracts: `.docs/Epidemic_UI_API_Contract.md`, `.docs/RPI_Epidemic_Contract.md`.
- Collect: epidemic run/generation payloads from ESP32-style devices (push).
- Expose: run list, run timeline, run summary.
- Status: fully implemented as hardcoded methods in `StatsCollectorService`/`StatsExposerFacade`; migrates to `EpidemicPlugin` in Phase 2 below.

### Temperature (built-in, pre-plugin-refactor)
- Contracts: `.docs/Temperature_UI_API_Contract.md`, `.docs/RPI_Temperature_Contract.md`.
- Collect: device temperature/humidity samples (push).
- Expose: device list, series (with resolution), summary.
- Status: same as epidemic — migrates to `TemperaturePlugin` in Phase 2.

### Home Assistant (planned)
- No `.docs` contract yet — HA is pull-based (poll HA's REST API), not push-from-device like the two above.
- Expose-only plugin; no `collect()` side needed.
- Status: Phase 3 below.

### Adding a new plugin (the mechanism)

Once Phase 1/4 land, adding source #4+ is:
1. Implement `StatPlugin` (`id`, `collect()` if push-based, `expose()`).
2. Add validation inside the plugin's `collect()` (not a shared DTO-keyed `RequestValidation` block).
3. Add one line to the plugin registration list (`Plugins.kt`) — this feeds both DI and the registry.
4. (Optional) add a config entry if the plugin needs credentials/poll interval/entity filters.
5. Done — `/stats/collect/{pluginId}` and `/stats/expose/{pluginId}/**` route automatically, no routing-file edits.

No dynamic/jar loading, no plugin marketplace — these are Kotlin classes registered at compile time. Add loader machinery only if out-of-process/3rd-party plugins become a real need.

---

## Ground rules

- Every phase must leave the app fully working — epidemic/temperature endpoints keep responding identically throughout the migration (contract-tested against `.docs/*.md`).
- Each feature below is sized to be one Speckit `/specify` unit (one clear behavior, one acceptance check).

---

## Phase 1 — Plugin Contract (no behavior change)

Goal: introduce the interface the rest of the roadmap builds on, without moving any existing logic yet.

- **F1.1 — Define `StatPlugin` interface**
  `id: String`, `suspend fun collect(raw: JsonElement)`, `suspend fun expose(): PluginRouteSet` (split collect/expose into two smaller interfaces if a plugin is expose-only, e.g. Home Assistant).
  Acceptance: interface compiles, zero callers yet.

- **F1.2 — `PluginRegistry`**
  Simple `Map<String, StatPlugin>` built at startup from an explicit list (`listOf(EpidemicPlugin(...), TemperaturePlugin(...))`) — no reflection/ServiceLoader.
  Acceptance: registry resolves a plugin by id, throws a clear 404-mappable error for unknown id.

- **F1.3 — Generic Redis key namespacing per plugin**
  Replace `getEpidemicKey`/`getTemperatureKey` with one `ApplicationConstants.getKey(pluginId, deviceId, runId?)` builder. Existing keys must resolve to the exact same strings (`epidemic:device:x:run:y`) so no data migration is needed.
  Acceptance: existing Redis keys still read/write identically (covered by existing tests).

---

## Phase 2 — Migrate Epidemic & Temperature onto the contract

Goal: prove the contract by moving the two existing plugins (see catalog above) onto it. Pure refactor, no new features visible externally.

- **F2.1 — `EpidemicPlugin`** — wrap current `StatsCollectorService.saveEpidemicStats` + `EpidemicStatsExposerService` behind `StatPlugin`.
- **F2.2 — `TemperaturePlugin`** — same for temperature.
- **F2.3 — Generic collect route** — `POST /api/v1/stats/collect/{pluginId}` replacing the two hardcoded routes in `StatsCollectorRouting.kt`. Validation (`isEpidemicValid`/`isTemperatureDtoValid`) moves into each plugin's `collect()`.
- **F2.4 — Generic expose route** — `GET /api/v1/stats/expose/{pluginId}/**` replacing `StatsExposerFacade`'s hardcoded methods — the facade becomes a thin `registry[pluginId].expose()` dispatch.
- **F2.5 — Delete now-dead code** — remove `StatsCollectorService`'s and `StatsExposerFacade`'s domain methods, the two old routing files, once F2.3/F2.4 pass the existing test suite unchanged.

---

## Phase 3 — Home Assistant plugin (first real 3rd-party source)

Goal: prove the contract works for a *pull-based* source, not just push-from-microcontroller. This is the actual "universal" test — HA doesn't POST to us, we poll it.

- **F3.1 — HA REST client** — minimal Ktor HttpClient wrapper: fetch entity states from HA's `/api/states` (long-lived access token from config, not stored in code).
- **F3.2 — `HomeAssistantPlugin`** — expose-only, backed by a scheduled coroutine ticker (plain `kotlinx.coroutines` delay loop — no new scheduling library) that polls configured entity ids on an interval and writes them via the *same* `StatsRepository.saveStats`.
- **F3.3 — Config for entity selection** — `application.yaml` list of entity ids + poll interval. Skip a UI for this — config file is enough until someone asks for runtime toggling.

---

## Phase 4 — Plugin registration hygiene

Goal: make adding plugin #4 (and #5...) a one-line change, since that's the actual pain point once you have 3+.

- **F4.1 — Single plugin registration list** — one file (`Plugins.kt`) listing all active plugins, consumed by both DI (`DependencyInjection.kt`) and `PluginRegistry`. Adding a plugin = add one line here, not edit 3 files.
- **F4.2 — Per-plugin enable/disable via config** — `application.yaml` flag per plugin id, checked at registry build time. Only build this if you actually want to ship with a plugin disabled in some environment — otherwise skip, YAGNI.

---

## Phase 5 — Cross-plugin surface (only if a UI needs it)

- **F5.1 — `GET /api/v1/stats/plugins`** — list of registered plugin ids + a short descriptor, so a UI can discover what's available without hardcoding domain names.

---

## Backlog (not phased — revisit when there's a concrete trigger)

- Auth on collect endpoints (currently open to anything that can reach the service) — needed once this isn't just talking to trusted devices on a local network.
- Rate limiting on collect endpoints — needed once a device can misbehave and flood Redis.
- OpenAPI `describe {}` blocks are still hand-written per plugin route (F2.3/F2.4) — a generic schema-from-`StatPlugin` generator is a nice-to-have; don't build it until writing the third one hurts.
- WebSocket live feeds, CSV/JSON export & CLI tooling, ClickHouse-scale analytics — from the original scope, no current driver.
- Enterprise SSO/RBAC, PostgreSQL archival, minikube/k8s orchestration — carried over from an earlier generic discovery round (agent-os initial spec), never built, not implied by the plugin-platform direction. Left here only for visibility; treat as out of scope unless something concrete brings it back.

---

## Speckit pairing notes

Each `F#.#` above maps 1:1 to a Speckit feature spec: one clear "what changes" + acceptance line. Phases 1–2 should be spec'd and executed together (both refactor-only, no external contract change) before touching Phase 3, since Phase 3 is where the abstraction gets exercised by a source it wasn't originally designed around — if the interface is wrong, better to find out before Phase 4 locks in the registration pattern.
