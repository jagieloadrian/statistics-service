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
- Plugin contract (Phase 1) and Epidemic/Temperature migration onto it (Phase 2) — `StatPlugin`, `PluginRegistry`, generic `/stats/collect/{pluginId}` and `/stats/expose/{pluginId}/**` routes; old hardcoded routing/facade files deleted.
- Redis Streams storage & indexing — `StatsRepository` (streams + set indices per run/device).
- UI-facing endpoints — runs list, run timeline, run summary, temperature series, temperature summary — unchanged externally throughout the migration.
- Local Redis deployment via docker-compose.

Not yet done: plugins still live as classes inside core `src` instead of separate `/plugin` packages — that's Phase 2.5 below, the reason for this roadmap update.

Deferred, unbuilt, not currently planned (carried over from an earlier generic discovery round, not aligned with the plugin-platform direction — resurrect only if a concrete need shows up):
- WebSocket live feeds, CSV/JSON export & CLI tooling, ClickHouse-scale analytics.
- Enterprise SSO/RBAC, PostgreSQL archival, minikube/k8s orchestration.

---

## Plugins are packages, not classes in `src`

`StatPlugin`/`PluginRegistry`/the generic collect+expose routes already live in `src` (Phase 1/2 below are done). What's wrong today: `EpidemicPlugin`/`TemperaturePlugin` are Kotlin classes sitting inside the core module (`src/main/kotlin/.../plugin/`), wired by editing core's `DependencyInjection.kt` directly. That's still "hardcode a class into core", just behind an interface — it doesn't let a plugin be added as a separate build artifact, and it can't get to runtime drop-in later.

Target layout — each plugin is its own Gradle subproject under `/plugin`, depending on a small `:plugin-api` module (just `StatPlugin`, `PluginRouteSet`, the Redis key builder) that core also depends on. Core stops importing plugin classes by name; it only depends on `:plugin-api` and gets the concrete plugin list from `settings.gradle.kts` module wiring.

```
/plugin
  /plugin-api/                # StatPlugin, PluginRouteSet, key-builder — the only thing core AND plugins depend on
  /rpi_temperature_api/       # real: current TemperaturePlugin logic moves here
  /rpi_epidemic_api/          # real: current EpidemicPlugin logic moves here
  /home_assistant_api/        # stub only — real HA polling lives in a separate project, this is a wire-up shell
```

### rpi_temperature_api, rpi_epidemic_api (real)
- Contracts: `.docs/Temperature_UI_API_Contract.md` / `.docs/RPI_Temperature_Contract.md`; `.docs/Epidemic_UI_API_Contract.md` / `.docs/RPI_Epidemic_Contract.md`.
- Content is a move, not a rewrite: today's `EpidemicPlugin.kt`/`TemperaturePlugin.kt` + their exposer services relocate into their own module, unchanged behavior.
- Push-based collect + expose, same as now.

### home_assistant_api (stub)
- Real Home Assistant polling/integration is its own separate project (out of scope here) — this module is a wydmuszka: an empty `StatPlugin` implementation (`id = "home-assistant"`, `expose()` returns an empty/placeholder `PluginRouteSet`, no `collect()`) that proves the module wires into core's registry. Fill it in only when that separate project is ready to be pointed at.

### Generic service + UI contract stay in `src`
- `src` keeps: `PluginRegistry`, the generic `/stats/collect/{pluginId}` and `/stats/expose/{pluginId}/**` routes, Redis repository, DI plumbing.
- The UI-facing contract is fixed by the consumer, [StatisticUI](/home/diether18/IdeaProjects/Statistics-UI) — its `ApiUrls.kt` hits `/api/v1/stats/expose/temperature/...` and `/api/v1/stats/expose/epidemic/...` literally. Plugin `id`s and route shapes must keep resolving to those exact paths; treat `Statistics-UI/.docs/*_Contract.md` + `ApiUrls.kt` as the acceptance check for any plugin-module extraction.

### Adding a new plugin (the mechanism, updated)

1. `./gradlew` new subproject under `/plugin/<name>`, depending on `:plugin-api`.
2. Implement `StatPlugin` (`id`, `collect()` if push-based, `expose()`); validation lives inside `collect()`.
3. Add the module to `settings.gradle.kts` (`include(":plugin:<name>")`) and as a core dependency; add one line to the registration list feeding DI + `PluginRegistry`.
4. (Optional) config entry if the plugin needs credentials/poll interval/entity filters.
5. Done — `/stats/collect/{pluginId}` and `/stats/expose/{pluginId}/**` route automatically.

