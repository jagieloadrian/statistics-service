# Implementation Plan: Extract Plugins into Separate `/plugin` Packages (Phase 2.5)

**Branch**: `003-plugin-modules` | **Date**: 2026-07-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-plugin-modules/spec.md`

## Summary

Split the monolithic Gradle build into a root `:plugin:plugin-api` module (the
domain-agnostic contract: `StatPlugin`, `PluginRouteSet`, `StatsRepository`
interface, the generic `getKey` builder, and the handful of truly common DB
field names), two real plugin modules (`:plugin:rpi_epidemic_api`,
`:plugin:rpi_temperature_api`) that each carry their own DTOs, validation,
collector/exposer logic and domain-specific field names, and one stub module
(`:plugin:home_assistant_api`). Core (`src`) keeps generic routing, DI wiring,
and the Redis-backed `StatsRepository` implementation, and stops importing
any concrete plugin class outside the DI registration list. Pure move — no
logic changes, no contract changes (FR-006).

## Technical Context

**Language/Version**: Kotlin (existing toolchain), JVM via Gradle Kotlin DSL

**Primary Dependencies**: Ktor 3.4.0 server-core (for `Route`/`get`/`respond`
in plugin modules), `kotlinx.serialization`, `kotlinx-datetime` — same as
today, no new deps; plugin modules gain their own `build.gradle.kts` declaring
the subset they actually use.

**Storage**: Redis (Streams) — `StatsRepositoryRedisImpl` and its Lettuce/DI
wiring stay in core; only the `StatsRepository` *interface* moves to
`:plugin:plugin-api` so plugin modules can depend on the abstraction without
depending on core.

**Testing**: JUnit + Kotest assertions — existing plugin tests
(`EpidemicPluginTest.kt`, `TemperaturePluginTest.kt`) relocate into their
module's own `src/test/kotlin`, same assertions, per constitution Principle
III (core flows, not exhaustive edge cases)

**Target Platform**: Linux server (existing Ktor deployment)

**Project Type**: Gradle multi-module — root/core application module plus
4 subprojects under `/plugin` (this *is* the feature)

**Performance Goals**: N/A — refactor, same request volume/shape as today

**Constraints**: FR-006 — zero externally-visible behavior change; FR-001/
FR-004 — plugin-api has zero core dependency, core references concrete
plugin types only in the DI registration list; SC-001 requires the existing
test suite to pass unchanged

**Scale/Scope**: 1 new module (`plugin-api`) + 2 modules carrying moved code
(`rpi_epidemic_api`, `rpi_temperature_api`) + 1 new stub module
(`home_assistant_api`); `settings.gradle.kts` + root `build.gradle.kts`
modified; `DependencyInjection.kt` modified to reference module packages;
no new business logic anywhere

## Constitution Check

*GATE: checked against `.specify/memory/constitution.md` v1.3.0*

- **I. Plugin Contract First** — this phase is what v1.3.0 of Principle I
  requires verbatim (plugins as separate Gradle subprojects depending only on
  `:plugin-api`, core free of plugin-class references outside the
  registration list); PASS.
- **II. Contract-Compliant Ingestion & Exposition** — `.docs` contracts and
  Statistics-UI's expected URLs are unchanged, only source-file location
  moves (FR-006); PASS.
- **III. Test Coverage for Core Flows** — existing plugin/routing tests move
  with their code and keep the same assertions; no new coverage needed beyond
  the stub module's minimal startup/resolve test (User Story 3); PASS.
- **IV. Local-First, Minimal-Ops Simplicity** — no dynamic/jar loading added
  (Assumptions: build-time Gradle subprojects only); no speculative loader
  machinery; PASS.
- **V. Storage & API Modularity** — `StatsRepository` stays the single
  storage abstraction, now formally exposed from `plugin-api` so every plugin
  depends on the interface, never a concrete Redis class; key building stays
  centralized (generic `getKey` in `plugin-api`, domain-specific legacy
  `getEpidemicKey`/`getTemperatureKey` move with their respective plugin,
  since those are domain logic, not shared infrastructure); PASS.

No violations. Complexity Tracking not needed — the multi-module split is the
principle's requirement, not a deviation from it.

## Project Structure

### Documentation (this feature)

```text
specs/003-plugin-modules/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── module-boundaries.md
└── tasks.md
```

### Source Code (repository root)

```text
settings.gradle.kts                      # MODIFIED — include(":plugin:plugin-api", ":plugin:rpi_epidemic_api", ":plugin:rpi_temperature_api", ":plugin:home_assistant_api")
build.gradle.kts                         # MODIFIED — core depends on all 4 plugin modules (project(":plugin:...")), drops now-moved code's now-unused deps if any

