# Feature Specification: Extract Plugins into Separate `/plugin` Packages (Phase 2.5)

**Feature Branch**: `003-plugin-modules`

**Created**: 2026-07-30

**Status**: Draft

**Input**: User description: "user-roadmap.md Phase 2.5 — Extract plugins into `/plugin` Gradle modules: EpidemicPlugin and TemperaturePlugin currently live as classes inside core's src, wired by editing core's DependencyInjection.kt directly. Move them into their own Gradle subprojects under /plugin, depending on a shared plugin-api module (StatPlugin, PluginRouteSet, the Redis key builder) that core also depends on, so core stops importing plugin classes by name. Also add a home_assistant_api stub module (empty StatPlugin, no real logic — the real Home Assistant integration is a separate project) to prove a third module wires in cleanly. Pure refactor — epidemic/temperature endpoints must keep responding identically."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Existing epidemic/temperature clients see no change (Priority: P1)

As a device or client currently posting to `/api/v1/stats/collect/{epidemic,temperature}`
or reading from `/api/v1/stats/expose/{epidemic,temperature}/**`, I need those endpoints
to keep behaving identically after the underlying plugin code moves out of the core
module into its own package — this is a pure internal restructuring, not a contract
change.

**Why this priority**: this is the safety net for the whole phase. If moving code
into separate packages breaks an existing endpoint, the packaging goal isn't worth it.

**Independent Test**: run the existing collect/expose test suite unchanged against the
service after the move; every test must still pass with identical request/response
behavior and identical stored data.

**Acceptance Scenarios**:

1. **Given** a valid epidemic payload, **When** it is submitted to the epidemic collect
   endpoint after the plugin code has moved to its own package, **Then** response and
   stored data are identical to before the move.
2. **Given** a valid temperature payload, **When** it is submitted to the temperature
   collect endpoint after the move, **Then** response and stored data are identical to
   before the move.
3. **Given** an existing expose request (run list, run summary, temperature series,
   temperature summary), **When** it is issued after the move, **Then** the response
   body and shape are byte-for-byte identical to before the move.

---

### User Story 2 - A new data-source plugin ships as its own package (Priority: P2)

As the person building this service, I need to add a new data-source plugin as a
self-contained package that only depends on the shared plugin contract — not on the
core service's internals — so that adding a source doesn't require touching or
recompiling unrelated core code, and a plugin can eventually be built/shipped on its
own schedule.

**Why this priority**: this is the actual point of Phase 2.5 — without it, "plugin"
is just an interface name inside one big module, not something addable as a separate
build artifact.

**Independent Test**: build each plugin package in isolation (its own build step) and
confirm it only requires the shared plugin-contract package, never the core service's
internal classes.

**Acceptance Scenarios**:

1. **Given** the epidemic plugin's package, **When** it is built on its own, **Then**
   the build succeeds using only the shared plugin-contract package as a dependency
   (no dependency on core's internal classes).
2. **Given** the temperature plugin's package, **When** it is built on its own,
   **Then** the same holds.
3. **Given** the core service, **When** its source is inspected, **Then** it
   references concrete plugin implementations only in the single place that
   registers them for startup — nowhere else in core names a specific plugin.

---

### User Story 3 - A stub third-party-style plugin proves the mechanism (Priority: P3)

As the person building this service, I need a placeholder plugin package (standing in
for the real, separately-developed Home Assistant integration) that does nothing but
register itself, so the packaging mechanism is proven with a third plugin before the
real one is built elsewhere.

**Why this priority**: cheapest possible proof that the mechanism scales past two
plugins, without waiting on the separate Home Assistant project.

**Independent Test**: register the stub plugin and confirm the service starts, lists
it as an available plugin, and its expose route responds (with a placeholder/empty
result) without any change to epidemic or temperature behavior.

**Acceptance Scenarios**:

1. **Given** the stub plugin package is registered, **When** the service starts,
   **Then** startup succeeds and the plugin is resolvable by its id.
2. **Given** the stub plugin is registered, **When** its expose endpoint is called,
   **Then** it returns a placeholder response without error, and epidemic/temperature
   endpoints are unaffected.

---

### Edge Cases

- What happens when a plugin package accidentally references a core-internal class
  that isn't part of the shared plugin-contract package? Build must fail loudly
  before startup, not at request time.
- What happens when two plugin packages declare the same plugin id? Startup must
  fail with a clear error, same as today's in-process duplicate-id check.
- What happens if the stub plugin's package is removed from the build entirely?
  Service must still start and serve epidemic/temperature normally — the stub must
  not become a hard dependency of core.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The shared plugin contract (plugin interface, route-registration type,
  and the per-plugin storage-key builder) MUST live in a package that has no
  dependency on the core service.
- **FR-002**: The epidemic plugin's implementation MUST live in its own package,
  depending only on the shared plugin-contract package.
- **FR-003**: The temperature plugin's implementation MUST live in its own package,
  depending only on the shared plugin-contract package.
- **FR-004**: The core service MUST NOT reference a specific plugin implementation
  type anywhere except the single startup registration list.
- **FR-005**: A stub plugin package MUST exist, implementing the shared contract with
  a fixed id and no real data logic, to prove a third package registers cleanly.
- **FR-006**: Existing epidemic and temperature collect/expose endpoints MUST respond
  identically (status, body shape, stored data) before and after the packages are
  separated.
- **FR-007**: The build MUST be able to build each plugin package independently of
  the others (an epidemic-only build does not require compiling the temperature
  package or vice versa).
- **FR-008**: Startup MUST fail with a clear error if two registered plugin packages
  declare the same plugin id (unchanged from current behavior).
- **FR-009**: Removing the stub plugin package from the build MUST NOT affect
  epidemic or temperature behavior.

### Key Entities

- **Plugin contract package**: the shared interface, route-registration type, and
  storage-key builder every plugin package depends on; owned by neither a specific
  plugin nor core's internals.
- **Plugin package**: one per data source (epidemic, temperature, and the
  Home-Assistant stub), each an independently buildable unit implementing the
  contract.
- **Core service**: the host application — owns generic routing, storage, and the
  startup list that wires concrete plugin packages together; no longer contains
  plugin-specific logic.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of existing epidemic/temperature collect and expose tests pass
  unchanged after the package split.
- **SC-002**: Each of the three plugin packages (epidemic, temperature, stub) builds
  successfully as an independent build unit.
- **SC-003**: Zero references to concrete plugin implementation types exist in core
  service code outside the single startup registration point.
- **SC-004**: A new stub-style plugin package can be registered and confirmed working
  (service starts, plugin resolvable, placeholder expose response) without editing
  any epidemic or temperature code.

## Assumptions

- "Package" means a Gradle subproject (build-time separation), not a dynamically
  loaded jar/plugin-marketplace mechanism — runtime drop-in loading is explicitly out
  of scope for this phase (per `user-roadmap.md`, deferred until a concrete need for
  shipping a plugin without a core rebuild appears).
- The Home Assistant stub package has no real polling/integration logic — the actual
  Home Assistant integration is being built as a separate project and will replace
  the stub later (Phase 3), not as part of this feature.
- No data migration is needed — Redis key formats and stream layouts are unchanged by
  the package move (only source-code location changes).
- The existing `.docs/*_Contract.md` files and the Statistics-UI consumer's expected
  URLs remain the acceptance bar for "identical behavior" — no UI-facing contract
  changes are introduced by this feature.
