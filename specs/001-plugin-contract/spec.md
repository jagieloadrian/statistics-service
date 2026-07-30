# Feature Specification: Plugin Contract (Phase 1)

**Feature Branch**: `001-plugin-contract`

**Created**: 2026-07-30

**Status**: Draft

**Input**: User description: "user-roadmap.md Phase 1 — Plugin Contract (no behavior change): introduce the StatPlugin interface, PluginRegistry, and generic Redis key namespacing per plugin, without moving any existing collect/expose logic yet. Existing epidemic/temperature endpoints must keep responding identically throughout."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Uniform plugin contract exists to build against (Priority: P1)

As the developer migrating epidemic/temperature onto plugins in Phase 2 (and adding
Home Assistant in Phase 3), I need one shared interface that defines what a "stats
plugin" is (an id, an optional collect step, an expose step), so future domains are
implemented against a stable contract instead of hand-wired per-domain code.

**Why this priority**: everything else in the roadmap (Phase 2 migration, Phase 3
Home Assistant, Phase 4 registration hygiene) is implemented *against* this
interface. Nothing downstream can start until it exists.

**Independent Test**: the interface compiles and has zero callers; verified by
building the project with the new interface present and unused.

**Acceptance Scenarios**:

1. **Given** the codebase before this change, **When** the `StatPlugin` contract
   (id, optional `collect(raw)`, `expose()`) is added, **Then** the project builds
   with the new interface unused by any existing code path.
2. **Given** a domain that is expose-only (e.g. a future pull-based source),
   **When** it implements the contract, **Then** it is not forced to implement a
   `collect()` it doesn't need (collect and expose are separable).

---

### User Story 2 - Plugins are resolvable by id through one registry (Priority: P2)

As the system routing an incoming request for a given data source, I need to look
up "the plugin for id X" through a single registry, so the generic collect/expose
routes introduced in Phase 2 have exactly one place to resolve a plugin.

**Why this priority**: depends on User Story 1 (the contract) existing first;
required before Phase 2's generic routes can be built, but is a smaller, later
step than defining the contract itself.

**Independent Test**: given a registry seeded with a known plugin id, looking it
up returns that plugin; looking up an unregistered id returns a clear,
404-mappable error — testable without any HTTP route wired up yet.

**Acceptance Scenarios**:

1. **Given** a registry built from an explicit list of plugins at startup,
   **When** a known plugin id is requested, **Then** the matching plugin instance
   is returned.
2. **Given** the same registry, **When** an unregistered plugin id is requested,
   **Then** a clear, distinguishable "unknown plugin" error is raised (not a
   generic null/crash).
3. **Given** the registry is built explicitly from a fixed list, **When** the
   application starts, **Then** no reflection- or classpath-scanning-based plugin
   discovery occurs.

---

### User Story 3 - Data location is derived the same way regardless of source (Priority: P3)

As the system persisting or reading stats for any plugin, I need one generic way
to build the storage key/location for a given plugin + device + run, so each new
plugin doesn't need its own hand-written key-building logic, and existing stored
data stays reachable under the exact same keys it already uses.

**Why this priority**: needed before Phase 2 can migrate epidemic/temperature
without a data migration, but is independent of and can be built/tested in
parallel with User Stories 1-2.

**Independent Test**: generate a key for an existing epidemic/temperature
device+run combination and confirm it matches the exact string produced by
today's domain-specific key builders — verifiable via existing storage tests
without touching collect/expose code.

**Acceptance Scenarios**:

1. **Given** a plugin id, device id, and (optional) run id, **When** the generic
   key builder is invoked, **Then** it returns a key string.
2. **Given** the same device/run identifiers the current epidemic and
   temperature key builders already produce keys for, **When** the generic
   builder is invoked with the equivalent plugin id, **Then** the resulting key
   string is byte-for-byte identical to what the existing builder produces today
   (no data migration required).

### Edge Cases

- What happens when the registry is queried for a plugin id before the registry
  has finished building at startup? (Must not silently return an empty/partial
  registry — fail startup instead.)
- What happens when a run id is omitted for a plugin/device combination that
  expects one? (Key builder must handle the optional-run-id case explicitly,
  matching existing device-only key behavior.)
- What happens if two entries in the explicit plugin list share the same id?
  (Must be caught, not silently let the second registration overwrite the
  first.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST define a `StatPlugin` contract exposing at minimum an
  id and an `expose()` capability, with `collect()` as a separately implementable
  capability so expose-only plugins are not forced to implement it.
- **FR-002**: System MUST provide a registry that resolves a plugin instance by
  its id.
- **FR-003**: System MUST build the registry from one explicit, compile-time list
  of plugin instances — no reflection- or classpath-scanning-based discovery.
- **FR-004**: System MUST raise a clear, distinguishable error when a lookup is
  made for a plugin id that is not registered.
- **FR-005**: System MUST reject (fail fast, not silently overwrite) duplicate
  plugin ids in the registration list.
- **FR-006**: System MUST provide one generic key-building function, parameterized
  by plugin id, device id, and optional run id, replacing the existing
  domain-specific key builders for epidemic and temperature.
- **FR-007**: The generic key builder MUST produce identical key strings to the
  existing epidemic/temperature key builders for the same device/run identifiers,
  so no data migration is required.
- **FR-008**: This phase MUST NOT change any observable behavior of the existing
  `/collect/epidemic`, `/collect/temperature`, or UI-facing expose endpoints —
  the new interface, registry, and key builder are introduced unused/adjacent to
  current code paths.
- **FR-009**: Existing epidemic/temperature contract tests (`.docs/*.md`) MUST
  continue to pass unchanged after this phase.

### Key Entities

- **StatPlugin**: the contract a data domain implements — carries an id, an
  optional collection behavior, and an exposition behavior.
- **PluginRegistry**: an id-to-`StatPlugin` lookup built once from an explicit
  list at startup.
- **Plugin storage key**: the generic identifier (plugin id + device id +
  optional run id) used to locate a plugin's persisted data, replacing today's
  per-domain key strings without changing their resulting values.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All Redis keys generated by the new generic key builder for
  existing epidemic/temperature device/run combinations are identical to keys
  produced by the current code, verified by the existing storage test suite
  passing unchanged.
- **SC-002**: All currently-passing tests for epidemic and temperature
  collect/expose endpoints continue to pass unchanged after this phase, with zero
  observable behavior difference to existing clients.
- **SC-003**: A plugin id lookup against the registry resolves correctly for
  every currently-known domain and returns a clear error for any unknown id, with
  zero ambiguous/null-crash outcomes in testing.
- **SC-004**: The `StatPlugin` interface and `PluginRegistry` exist and compile
  with zero production callers at the end of this phase (confirming this phase is
  purely additive, setting up Phase 2's migration).

## Assumptions

- This phase (Phase 1 in `user-roadmap.md`) is purely additive/refactor-prep:
  no existing `StatsCollectorService`/`StatsExposerFacade` code is deleted or
  rewired to use the new contract yet — that is Phase 2's scope.
- "Expose-only" plugins (Home Assistant, Phase 3) are anticipated but not
  implemented here; the contract only needs to support the split so Phase 3
  doesn't require reshaping the interface.
- The explicit plugin list mechanism (`Plugins.kt` naming, single-file hygiene)
  is Phase 4 scope; this phase only requires *an* explicit list, not the final
  single-file structure.
- No new dependency is introduced — registry and key builder are plain Kotlin,
  consistent with the project's local-first, minimal-ops constitution.