plugin/
├── plugin-api/
│   ├── build.gradle.kts                 # NEW — kotlin("jvm"), kotlinx-coroutines-core, ktor-server-core (for Route/PluginRouteSet), no core dependency
│   └── src/main/kotlin/com/anjo/statisticservice/
│       ├── plugin/
│       │   ├── StatPlugin.kt            # MOVED unchanged (was src/main/.../plugin/StatPlugin.kt)
│       │   └── PluginRouteSet.kt        # MOVED unchanged
│       ├── repository/
│       │   └── StatsRepository.kt       # MOVED unchanged (interface only — StatsRepositoryRedisImpl stays in core)
│       └── utils/
│           ├── ApplicationConstants.kt  # MOVED, trimmed to API_BASE_PATH + generic getKey() — getEpidemicKey/getTemperatureKey move out (domain-specific)
│           └── DbKeyConstants.kt        # MOVED, trimmed to the 3 "common" fields (DEVICE_ID_KEY, RUN_ID_KEY, TIMESTAMP_KEY) — domain fields move out
│
├── rpi_epidemic_api/
│   ├── build.gradle.kts                 # NEW — depends on :plugin:plugin-api, kotlinx-serialization, kotlinx-datetime, kotlin-logging
│   └── src/main/kotlin/com/anjo/statisticservice/
│       ├── plugin/
│       │   └── EpidemicPlugin.kt        # MOVED unchanged (constructs its own collector/exposer now instead of receiving core's StatsCollectorService)
│       ├── service/
│       │   ├── EpidemicStatsCollectorService.kt   # NEW — carved out of core's StatsCollectorService (saveEpidemicStats + prepareEpidemicBody + saveStatistics, epidemic-only)
│       │   └── EpidemicStatsExposerService.kt     # MOVED unchanged (was service/exposer/...)
│       ├── model/
│       │   ├── dto/EpidemicDto.kt                 # MOVED unchanged
│       │   └── responsedto/EpidemicResponseDto.kt # MOVED unchanged
│       ├── validation/EpidemicValidation.kt       # MOVED unchanged (isEpidemicValid)
│       └── utils/
│           ├── EpidemicKeyConstants.kt            # NEW — getEpidemicKey() + EPIDEMIC_KEYS, carved out of core's ApplicationConstants
│           └── EpidemicDbKeyConstants.kt          # NEW — epidemic-only fields carved out of core's DbKeyConstants
│
├── rpi_temperature_api/
│   ├── build.gradle.kts                 # NEW — same shape as rpi_epidemic_api's
│   └── src/main/kotlin/com/anjo/statisticservice/
│       ├── plugin/TemperaturePlugin.kt             # MOVED unchanged
│       ├── service/
│       │   ├── TemperatureStatsCollectorService.kt # NEW — carved out of core's StatsCollectorService (saveTemperatureStats + prepareTemperatureBody)
│       │   └── TemperatureStatsExposerService.kt   # MOVED unchanged
│       ├── model/
│       │   ├── Resolution.kt                       # MOVED unchanged
│       │   ├── dto/TemperatureDto.kt                # MOVED unchanged
│       │   └── responsedto/TemperatureResponseDto.kt # MOVED unchanged
│       ├── validation/TemperatureValidation.kt      # MOVED unchanged (isTemperatureDtoValid)
│       └── utils/
│           ├── TemperatureKeyConstants.kt           # NEW — getTemperatureKey() + TEMPERATURE_KEYS
│           └── TemperatureDbKeyConstants.kt         # NEW — temperature-only fields (STATUS_KEY, TEMPERATURE_KEY, HUMIDITY_KEY)
│
└── home_assistant_api/
    ├── build.gradle.kts                 # NEW — depends only on :plugin:plugin-api
    └── src/main/kotlin/com/anjo/statisticservice/plugin/
        └── HomeAssistantPlugin.kt        # NEW — id = "home-assistant", collect() absent (expose-only per StatPlugin default), expose() returns a placeholder PluginRouteSet (e.g. GET "/" -> 200 empty list)

