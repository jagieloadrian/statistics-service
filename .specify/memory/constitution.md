<!--
Sync Impact Report
Version change: 1.2.0 → 1.3.0
Modified principles: I. Plugin Contract First — expanded to require plugins be
  separate Gradle modules under `/plugin` (each depending only on
  `:plugin-api`, never on core-by-name), not classes living inside core `src`;
  reflects user-roadmap.md's new "Plugins are packages, not classes in `src`"
  section and Phase 2.5.
Added sections: none new (existing principle expanded, existing Tech
  Constraints bullet on Gradle builds expanded to name the multi-module layout)
Removed sections: none
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ no change needed
  - .specify/templates/spec-template.md ✅ no change needed
  - .specify/templates/tasks-template.md ✅ no change needed
Follow-up TODOs:
  - "i-have-adhd" still not present in this session's available skill/plugin
    list — Agent Tooling rule unchanged, still MUST-when-available.
  - agent-os/ and .copilot/ deletion still not performed (destructive, out of
    this skill's scope) — carried over from prior amendment.
-->

# StatisticsService Constitution

## Core Principles

### I. Plugin Contract First
Every data domain (epidemic, temperature, Home Assistant, ...) MUST implement
collection and exposition behind the single shared `StatPlugin` contract, and
MUST be registered explicitly (no dynamic/jar-based loading — Gradle
multi-module only, per `user-roadmap.md` "Plugins are packages, not classes in
`src`"). Each plugin MUST live in its own Gradle subproject under `/plugin`
(e.g. `:plugin:rpi_temperature_api`), depending only on the shared
`:plugin-api` module (`StatPlugin`, `PluginRouteSet`, the key builder) — never
on core, and core MUST NOT import plugin classes by name outside the single
registration list. Core storage (`StatsRepository`, Redis Streams) MUST stay
domain-agnostic — plugin-specific logic never leaks into shared
infrastructure. Adding a new data source MUST require only a new plugin
module plus a registration line (see "Adding a new plugin" mechanism in
`user-roadmap.md`), not new hand-wired routes or services.

**Rationale**: the product's differentiator is that new sources are cheap to
add and buildable independently; hardcoded per-domain services (the
pre-plugin state migrated away from in `user-roadmap.md` Phase 2) and
plugin classes hardcoded inside core `src` (the state migrated away from in
Phase 2.5) both re-accumulate the coupling the plugin architecture exists to
remove.

### II. Contract-Compliant Ingestion & Exposition
Ingestion and UI-facing API payloads MUST match the versioned contracts in
`.docs`. Breaking a documented contract MUST NOT happen silently — it
requires a version bump and explicit call-out, since device firmware and UI
clients evolve independently of the backend and rely on those contracts to
decouple safely. Every phase MUST leave epidemic/temperature endpoints
responding identically throughout any migration (contract-tested against
`.docs/*.md`, per `user-roadmap.md` Ground Rules).

**Rationale**: hobbyist devices and separate UI clients can't coordinate
deploys with the backend; the documented contract is the only sync point
between them.

### III. Test Coverage for Core Flows, Not Every Line
A plugin's collect/expose paths and Redis Streams indexing MUST have test
coverage equivalent to existing plugins (unit for services/validation,
integration for endpoints, contract tests for `.docs` shapes) before it is
considered done. Tests target core user/device flows and contract shapes —
MUST NOT be written for every intermediate step during development, and
non-critical edge cases/error states MAY be deferred to a dedicated
follow-up rather than blocking the feature. Tests assert behavior (what the
endpoint/plugin does), not implementation detail.

**Rationale**: the repo already tests ingestion, storage indexing, and
UI-facing endpoints (see `src/test/kotlin/...RoutesTest.kt`,
`StatsCollectorServiceTest.kt`); the plugin migration must preserve that bar
without over-testing coverage that isn't load-bearing.

### IV. Local-First, Minimal-Ops Simplicity
Default to local, low-friction deployment (Docker Compose, Redis) over
speculative infrastructure. Do not build multi-tenant, SSO/RBAC, k8s
orchestration, or analytics-store migrations (ClickHouse/PostgreSQL), auth,
or rate limiting until a concrete, confirmed need exists — these are
explicitly the Backlog in `user-roadmap.md`, not phased/planned work. Follow
YAGNI: minimal implementations, no premature abstractions beyond the plugin
contract itself (e.g. no loader/marketplace machinery for out-of-process
plugins until 3rd-party plugins are a real need).

**Rationale**: `user-roadmap.md` explicitly separates the phased plugin-
platform roadmap (Phases 1-5) from its Backlog (auth, rate limiting,
exports, SSO/RBAC, k8s) — building ahead of a concrete trigger wastes work
and adds unneeded complexity for the target hobbyist/local-network user.

### V. Storage & API Modularity
Storage backends (Redis today; PostgreSQL/ClickHouse as future options) and
transport (HTTP REST, optional WebSocket) MUST remain swappable without
breaking published contracts. Redis key/stream structures MUST be built
through the shared repository layer, keyed per plugin id via one generic key
builder (`ApplicationConstants.getKey(pluginId, deviceId, runId?)` per
`user-roadmap.md` F1.3), not duplicated per-domain.

**Rationale**: `user-roadmap.md` explicitly reserves the option to migrate
storage without breaking clients; duplicated per-domain storage code blocks
that migration path.

## Technology & Architecture Constraints

- Backend: Kotlin + Ktor (HTTP + WebSocket server), Gradle Kotlin DSL builds —
  multi-module: core (`src`) plus one Gradle subproject per plugin under
  `/plugin` (`:plugin:plugin-api`, `:plugin:rpi_temperature_api`,
  `:plugin:rpi_epidemic_api`, `:plugin:home_assistant_api`), per
  `user-roadmap.md` Phase 2.5.
- Storage: Redis (Streams) for ingestion/time-series storage — domain-agnostic
  (`StatsRepository`), reused unchanged by every plugin; PostgreSQL/ClickHouse
  reserved for future analytics needs, not current scope.
- Contracts: JSON over HTTP; DTOs documented as contracts in `.docs/`;
  WebSocket only for optional live feeds (Backlog, no current driver).
- API shape: RESTful, resource-based, plural-noun, versioned via URL path
  (already `/api/v1/...`); generic plugin routes per `user-roadmap.md`
  Phase 2 — `POST /api/v1/stats/collect/{pluginId}`,
  `GET /api/v1/stats/expose/{pluginId}/**` — replace hardcoded per-domain
  routes; nesting depth capped at 2-3 levels; filtering/sorting/pagination
  via query params, not new endpoints.
- Data layer: no relational schema/migrations (Redis, not SQL) — discipline
  instead applies to key/stream design: one generic key builder per plugin
  id (not per-domain builders), stable key strings across refactors so no
  data migration is ever needed, index only fields actually queried
  (set/sorted-set indices per run/device).
- Validation: plugin-owned — each plugin validates its own payload inside
  `collect()` (per `user-roadmap.md` "Adding a new plugin" step 2), not a
  shared DTO-keyed validation block; server-side always, never trust
  device/client input.
- Local dev/deploy: Docker Compose for Redis + RedisInsight
  (`.devops/redis-compose`); backend runs directly, no compose/k8s service
  for it yet; Minikube/k8s out of scope until a real multi-host need appears.
- Testing: JUnit unit + integration tests against the Redis contract.
- CI: GitHub Actions for build and test.

## Development Workflow

- New or changed endpoints MUST update `.docs` contracts in the same change.
- Plugin migrations (collapsing hardcoded per-domain services into
  `StatPlugin` implementations, or extracting plugin classes out of core
  `src` into their own Gradle module) MUST NOT change observable behavior
  unless explicitly scoped to do so — see `user-roadmap.md` Ground Rules.
- Tests MUST accompany contract or storage-indexing changes; a change that
  touches `StatsRepository`, collect/expose routes, or plugin registration
  without corresponding test updates fails review — scoped to core flows
  per Principle III, not exhaustive edge cases.
- Product direction, phase status, and backlog live solely in
  `user-roadmap.md`. `agent-os/` and `.copilot/` are legacy/frozen and MUST
  NOT be treated as a source of truth once removed from the repo.
- **Agent tooling**: every Claude Code skill invocation on this project MUST
  run with the `ponytail` and `caveman` skills active (lazy/minimal-diff
  implementation, terse output), plus `i-have-adhd` when that plugin is
  installed in the working environment. If a named skill isn't installed,
  proceed without it and say so rather than blocking the task.

## Governance

This constitution supersedes ad-hoc practice for this repository. Amendments
require: (1) a documented rationale for the change, (2) a version bump per
semantic versioning (MAJOR for incompatible principle removal/redefinition,
MINOR for new principles or materially expanded guidance, PATCH for wording/
clarification), and (3) propagation check against
`.specify/templates/plan-template.md`, `spec-template.md`, and
`tasks-template.md` for now-stale references. Reviews (plans, PRs) MUST
verify compliance with Core Principles; deviations MUST be justified in the
plan's Complexity Tracking section or rejected.

**Version**: 1.3.0 | **Ratified**: 2026-07-30 | **Last Amended**: 2026-07-30
