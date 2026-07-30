# Tasks: Migrate Epidemic & Temperature onto the Plugin Contract (Phase 2)

**Input**: Design documents from `/specs/002-migrate-plugins/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/plugin-routing.md, quickstart.md

**Tests**: included — spec's SC-001/SC-002/SC-003 are test-verified; constitution Principle III requires core-flow coverage (not exhaustive edge cases) for new plugin code, plus re-running the *existing, unmodified* route tests to prove equivalence.

**Organization**: tasks grouped by user story (US1 = P1 collect, US2 = P1 expose, US3 = P2 cleanup), per spec.md priorities.

**Note on FR-008 wording**: spec.md FR-008 says "StatsCollectorService's domain-specific save methods" get deleted alongside the dead routing files. Per `research.md`'s "delegate, don't reimplement" decision, `StatsCollectorService.saveEpidemicStats`/`saveTemperatureStats` are the *actual persistence logic* the plugins call — they are not dead code, they're just called from a new place. Only the pure-dispatch layers genuinely made redundant by `PluginRegistry` (`StatsCollectorRouting.kt`, `StatsExposerRouting.kt`, `StatsExposerFacade.kt`) are deleted in US3. This matches `plan.md`'s already-stated Project Structure. Flagging here rather than silently diverging from the spec text.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies)
- File paths are exact, relative to repo root

## Path Conventions

Single Kotlin/Ktor project — `src/main/kotlin/com/anjo/statisticservice/`, `src/test/kotlin/com/anjo/statisticservice/`.

---

## Phase 1: Setup

- [X] T001 Run `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build test` from repo root (with local Redis up via `.devops/redis-compose`), confirm baseline: only the known pre-existing `ApplicationTest` health/ready failures, nothing else — baseline for SC-002 — confirmed: 38 tests, same 2 known failures

---

## Phase 2: User Story 1 - Epidemic and temperature data flow through the same generic collect route (Priority: P1) 🎯 MVP

**Goal**: `POST /api/v1/stats/collect/{pluginId}` produces byte-identical results to today's hardcoded `/collect/epidemic` and `/collect/temperature`, for both valid and invalid payloads.

**Independent Test**: re-run the existing `CollectStatisticRoutesTest` (paths unchanged — `pluginId` = `epidemic`/`temperature` reproduces today's exact URLs) against the new route, unmodified assertions.

**Depends on**: Phase 1 baseline.

### Implementation for User Story 1

- [X] T002 [US1] Add `PluginValidationException(val reasons: List<String>) : Exception()` to `src/main/kotlin/com/anjo/statisticservice/exception/Exceptions.kt`
- [X] T003 [US1] Add a `StatusPages` handler for `PluginValidationException` in `src/main/kotlin/com/anjo/statisticservice/routing/GlobalExceptionHandler.kt`: `call.respond(HttpStatusCode.BadRequest, cause.reasons)` — identical shape to the existing `RequestValidationException` handler (depends on T002)

### Tests for User Story 1

- [X] T004 [P] [US1] Write `EpidemicPluginTest` in `src/test/kotlin/com/anjo/statisticservice/plugin/EpidemicPluginTest.kt`: valid `EpidemicDto` JSON → `collect()` delegates to a (mocked) `StatsCollectorService.saveEpidemicStats`; invalid payload → `collect()` throws `PluginValidationException` with the exact same reasons `isEpidemicValid()` produces (must fail before T006)
- [X] T005 [P] [US1] Write `TemperaturePluginTest` in `src/test/kotlin/com/anjo/statisticservice/plugin/TemperaturePluginTest.kt`: same pattern using `isTemperatureDtoValid()` and `StatsCollectorService.saveTemperatureStats` (must fail before T007)

### Implementation for User Story 1 (continued)

- [X] T006 [US1] Create `EpidemicPlugin` in `src/main/kotlin/com/anjo/statisticservice/plugin/EpidemicPlugin.kt`: `id = "epidemic"`, constructor takes `StatsCollectorService` + `EpidemicStatsExposerService`, `collect(raw)` decodes to `EpidemicDto`, calls `isEpidemicValid()`, throws `PluginValidationException` on failure else calls `saveEpidemicStats(dto)`; `expose()` temporarily `TODO("implemented in US2")` so the class compiles (depends on T002, makes T004 pass)
- [X] T007 [US1] Create `TemperaturePlugin` in `src/main/kotlin/com/anjo/statisticservice/plugin/TemperaturePlugin.kt`: same pattern for temperature, `expose()` temporarily `TODO()` (depends on T002, makes T005 pass)
- [X] T008 [US1] Create `src/main/kotlin/com/anjo/statisticservice/routing/PluginStatsRouting.kt` with a `pluginCollectRoute(registry: PluginRegistry)` function: `POST ${API_BASE_PATH}/stats/collect/{pluginId}` resolves the plugin via `registry.resolve(pluginId)`, reads the body as `JsonElement`, calls `plugin.collect(raw)`, responds `200 OK` (depends on T006, T007)
- [X] T009 [US1] Register `EpidemicPlugin`/`TemperaturePlugin` + a `PluginRegistry` in `src/main/kotlin/com/anjo/statisticservice/di/DependencyInjection.kt`, and wire `pluginCollectRoute(registry)` into `src/main/kotlin/com/anjo/statisticservice/routing/Routing.kt`. **Correction discovered during implementation**: "coexist alongside" from the original task description is impossible here — the new route uses the exact same URL (`/collect/epidemic`), and Ktor always matches a literal path ahead of a parameterized one, so the old `collectStatisticRoutes(...)` call would silently keep shadowing the new route and T010 would prove nothing. Removed the `collectStatisticRoutes(...)` call from `Routing.kt` instead (the old *file* `StatsCollectorRouting.kt` still exists, unused, until US3 deletes it) — this is the actual cutover point, US3 just removes the now-dead file (depends on T008)
- [X] T010 [US1] Run `CollectStatisticRoutesTest` — passes unchanged against the new generic route (same URL strings, since `pluginId` = `epidemic`/`temperature`), proving SC-001/FR-007 for collect (depends on T009) — confirmed green, plus both new `EpidemicPluginTest`/`TemperaturePluginTest`

**Checkpoint**: collect path fully proven equivalent. US2 can now start (independent files, but same plugin classes get extended).

---

## Phase 3: User Story 2 - UI-facing reads keep working through the same generic expose route (Priority: P1)

**Goal**: `GET /api/v1/stats/expose/{pluginId}/**` produces byte-identical results to today's hardcoded expose endpoints.

**Independent Test**: re-run `StatsEpidemicExposerRoutingTest` (paths unchanged) against the new mounting.

**Depends on**: Phase 2 (T006/T007 created the plugin classes this phase extends).

### Implementation for User Story 2

- [X] T011 [US2] Change `PluginRouteSet` in `src/main/kotlin/com/anjo/statisticservice/plugin/PluginRouteSet.kt` from an empty placeholder to `class PluginRouteSet(val configure: Route.() -> Unit)`
- [X] T012 [US2] Implement `EpidemicPlugin.expose()` in `src/main/kotlin/com/anjo/statisticservice/plugin/EpidemicPlugin.kt` (replacing the T006 `TODO`): returns a `PluginRouteSet` registering `get("/runs")`, `get("/device/{deviceId}/run/{runId}")`, `get("/device/{deviceId}/run/{runId}/summary")`, delegating to `EpidemicStatsExposerService` exactly as `StatsExposerRouting.kt` does today (depends on T011, T006)
- [X] T013 [US2] Implement `TemperaturePlugin.expose()` in `src/main/kotlin/com/anjo/statisticservice/plugin/TemperaturePlugin.kt` (replacing the T007 `TODO`): `get("/devices")`, `get("/devices/{deviceId}")`, `get("/devices/{deviceId}/summary")`, delegating to `TemperatureStatsExposerService` (depends on T011, T007)
- [X] T014 [US2] In `src/main/kotlin/com/anjo/statisticservice/routing/PluginStatsRouting.kt`, add a `pluginExposeRoutes(registry: PluginRegistry)` function: for each plugin in the registry, `route("${API_BASE_PATH}/stats/expose/${plugin.id}") { plugin.expose().configure(this) }`, called once at startup via `runBlocking`; **then** one catch-all fallback *after* those, resolving via `PluginRegistry`. Required adding `PluginRegistry.all(): Collection<StatPlugin>` (small, minimal addition — `PluginRegistry` only exposed `resolve()` before) since mounting per-plugin routes needs to iterate all registered plugins, not just look one up (depends on T012, T013)
- [X] T015 [US2] Wire `pluginExposeRoutes(registry)` into `src/main/kotlin/com/anjo/statisticservice/routing/Routing.kt`. **Same correction as T009**: replaced the `exposeEpidemicData(...)`/`exposeTemperatureData(...)` calls rather than keeping them alongside — identical-URL shadowing issue applies here too (depends on T014)
- [X] T016 [US2] Run `StatsEpidemicExposerRoutingTest` — passes unchanged against the new mounting, proving SC-001/FR-007 for expose (temperature has no dedicated route test today; verify via `quickstart.md`'s manual check instead). Also manually hit `GET /api/v1/stats/expose/unknown-plugin/anything` and confirm the same 404 + `EmptyDataException` message shape collect already returns for an unknown id (SC-004) (depends on T015) — confirmed: `StatsEpidemicExposerRoutingTest` 6/6 pass; manual check on unknown pluginId returns `404` + `Unknown plugin id: unknown-plugin` for both collect and expose

**Checkpoint**: both collect and expose fully proven equivalent through the generic routes, old routes still present as a safety net. US3 (cleanup) can now start.

---

## Phase 4: User Story 3 - Dead hardcoded code is removed once the migration is proven safe (Priority: P2)

**Goal**: old hardcoded routing files and the now-redundant `StatsExposerFacade` are deleted; project still builds and tests still pass.

**Independent Test**: after deletion, `./gradlew build test` passes and a repo-wide grep for the deleted symbols returns zero hits.

**Depends on**: Phase 2 (T010) and Phase 3 (T016) both green — the equivalence proof is the gate (per spec.md User Story 3 / Assumptions).

### Implementation for User Story 3

- [X] T017 [US3] Delete `src/main/kotlin/com/anjo/statisticservice/routing/StatsCollectorRouting.kt` and `src/main/kotlin/com/anjo/statisticservice/routing/StatsExposerRouting.kt`; delete `src/main/kotlin/com/anjo/statisticservice/service/exposer/StatsExposerFacade.kt`. Also deleted `src/test/kotlin/com/anjo/statisticservice/service/exposer/StatsExposerFacadeTest.kt`, discovered mid-task — tests the now-deleted class, not accounted for in the original task list (depends on T010, T016)
- [X] T018 [US3] Remove the now-dangling calls in `src/main/kotlin/com/anjo/statisticservice/routing/Routing.kt` — `collectStatisticRoutes(...)`/`exposeEpidemicData(...)`/`exposeTemperatureData(...)` were already removed in T009/T015 (the swap correction); this step only removed the now-dead `validationStatsRequestBody()` call, leaving `pluginCollectRoute(registry)` + `pluginExposeRoutes(registry)` (depends on T017)
- [X] T019 [US3] Remove `StatsExposerFacade` wiring from `src/main/kotlin/com/anjo/statisticservice/di/DependencyInjection.kt` (`EpidemicStatsExposerService`/`TemperatureStatsExposerService` stay registered — the plugins now depend on them directly) (depends on T018)
- [X] T020 [US3] Run `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew build test`, confirm it builds and all tests pass — same baseline as T001 (SC-002) (depends on T019) — confirmed: 36 tests, same 2 known `ApplicationTest` failures, zero new failures
- [X] T021 [US3] Run `grep -rn "StatsExposerFacade\|StatsCollectorRouting\|StatsExposerRouting" src/main/kotlin src/test/kotlin`, confirm zero hits (SC-003) (depends on T020) — confirmed, zero hits

**Checkpoint**: migration complete, dead code removed, plugin contract is now the only path for epidemic/temperature.

---

## Phase 5: Polish

- [X] T022 Run `quickstart.md` end to end (build, test, manual sanity check) one final time; confirm no behavior difference beyond the known pre-existing `ApplicationTest` failures noted in T001 — confirmed clean

---

## Dependencies & Execution Order

- **Setup (T001)**: no dependencies, run first.
- **US1 (T002-T010)**: depends on T001. T002→T003 sequential (exception then handler). T004/T005 [P] (different test files). T006/T007 depend on T002 (and are unblocked in parallel by each other — different files — but each is gated by its own test, T004/T005 respectively). T008 depends on T006+T007. T009 depends on T008. T010 depends on T009.
- **US2 (T011-T016)**: depends on US1's T006/T007 (extends the same plugin files). T011 first (type change), then T012/T013 [P] (different files), T014 depends on both, T015 depends on T014, T016 depends on T015.
- **US3 (T017-T021)**: depends on US1 (T010) AND US2 (T016) both green. Strictly sequential T017→T018→T019→T020→T021 (each edits/depends on the previous step's file state).
- **Polish (T022)**: depends on all of the above.

### Parallel Opportunities

- T004 and T005 (tests) in parallel — different files, both gate different plugin classes.
- T006 and T007 (plugin collect implementations) in parallel once T002 lands — different files.
- T012 and T013 (plugin expose implementations) in parallel once T011 lands — different files.
- US1 and US2 cannot run fully in parallel — both touch `EpidemicPlugin.kt`/`TemperaturePlugin.kt` (collect vs expose halves of the same class), so treat as sequential per-plugin-file even though they're different *methods*.

---

## Implementation Strategy

### MVP First

1. T001 baseline.
2. Phase 2 (US1) — collect path proven equivalent. This alone is a safely-shippable increment (old expose routes untouched, old collect routes still present as a fallback since T009 adds the new route *alongside* the old one, not replacing it).

### Full Phase 2 (recommended, since US3 needs both US1 and US2 done)

1. T001 → US1 (T002-T010) → US2 (T011-T016) → US3 (T017-T021) → T022 polish.
2. Commit after each checkpoint. US3 is the only irreversible-feeling step (deletion) — but it's gated behind both equivalence proofs passing, and git history keeps it recoverable regardless.

## Notes

- Old and new routes coexist from T009/T015 through T016 — this is intentional, it's what makes US1/US2 "independently testable" without a live cutover risk; US3 is the only step that removes the old path.
- 22 tasks total. New: 2 plugin classes, 1 exception type, 1 new routing file, 2 new plugin test files. Modified: `GlobalExceptionHandler.kt`, `Routing.kt`, `DependencyInjection.kt`, `PluginRouteSet.kt`. Deleted (US3): 2 routing files, 1 facade class.
