# Tasks: Plugin Contract (Phase 1)

**Input**: Design documents from `/specs/001-plugin-contract/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/StatPlugin.md, quickstart.md

**Tests**: included — spec's acceptance scenarios (SC-001..SC-004) are test-verified, and constitution Principle III requires core-flow coverage for new plugin-package code (not exhaustive edge cases).

**Organization**: tasks grouped by user story (US1 = P1 contract, US2 = P2 registry, US3 = P3 key builder), per spec.md priorities.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies)
- File paths are exact, relative to repo root

## Path Conventions

Single Kotlin/Ktor project — `src/main/kotlin/com/anjo/statisticservice/`, `src/test/kotlin/com/anjo/statisticservice/` (per plan.md Project Structure).

---

## Phase 1: Setup

**Purpose**: confirm a clean baseline before touching anything (no project init needed — existing Gradle project).

- [X] T001 Run `./gradlew build test` from repo root, confirm all existing tests pass before any change (baseline for SC-002) — baseline: 33 tests, 2 pre-existing failures in `ApplicationTest` (health/ready endpoints, 404), unrelated to plugin work; noted, not fixed (out of scope)

---

## Phase 2: User Story 1 - Uniform plugin contract exists to build against (Priority: P1) 🎯 MVP

**Goal**: `StatPlugin` interface exists, compiles, has zero production callers.

**Independent Test**: `./gradlew compileKotlin` succeeds with the new interface present and unreferenced by any existing route/service/DI code.

### Implementation for User Story 1

- [X] T002 [US1] Create minimal `PluginRouteSet` marker type in `src/main/kotlin/com/anjo/statisticservice/plugin/PluginRouteSet.kt` (empty/placeholder shape — real content added in Phase 2 of the roadmap, not here)
- [X] T003 [US1] Create `StatPlugin` interface (`id: String`, `suspend fun collect(raw: JsonElement)` with default no-op, `suspend fun expose(): PluginRouteSet`) in `src/main/kotlin/com/anjo/statisticservice/plugin/StatPlugin.kt` (depends on T002 — `expose()` return type is `PluginRouteSet`, not parallelizable with it)

**Checkpoint**: `StatPlugin` + `PluginRouteSet` compile, zero callers (SC-004 for this slice). US2 can now start.

---

## Phase 3: User Story 2 - Plugins are resolvable by id through one registry (Priority: P2)

**Goal**: `PluginRegistry` resolves a `StatPlugin` by id from an explicit list; unknown id → clear 404-mappable error; duplicate id → fails fast at construction.

**Independent Test**: unit tests construct a registry directly (no HTTP route involved) and assert resolve/error/duplicate behavior.

**Depends on**: Phase 2 (US1) — needs the `StatPlugin` type.

### Tests for User Story 2

- [X] T004 [P] [US2] Write `PluginRegistryTest` in `src/test/kotlin/com/anjo/statisticservice/plugin/PluginRegistryTest.kt`: known id resolves to the registered instance; unknown id throws `EmptyDataException`; duplicate id in the input list fails registry construction (must fail before T005 passes)

### Implementation for User Story 2

- [X] T005 [US2] Implement `PluginRegistry` (built from explicit `List<StatPlugin>`, `resolve(pluginId): StatPlugin`, reuses `com.anjo.statisticservice.exception.EmptyDataException` for unknown id, throws on duplicate id) in `src/main/kotlin/com/anjo/statisticservice/plugin/PluginRegistry.kt` (depends on T003; makes T004 pass)

**Checkpoint**: registry lookup/error/duplicate-rejection all verified independently of any route. US1+US2 both functional in isolation.

---

## Phase 4: User Story 3 - Data location is derived the same way regardless of source (Priority: P3)

**Goal**: one generic `getKey(pluginId, deviceId, runId?)` produces byte-identical output to today's `getEpidemicKey`/`getTemperatureKey`.

**Independent Test**: unit test compares generic builder output against the existing builders for the same inputs — no dependency on US1/US2, storage tests untouched.

**Depends on**: nothing (independent of Phase 2/3, can run in parallel with them).

### Tests for User Story 3

- [X] T006 [P] [US3] Write `ApplicationConstantsKeyTest` in `src/test/kotlin/com/anjo/statisticservice/utils/ApplicationConstantsKeyTest.kt`: `getKey("epidemic", deviceId, runId)` == `getEpidemicKey(deviceId, runId)`; `getKey("temperature", deviceId, null)` == `getTemperatureKey(deviceId)`, for representative id values (must fail before T007 passes)

### Implementation for User Story 3

- [X] T007 [US3] Add `getKey(pluginId: String, deviceId: String, runId: String? = null): String` to `src/main/kotlin/com/anjo/statisticservice/utils/ApplicationConstants.kt`, alongside (not replacing) `getEpidemicKey`/`getTemperatureKey` (makes T006 pass; FR-007/FR-008 — old methods untouched, still callable)

**Checkpoint**: all three user stories independently functional; no existing route/service/DI file has been touched (FR-008/SC-002 preserved).

---

## Phase 5: Polish

**Purpose**: confirm the whole phase delivered zero observable behavior change.

- [X] T008 Run `./gradlew build test` again, confirm 100% of pre-existing tests still pass unchanged plus the three new test files pass (SC-001, SC-002, SC-003) — matches `quickstart.md` — 38 tests total, same 2 pre-existing unrelated failures, 5/5 new tests pass
- [X] T009 Run `grep -rn "StatPlugin\|PluginRegistry" src/main/kotlin --include=*.kt | grep -v "src/main/kotlin/com/anjo/statisticservice/plugin/"`, confirm zero hits — verifies SC-004 (zero production callers outside the new `plugin/` package) — confirmed, zero hits

---

## Dependencies & Execution Order

- **Setup (T001)**: no dependencies, run first.
- **US1 (T002-T003)**: depends on T001 only. Blocks US2.
- **US2 (T004-T005)**: depends on US1 (needs `StatPlugin` type).
- **US3 (T006-T007)**: depends on T001 only — independent of US1/US2, can be done in parallel.
- **Polish (T008-T009)**: depends on all of the above.

### Parallel Opportunities

- T002 and T003 are sequential (T003 needs T002's type), not parallel — both trivial/small, do them back-to-back.
- US3 (T006-T007) can be done in parallel with US1+US2 by a second contributor — no shared files.
- Within each story, the test task ([P]) can be written while implementation is drafted, but must fail before the implementation task lands (standard red-green).

---

## Implementation Strategy

### MVP First

1. T001 baseline.
2. Phase 2 (US1) — the contract itself; this alone satisfies "everything downstream can now be built against something stable" per spec.
3. Stop here if only the interface is needed immediately; Phase 3/4 can land in follow-up commits.

### Full Phase 1 (recommended, since roadmap Phase 2 needs all three)

1. T001 → T002→T003 (US1, sequential) → T004/T005 (US2) → T006/T007 (US3, may run in parallel with US1/US2) → T008-T009 polish.
2. Each user story is independently testable and committable — commit after each checkpoint.

## Notes

- No task touches `StatsCollectorService`, `StatsExposerFacade`, or any routing file — that's Phase 2 of `user-roadmap.md`, out of scope here (FR-008).
- 9 tasks total, 3 new production files, 2 new test files, 1 modified file (`ApplicationConstants.kt`).
