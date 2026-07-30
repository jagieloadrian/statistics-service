# Phase 0 Research: Extract Plugins into Separate `/plugin` Packages

No `[NEEDS CLARIFICATION]` markers in the spec — this documents the design
decisions needed to turn the spec's requirements into a concrete module
layout, since the spec deliberately stays implementation-agnostic ("package"
not "Gradle subproject").

## Decision: build-time Gradle multi-module, not runtime/dynamic loading

**Decision**: Each plugin is a Gradle subproject under `/plugin`, compiled
and linked into the core application at build time via `project(":plugin:...")`
dependencies declared in core's `build.gradle.kts`.

**Rationale**: matches the spec's Assumptions section and constitution
Principle IV (no loader machinery until a concrete out-of-process need
exists) and `user-roadmap.md`'s explicit "still a compile-time, in-process
Gradle multi-module setup" note for this phase.

**Alternatives considered**: ServiceLoader/classpath-jar-drop-in (rejected —
no current need for shipping a plugin without a core rebuild, adds a loader
+ classpath-isolation problem this phase doesn't need to solve); a Gradle
composite build with fully separate builds per plugin (rejected — subprojects
of one build already give independent compilation units without also
splitting version catalogs/build config per plugin).

## Decision: `plugin-api` module contents

**Decision**: `:plugin:plugin-api` carries exactly: `StatPlugin`,
`PluginRouteSet`, the `StatsRepository` interface, and `ApplicationConstants`
trimmed to `API_BASE_PATH` + the generic `getKey(pluginId, deviceId, runId?)`
builder, plus the 3 field names in `DbKeyConstants` already commented
`//common` (`DEVICE_ID_KEY`, `RUN_ID_KEY`, `TIMESTAMP_KEY`).

**Rationale**: these are exactly the types every plugin and core both need
and that carry zero domain-specific logic today — `DbKeyConstants` already
separates "common" from "epidemic"/"temperatures" fields in comments, so the
split point already exists in the code, it's just not enforced by module
boundaries yet.

**Alternatives considered**: putting `StatsRepositoryRedisImpl` in
`plugin-api` too (rejected — it's a concrete Redis adapter, not a shared
contract; keeping it in core matches Principle V, storage stays swappable
behind the interface); keeping `ApplicationConstants`/`DbKeyConstants`
un-split as shared classes both domains keep using (rejected — that's the
exact "leaks domain logic into shared infrastructure" problem Principle I
calls out; `getEpidemicKey`/`getTemperatureKey` and the domain-only field
names are plugin-owned data, not contract).

## Decision: retire `StatsCollectorService`, split by domain

**Decision**: `StatsCollectorService`'s two public methods
(`saveEpidemicStats`, `saveTemperatureStats`) and their private helpers
split into `EpidemicStatsCollectorService` (in `rpi_epidemic_api`) and
`TemperatureStatsCollectorService` (in `rpi_temperature_api`), each taking a
`StatsRepository` directly. The class itself is deleted from core.

**Rationale**: `StatsCollectorService` already has zero shared logic between
its two methods beyond calling the same private `saveStatistics` helper —
splitting it is a mechanical extraction, not a rewrite. Keeping it as one
class would force either a new shared module beyond `plugin-api` (over-
engineering for a private one-line helper) or leaving genuinely
domain-specific save logic sitting in core (violates FR-004).

**Alternatives considered**: keep `StatsCollectorService` in core, have both
plugins depend on core (rejected — directly violates FR-001/FR-004, the
entire point of this phase); move it to `plugin-api` unsplit (rejected — it
contains epidemic- and temperature-specific field mapping, not contract).

## Decision: package names stay `com.anjo.statisticservice.*` across modules

**Decision**: moved files keep their existing package declarations; only
their Gradle module (and therefore their compiled JAR/classpath entry)
changes.

**Rationale**: minimizes the diff to file moves + import path fixes where a
type's *module* changed — no renaming class names or import statements for
same-module references, keeping this a pure-relocation refactor per FR-006's
zero-behavior-change requirement. Kotlin doesn't require a 1:1 package/module
mapping, so there's no correctness reason to rename.

**Alternatives considered**: per-module package prefixes (e.g.
`com.anjo.statisticservice.plugin.epidemic`) — rejected, pure churn with no
functional benefit for a two-plugin (soon three) codebase; revisit only if
package-name collisions actually happen.

## Decision: stub module minimal shape

**Decision**: `HomeAssistantPlugin` in `:plugin:home_assistant_api` has
`id = "home-assistant"`, no `collect()` override (uses `StatPlugin`'s default
no-op), and `expose()` returning a `PluginRouteSet` with one `GET "/"` route
responding `200 OK` with an empty JSON array/object.

**Rationale**: cheapest possible proof (User Story 3) that a third module
registers and resolves without any real logic — matches `user-roadmap.md`'s
"wydmuszka" framing (empty shell, real work is a separate project).

**Alternatives considered**: no routes at all (rejected — `expose()` is
non-optional in `StatPlugin`, and an empty `PluginRouteSet {}` with zero
registered routes makes the "expose responds" acceptance scenario
untestable); building any real HA polling logic now (out of scope, that's
Phase 3 and a separate project per the spec's Assumptions).