src/main/kotlin/com/anjo/statisticservice/
├── plugin/
│   ├── StatPlugin.kt                    # DELETED (moved to plugin-api)
│   ├── PluginRouteSet.kt                # DELETED (moved to plugin-api)
│   ├── EpidemicPlugin.kt                # DELETED (moved to rpi_epidemic_api)
│   ├── TemperaturePlugin.kt             # DELETED (moved to rpi_temperature_api)
│   └── PluginRegistry.kt                # UNCHANGED — still core, now imports StatPlugin from plugin-api
├── repository/
│   ├── StatsRepository.kt               # DELETED (moved to plugin-api)
│   └── StatsRepositoryRedisImpl.kt      # UNCHANGED — implements the plugin-api interface
├── service/
│   ├── StatsCollectorService.kt         # DELETED — split into EpidemicStatsCollectorService/TemperatureStatsCollectorService in their plugin modules
│   └── exposer/                         # DELETED — both exposer services moved to their plugin modules
├── utils/
│   ├── ApplicationConstants.kt          # MODIFIED — only API_BASE_PATH + getKey() remain (rest moved to plugin-api and the two plugin modules)
│   └── DbKeyConstants.kt                # DELETED — split across plugin-api (common) + each plugin module (domain fields)
└── di/
    └── DependencyInjection.kt           # MODIFIED — provides RedisConfig/RedisClientProvider/StatsRepositoryRedisImpl (as StatsRepository), then EpidemicStatsCollectorService+EpidemicStatsExposerService+EpidemicPlugin, TemperatureStatsCollectorService+TemperatureStatsExposerService+TemperaturePlugin, HomeAssistantPlugin, then PluginRegistry — the one place naming concrete plugin/service types

src/test/kotlin/com/anjo/statisticservice/
├── plugin/
│   ├── EpidemicPluginTest.kt            # MOVED to plugin/rpi_epidemic_api/src/test/kotlin/...
│   └── TemperaturePluginTest.kt         # MOVED to plugin/rpi_temperature_api/src/test/kotlin/...
├── service/StatsCollectorServiceTest.kt # SPLIT/MOVED alongside the collector classes into their plugin modules
└── (routing tests for /collect/{pluginId}, /expose/{pluginId}/** stay in core — they test the generic dispatch layer, not plugin-internal logic)
```

**Structure Decision**: Gradle multi-module. `plugin-api` is the only module
every other module (including core) may depend on; the three concrete plugin
modules depend only on `plugin-api`; core depends on `plugin-api` plus all
concrete plugin modules (to build the DI registration list) but no concrete
plugin module ever depends on core or on a sibling plugin module. This
dependency direction is the enforcement mechanism for FR-001/FR-004 — a
plugin *cannot* accidentally reach into core internals because core isn't on
its compile classpath. `StatsCollectorService` (a single class straddling
both domains) is retired rather than moved, since keeping it would force
either a shared dependency the domains don't need or leaving domain logic in
core — splitting it in two, one per plugin module, is the smaller change per
the actual per-domain methods it already contained (see `research.md`).

## Complexity Tracking

*No constitution violations — table not needed.*