Still a compile-time, in-process Gradle multi-module setup — no ServiceLoader/jar-drop-in yet, that's a later runtime-loading step once a plugin needs to ship without a core rebuild. Don't build that loader until that's a real need.

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

## Phase 2 — Migrate Epidemic & Temperature onto the contract ✅ done

Goal: prove the contract by moving the two existing plugins onto it. Pure refactor, no new features visible externally.

- **F2.1 — `EpidemicPlugin`** ✅ wraps `StatsCollectorService` + `EpidemicStatsExposerService` behind `StatPlugin`.
- **F2.2 — `TemperaturePlugin`** ✅ same for temperature.
- **F2.3 — Generic collect route** ✅ `POST /api/v1/stats/collect/{pluginId}` (`PluginStatsRouting.kt`), validation moved into each plugin's `collect()`.
- **F2.4 — Generic expose route** ✅ `GET /api/v1/stats/expose/{pluginId}/**` (`PluginStatsRouting.kt`), dispatches via `registry.resolve(pluginId).expose()`.
- **F2.5 — Delete now-dead code** ✅ old `StatsCollectorRouting.kt`/`StatsExposerRouting.kt`/`StatsExposerFacade.kt` removed.

---

## Phase 2.5 — Extract plugins into `/plugin` Gradle modules

Goal: the actual gap this roadmap update is about. Phase 2 got the *interface* right but left `EpidemicPlugin`/`TemperaturePlugin` as classes inside core's `src` — this phase turns them into separately buildable packages, which is the prerequisite for "drop a plugin in at build time" and eventually runtime.

- **F2.5.1 — `:plugin:plugin-api` module** — extract `StatPlugin`, `PluginRouteSet`, and the Redis key builder (`ApplicationConstants.getKey`) into a module with no dependency on core; core and every plugin module depend on it, nothing depends on core.
- **F2.5.2 — `:plugin:rpi_temperature_api` module** — move `TemperaturePlugin` + `TemperatureStatsExposerService` here unchanged. Contract-check against `Statistics-UI/.docs/Temperature_UI_API_Contract.md` and `ApiUrls.kt`.
- **F2.5.3 — `:plugin:rpi_epidemic_api` module** — same move for `EpidemicPlugin` + `EpidemicStatsExposerService`. Contract-check against `Statistics-UI/.docs/Epidemic_UI_API_Contract.md` and `ApiUrls.kt`.
- **F2.5.4 — `:plugin:home_assistant_api` stub module** — empty `StatPlugin` (`id = "home-assistant"`, placeholder `expose()`), just proving a third module wires into the registry without touching core. Real logic stays in the separate HA project until that's ready to integrate.
- **F2.5.5 — Core stops importing plugin classes by name** — `DependencyInjection.kt`'s plugin list becomes the one place core references concrete plugin types (via their module dependency), everything else in core only sees `StatPlugin`/`PluginRegistry`.

Acceptance: `./gradlew build` builds each plugin as its own module; existing epidemic/temperature endpoints respond identically (same contract tests as Phase 2).

---

## Phase 3 — Home Assistant plugin (real integration, later)

Goal: once the separate Home Assistant project is ready, replace the `home_assistant_api` stub (F2.5.4) with the real pull-based integration. This is the actual "universal" test — HA doesn't POST to us, we poll it.

- **F3.1 — HA REST client** — minimal Ktor HttpClient wrapper: fetch entity states from HA's `/api/states` (long-lived access token from config, not stored in code).
- **F3.2 — Real `HomeAssistantPlugin`** — expose-only, backed by a scheduled coroutine ticker (plain `kotlinx.coroutines` delay loop — no new scheduling library) that polls configured entity ids on an interval and writes them via the *same* `StatsRepository.saveStats`.
- **F3.3 — Config for entity selection** — `application.yaml` list of entity ids + poll interval. Skip a UI for this — config file is enough until someone asks for runtime toggling.

---

## Phase 4 — Plugin registration hygiene

Goal: make adding plugin #4 (and #5...) a one-line change, since that's the actual pain point once you have 3+.

- **F4.1 — Single plugin registration list** — one file (`Plugins.kt`) listing all active plugin modules, consumed by both DI (`DependencyInjection.kt`) and `PluginRegistry`. Adding a plugin = add its module to `settings.gradle.kts` + one line here, not edit 3 files.
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

Each `F#.#` above maps 1:1 to a Speckit feature spec: one clear "what changes" + acceptance line. Phases 1–2 are done. Phase 2.5 (module extraction) should be spec'd and executed next, still refactor-only/no external contract change, before touching Phase 3, since Phase 3 is where the abstraction gets exercised by a source it wasn't originally designed around — if the interface is wrong, better to find out before Phase 4 locks in the registration pattern.
