# Implementation Plan: Plugin Contract (Phase 1)

**Branch**: `001-plugin-contract` | **Date**: 2026-07-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-plugin-contract/spec.md`

## Summary

Introduce a `StatPlugin` contract (id + optional `collect()` + `expose()`), a
`PluginRegistry` that resolves plugins by id from one explicit compile-time list,
and one generic Redis key builder replacing `getEpidemicKey`/`getTemperatureKey`.
Purely additive — nothing existing is rewired to use it yet (Phase 2 does that).
No new dependency; all pieces are plain Kotlin classes added alongside current code.

## Technical Context

**Language/Version**: Kotlin (existing project toolchain), JVM via Gradle Kotlin DSL

**Primary Dependencies**: Ktor 3.4.0, `kotlinx.serialization` (JsonElement for
`collect(raw)` payload type) — both already in `build.gradle.kts`, no new deps

**Storage**: Redis (Streams) via existing `StatsRepository` / `RedisClientProvider` — unchanged this phase

**Testing**: JUnit (existing `src/test/kotlin` suite, per constitution Principle III — core flows only)

**Target Platform**: Linux server (existing Ktor deployment)

**Project Type**: Single Kotlin/Ktor backend project (existing structure, no new module)

**Performance Goals**: N/A — internal refactor-prep, no new runtime path exercised by clients this phase

**Constraints**: FR-007/FR-008 — generated keys and observable endpoint behavior MUST be byte-identical to today's; zero behavior change

**Scale/Scope**: 3 new small files (interface, registry, key builder) + matching unit tests; no route/DI wiring changes

## Constitution Check

*GATE: checked against `.specify/memory/constitution.md` v1.2.0*

- **I. Plugin Contract First** — this phase *is* the contract; PASS.
- **II. Contract-Compliant Ingestion & Exposition** — no `.docs` contract changes, no route changes; PASS (FR-008/FR-009).
- **III. Test Coverage for Core Flows** — unit tests for registry lookup + key-builder parity with existing key strings; no exhaustive edge-case suite required; PASS.
- **IV. Local-First, Minimal-Ops Simplicity** — explicit list, no reflection/ServiceLoader, no loader machinery; PASS.
- **V. Storage & API Modularity** — key builder becomes the one place Redis keys are built, keyed per plugin id; PASS.

No violations. Complexity Tracking not needed.

## Project Structure

### Documentation (this feature)

```text
specs/001-plugin-contract/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/            # Phase 1 output (StatPlugin Kotlin interface contract)
└── tasks.md              # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
src/main/kotlin/com/anjo/statisticservice/
├── plugin/                          # NEW package
│   ├── StatPlugin.kt                # FR-001 — id, optional collect(), expose()
│   ├── PluginRegistry.kt            # FR-002..FR-005 — id → StatPlugin, explicit list, dup/unknown-id errors
│   └── PluginRouteSet.kt            # return type of expose(); shape TBD by Phase 2, minimal marker type here
├── utils/
│   └── ApplicationConstants.kt      # MODIFIED — add getKey(pluginId, deviceId, runId?) (FR-006/FR-007)
└── exception/
    └── Exceptions.kt                # reuse EmptyDataException (already 404-mapped) for unknown plugin id — no new exception type

src/test/kotlin/com/anjo/statisticservice/
└── plugin/                          # NEW
    ├── PluginRegistryTest.kt        # known id resolves, unknown id → EmptyDataException, duplicate id → fails registration
    └── ApplicationConstantsKeyTest.kt  # getKey(...) output == existing getEpidemicKey/getTemperatureKey output
```

**Structure Decision**: single existing Kotlin/Ktor project, no new module. One new
`plugin` package under `com.anjo.statisticservice` holds the contract + registry;
the key builder lands in the existing `utils/ApplicationConstants.kt` next to the
methods it replaces (not a new file) so the "identical output" requirement (FR-007)
is easy to eyeball in review. `getEpidemicKey`/`getTemperatureKey` are left in place
this phase (Phase 1 is additive-only, FR-008) — Phase 2 deletes them once
`EpidemicPlugin`/`TemperaturePlugin` call the generic builder instead.

## Complexity Tracking

*No constitution violations — table not needed.*
