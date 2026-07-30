# Feature Specification: Migrate Epidemic & Temperature onto the Plugin Contract (Phase 2)

**Feature Branch**: `002-migrate-plugins`

**Created**: 2026-07-30

**Status**: Draft

**Input**: User description: "user-roadmap.md Phase 2 — Migrate Epidemic & Temperature onto the contract: wrap the existing epidemic/temperature collect+expose logic behind EpidemicPlugin/TemperaturePlugin (the StatPlugin contract from Phase 1), introduce generic POST /api/v1/stats/collect/{pluginId} and GET /api/v1/stats/expose/{pluginId}/** routes replacing the two hardcoded route sets, then delete the now-dead hardcoded service/routing code once the existing test suite passes unchanged. Pure refactor — no new externally-visible behavior."

## Clarifications

### Session 2026-07-30

- Q: How does a plugin define its own expose sub-routes, given epidemic and temperature have structurally different sub-path shapes today (and there's a longer-term wish for fully generic objects/endpoints where plugins only supply the data source)? → A: Freeform for this phase — `PluginRouteSet` lets each plugin register its own arbitrary sub-routes under the generic prefix, preserving today's exact URLs/response shapes (FR-010 stays intact). Genericizing objects/endpoints so plugins only supply a data source is explicitly deferred to a later phase, not built here.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Epidemic and temperature data flow through the same generic route (Priority: P1)

As a device or client currently posting to `/api/v1/stats/collect/epidemic` or
`/api/v1/stats/collect/temperature`, I need those exact endpoints to keep behaving
identically after this change, even though internally they now both go through
one generic `POST /api/v1/stats/collect/{pluginId}` route dispatching to the
matching plugin instead of two hand-written routes.

**Why this priority**: this is the entire point of the migration — proving the
Phase 1 contract actually replaces hardcoded per-domain code without breaking
anyone talking to the service today. Nothing else in this phase matters if this
breaks.

**Independent Test**: post the exact same payloads used by today's existing
collect tests to the new route path and confirm identical response status/body
and identical downstream stored data (same Redis keys, same validation errors
for invalid payloads).

**Acceptance Scenarios**:

1. **Given** a valid epidemic payload, **When** it is submitted to the epidemic
   collect endpoint, **Then** the response and the resulting stored data are
   identical to what today's hardcoded route produces.
2. **Given** an invalid epidemic payload (fails today's validation rules),
   **When** it is submitted, **Then** the same validation error response is
   returned as today (status code and reasons list).
3. **Given** a valid temperature payload, **When** it is submitted to the
   temperature collect endpoint, **Then** the response and stored data are
   identical to today's hardcoded route.
4. **Given** an invalid temperature payload, **When** it is submitted, **Then**
   the same validation error response is returned as today.

---

### User Story 2 - UI-facing reads keep working through the same generic route (Priority: P1)

As the UI/client reading run lists, run timelines, run summaries, device lists,
temperature series, and temperature summaries, I need every one of those existing
read endpoints to keep returning identical data after this change, even though
they now route through one generic expose dispatcher instead of the current
per-domain facade methods.

**Why this priority**: equally critical to User Story 1 — read paths are the
other half of "the app stays fully working throughout the migration" (Ground
Rules in `user-roadmap.md`). Sized as P1 alongside collect since both must hold
for the phase to be considered safe to ship.

**Independent Test**: call each existing expose endpoint (runs list, run
timeline, run summary, temperature device list, temperature series, temperature
summary) before and after the change with the same inputs and diff the
responses — must be byte-identical.

**Acceptance Scenarios**:

1. **Given** existing epidemic run data, **When** the run list, a specific run's
   timeline, and that run's summary are requested, **Then** each response is
   identical to today's output.
2. **Given** existing temperature data, **When** the device list, a device's
   series (with resolution), and that device's summary are requested, **Then**
   each response is identical to today's output.
3. **Given** a request for an unsupported/unknown data source id, **When** it
   hits the generic expose route, **Then** a clear, distinguishable "unknown
   source" error is returned (not a silent empty response or crash).

---

### User Story 3 - Dead hardcoded code is removed once the migration is proven safe (Priority: P2)

As the developer maintaining this codebase going forward, I need the old
hardcoded per-domain collect/expose service methods and routing files removed
once the generic routes are proven equivalent, so the plugin contract becomes
the *only* way new and existing data sources are wired in — no leftover parallel
implementation to keep in sync.

**Why this priority**: depends on User Stories 1 and 2 both being proven safe
first (their passing tests are the gate); it's cleanup that only makes sense
after the replacement is trusted, not a blocker to shipping the migration itself.

**Independent Test**: after removal, the project still builds, and the full
existing test suite (now exercising the generic routes) still passes with zero
references left to the deleted hardcoded methods/files.

**Acceptance Scenarios**:

1. **Given** the generic collect/expose routes pass the full existing test
   suite unchanged, **When** the old hardcoded per-domain service methods and
   the two old routing files are deleted, **Then** the project still builds and
   the same test suite still passes.
2. **Given** the deletion is complete, **When** the codebase is searched for the
   deleted methods/files, **Then** there are zero remaining references outside
   version control history.

### Edge Cases

- What happens when a plugin's `collect()` validation rejects a payload — does
  the error format/status match what each domain's validation produces today?
  (Must match exactly — validation logic moves into the plugin, not its output.)
- What happens if the generic collect/expose route is hit with a `pluginId` that
  isn't registered (e.g. a typo, or a not-yet-implemented source)? (Must return
  the same clear "unknown plugin" error established in Phase 1, not a 500.)
- What happens to in-flight requests to the old hardcoded routes during the
  cutover? (Out of scope — this is a code change, not a live traffic migration;
  no rolling-deploy/dual-route period is required.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide an `EpidemicPlugin` implementing the Phase 1
  `StatPlugin` contract, wrapping today's epidemic collect and expose behavior
  without changing its inputs, outputs, or validation rules.
- **FR-002**: System MUST provide a `TemperaturePlugin` implementing the same
  contract, wrapping today's temperature collect and expose behavior unchanged.
- **FR-003**: System MUST expose one generic collect endpoint,
  `POST /api/v1/stats/collect/{pluginId}`, replacing the two hardcoded
  `/collect/epidemic` and `/collect/temperature` endpoints, dispatching to the
  matching plugin's collect behavior via the Phase 1 registry.
- **FR-004**: System MUST expose one generic family of expose endpoints,
  `GET /api/v1/stats/expose/{pluginId}/**`, replacing today's hardcoded
  per-domain read endpoints, dispatching to the matching plugin's expose
  behavior via the Phase 1 registry. Each plugin MUST be free to define its
  own sub-route shape and response objects under its `{pluginId}` prefix
  (freeform, per Clarifications) — this phase does not impose a shared
  generic response object or fixed operation set across plugins; that
  genericization is explicitly out of scope here (see Assumptions).
- **FR-005**: Payload validation currently performed centrally (epidemic and
  temperature DTO validation) MUST move into each plugin's own `collect()`
  implementation, producing the exact same validation results (same fields
  checked, same error reasons, same status code) as today.
- **FR-006**: The generic collect/expose routes MUST return the same clear,
  distinguishable "unknown plugin" error (established in Phase 1's registry)
  when hit with an unregistered `pluginId`.
- **FR-007**: All currently-passing epidemic and temperature contract tests
  (`.docs/*.md`) and existing endpoint tests MUST continue to pass, unchanged in
  intent, once traffic is routed through the new generic endpoints.
- **FR-008**: Once the generic routes are proven equivalent (FR-007 passes),
  the old hardcoded per-domain service methods (`StatsCollectorService`'s
  domain-specific save methods, `StatsExposerFacade`'s domain-specific read
  methods) and the two old routing files MUST be deleted.
- **FR-009**: After the deletion in FR-008, the system MUST build successfully
  and the full test suite MUST still pass, with zero remaining references to
  the deleted code.
- **FR-010**: This phase MUST NOT introduce any new externally-visible
  behavior, field, or endpoint beyond replacing the collect/expose URL shape
  from per-domain paths to the generic `{pluginId}` path — no new features.

### Key Entities

- **EpidemicPlugin**: the `StatPlugin` implementation wrapping epidemic
  collect (run/generation payloads) and expose (run list, timeline, summary)
  behavior, replacing today's hardcoded `StatsCollectorService` +
  `EpidemicStatsExposerService` domain methods.
- **TemperaturePlugin**: the `StatPlugin` implementation wrapping temperature
  collect (device samples) and expose (device list, series, summary) behavior,
  replacing today's hardcoded equivalents.
- **Generic collect/expose route**: the single dispatch point
  (`{pluginId}`-parameterized) that resolves a plugin via the Phase 1
  `PluginRegistry` and delegates to it, replacing the two hardcoded route sets.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of existing epidemic and temperature collect/expose
  requests produce identical responses (status, body, stored data) through the
  new generic routes as they did through the old hardcoded routes.
- **SC-002**: 100% of pre-existing passing tests continue to pass unchanged
  after both the route migration (FR-003/FR-004) and the dead-code deletion
  (FR-008), with zero regressions introduced at either step.
- **SC-003**: Zero references to the deleted hardcoded per-domain methods or
  routing files remain in the codebase after this phase completes.
- **SC-004**: A request to the generic collect or expose route with an
  unregistered plugin id returns a clear, distinguishable error in 100% of
  cases — zero silent failures or unhandled crashes.

## Assumptions

- This phase (Phase 2 in `user-roadmap.md`) builds directly on Phase 1's
  `StatPlugin`/`PluginRegistry`/generic key builder, already implemented and
  merged (`specs/001-plugin-contract`) — this spec does not re-derive those,
  it consumes them.
- "Identical behavior" means identical from the perspective of an external
  caller (status code, response body shape/values, stored Redis data) — not
  identical internal code structure; the internals are explicitly refactored.
- The cutover from old hardcoded routes to new generic routes happens in a
  single deploy, not a phased/dual-running rollout — per Edge Cases, in-flight
  request handling during a live cutover is out of scope for this spec.
- Deletion of dead code (FR-008/FR-009, User Story 3) happens only after the
  new routes are proven equivalent via the existing test suite — it is not a
  separate, independently-shippable increment ahead of that proof.
- No new plugin (e.g. Home Assistant) is introduced in this phase — that is
  Phase 3, out of scope here.
- Fully generic expose objects/endpoints (where a plugin supplies only its
  data source and the platform provides shared default objects/endpoints) is
  a desired future direction, but explicitly deferred to a later phase — not
  part of this spec, per Clarifications. Building it now would reshape
  epidemic's and temperature's today-divergent response shapes and violate
  FR-010 (zero externally-visible behavior change) and the roadmap's Phase 2
  "pure refactor" ground rule.
