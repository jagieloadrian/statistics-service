# Implementation Plan: Migrate Epidemic & Temperature onto the Plugin Contract (Phase 2)

**Branch**: `002-migrate-plugins` | **Date**: 2026-07-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-migrate-plugins/spec.md`

## Summary

Wrap today's hardcoded epidemic/temperature collect+expose logic behind
`EpidemicPlugin`/`TemperaturePlugin` (Phase 1's `StatPlugin` contract), introduce
generic `POST /api/v1/stats/collect/{pluginId}` and
`GET /api/v1/stats/expose/{pluginId}/**` routes dispatching through the Phase 1
`PluginRegistry`, prove zero behavior change against the existing test suite,
then delete the old hardcoded service methods and routing files. Both plugins
delegate to the *existing* service classes/validation functions unchanged
(reuse, not reimplementation) — the only thing that moves is the trigger point.

## Technical Context

**Language/Version**: Kotlin (existing toolchain), JVM via Gradle Kotlin DSL

**Primary Dependencies**: Ktor 3.4.0, `kotlinx.serialization` — same as Phase 1, no new deps

**Storage**: Redis (Streams) via existing `StatsRepository` — unchanged, plugins delegate to it through the existing service classes

**Testing**: JUnit + Kotest assertions (existing suite), per constitution Principle III — core flows, not exhaustive edge cases

**Target Platform**: Linux server (existing Ktor deployment)

**Project Type**: Single Kotlin/Ktor backend project (existing structure, no new module)

**Performance Goals**: N/A — refactor, same request volume/shape as today

**Constraints**: FR-010 — zero externally-visible behavior change; SC-001/SC-002 require byte-identical responses and zero regressions in the existing test suite

**Scale/Scope**: 2 new plugin classes, 1 new generic routing file (replacing 2 old ones), 1 new exception type, ~4-5 modified files (DI, routing entry point, exception handler), deletion of 2 old routing files + hardcoded methods once proven

## Constitution Check

*GATE: checked against `.specify/memory/constitution.md` v1.2.0*

- **I. Plugin Contract First** — this phase proves the contract with real domains; PASS.
- **II. Contract-Compliant Ingestion & Exposition** — `.docs` contracts unchanged, same payloads/responses (FR-007, FR-010); PASS.
- **III. Test Coverage for Core Flows** — existing tests re-targeted at generic routes prove equivalence (FR-007); new tests only for the thin dispatch/validation-exception-mapping layer, not re-testing existing business logic; PASS.
- **IV. Local-First, Minimal-Ops Simplicity** — no new deps, no speculative genericization (explicitly deferred per Clarifications); PASS.
- **V. Storage & API Modularity** — plugins delegate to the unchanged `StatsRepository`-backed services; PASS.

No violations. Complexity Tracking not needed.

## Project Structure

### Documentation (this feature)

```text
specs/002-migrate-plugins/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md
```

### Source Code (repository root)

```text
src/main/kotlin/com/anjo/statisticservice/
├── plugin/
│   ├── StatPlugin.kt                   # UNCHANGED (Phase 1)
│   ├── PluginRegistry.kt               # UNCHANGED (Phase 1)
│   ├── PluginRouteSet.kt               # MODIFIED — becomes a Route.() -> Unit holder (was empty placeholder)
│   ├── EpidemicPlugin.kt               # NEW — delegates to existing StatsCollectorService + EpidemicStatsExposerService
│   └── TemperaturePlugin.kt            # NEW — delegates to existing StatsCollectorService + TemperatureStatsExposerService
├── exception/
│   └── Exceptions.kt                   # MODIFIED — add PluginValidationException(reasons: List<String>)
├── routing/
│   ├── PluginStatsRouting.kt           # NEW — generic POST /collect/{pluginId} + per-plugin GET /expose/{pluginId}/** mounting
│   ├── GlobalExceptionHandler.kt       # MODIFIED — map PluginValidationException to 400 + reasons (same shape as today's RequestValidationException handling)
│   ├── StatsCollectorRouting.kt        # DELETED once FR-007 proven (old hardcoded /collect/epidemic, /collect/temperature + validationStatsRequestBody())
│   ├── StatsExposerRouting.kt          # DELETED once FR-007 proven (old hardcoded /expose/epidemic/**, /expose/temperature/**)
│   └── Routing.kt                      # MODIFIED — wire pluginStatsRoutes(registry) instead of the deleted route functions
├── service/
│   ├── StatsCollectorService.kt        # UNCHANGED until delete-step — plugin delegates to its existing save methods as-is
│   └── exposer/
│       ├── EpidemicStatsExposerService.kt     # UNCHANGED — plugin delegates to it as-is
│       ├── TemperatureStatsExposerService.kt  # UNCHANGED — plugin delegates to it as-is
│       └── StatsExposerFacade.kt              # DELETED once FR-007 proven (was only a thin dispatch layer, now redundant with PluginRegistry)
└── di/
    └── DependencyInjection*.kt          # MODIFIED — register EpidemicPlugin/TemperaturePlugin + PluginRegistry, drop StatsExposerFacade wiring once deleted

src/test/kotlin/com/anjo/statisticservice/
├── routing/
│   ├── CollectStatisticRoutesTest.kt        # MODIFIED — point at generic /collect/{pluginId}, same assertions (proves FR-007/SC-001)
│   └── StatsEpidemicExposerRoutingTest.kt   # MODIFIED — point at generic /expose/{pluginId}/**, same assertions
├── plugin/
│   ├── EpidemicPluginTest.kt            # NEW — thin: valid/invalid payload → same result as isEpidemicValid()/StatsCollectorService today
│   └── TemperaturePluginTest.kt         # NEW — same for temperature
└── (StatsCollectorServiceTest.kt stays, since the service class itself is reused, not deleted, until FR-008 removal step)
```

**Structure Decision**: single existing Kotlin/Ktor project. Plugins are thin
delegation wrappers around the *existing, unchanged* service classes — the
migration is at the routing/dispatch layer, not a rewrite of business logic.
Old routing files and `StatsExposerFacade` are deleted only after the generic
routes pass the (re-pointed) existing test suite unchanged, per FR-008/FR-009
and User Story 3's ordering ("only after proven equivalent"). Concretely
(see tasks.md): the new generic routes are wired in *alongside* the old ones
first, both coexisting while equivalence is proven, and only US3 removes the
old routes — not a direct swap-then-delete.

## Complexity Tracking

*No constitution violations — table not needed.*
