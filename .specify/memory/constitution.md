<!--
Sync Impact Report
Version change: 1.3.0 → 2.0.0 (MAJOR — principles I, II, IV redefined; plugin-per-domain
  model removed)
Modified principles:
  - I. Plugin Contract First → I. Configuration Over Code
  - II. Contract-Compliant Ingestion & Exposition → II. Generic, Versioned Contracts
  - III. Test Coverage for Core Flows, Not Every Line — kept; scope wording moved from
    plugin collect/expose to ingestion/mapping/query/access paths
  - IV. Local-First, Minimal-Ops Simplicity — kept name; auth, user panel and roles
    moved from deferred to in scope; single organization extensible to multi-org
  - V. Storage & API Modularity — one record store; PostgreSQL direction; Redis =
    untouched archive; per-plugin key builder rule removed
Added sections: none
Removed sections: none (Technology & Architecture Constraints and Development
  Workflow rewritten for spec 004)
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ no change needed (generic Constitution Check)
  - .specify/templates/spec-template.md ✅ no change needed
  - .specify/templates/tasks-template.md ✅ no change needed
  - user-roadmap.md ✅ marked superseded by specs/004-universal-stats-platform
  - CLAUDE.md ⚠ pending — still points at specs/003 plan; refreshed by
    /speckit-agent-context-update after /speckit-plan for spec 004
Follow-up TODOs:
  - "i-have-adhd" still not installed — Agent Tooling rule unchanged.
  - agent-os/ and .copilot/ deletion still not performed (carried over).
-->

# StatisticsService Constitution

## Core Principles

### I. Configuration Over Code

A data domain (temperature, epidemic, Home Assistant, ...) MUST exist only as
configuration — source, mapping, dataset and dashboard definitions — never as
domain-specific types, routes, screens or URLs in the backend or the frontend
(spec 004 FR-018, SC-002). Adding a new data source MUST NOT require code
changes, rebuilds or redeploys of either side. Logic that declarative mapping
cannot express MUST live in a separate collector app (any language) that
feeds the platform over a supported channel (HTTP push, HTTP pull, Kafka,
gRPC) with a source credential. The platform MUST NOT load plugins or
extensions in-process (no ServiceLoader, no jar drop-in, no per-domain Gradle
modules).

**Rationale**: the plugin contract (specs 001–003) made routing generic but
left data model, aggregation and screens per domain, so every new source still
cost backend and frontend code. Out-of-process collectors give the same
extensibility with process isolation and zero coupling to platform internals.

### II. Generic, Versioned Contracts

The platform's published contracts are generic: the ingest envelope and
mapping model, the query API, the dimension-values API, and the definition
file format (sources, datasets, widgets, dashboards). They MUST be versioned
(URL path for APIs, explicit version field for definition files), documented
(OpenAPI + `.docs`), and MUST NOT break silently — a breaking change needs a
version bump and explicit call-out. Legacy device ingestion addresses and
payloads (`.docs/RPI_*_Contract.md`) MUST keep working unchanged, served as
ordinary source definitions. Per-domain UI read endpoints
(`/api/v1/stats/expose/**`, `.docs/*_UI_API_Contract.md`) MAY be removed once
the generic frontend replaces the screens that use them.

**Rationale**: device firmware cannot be redeployed in sync with the platform,
so device ingestion is the one legacy contract that must survive; the
frontend is owned by the same person and is the only consumer of the old read
endpoints, so keeping them adds cost without protecting anyone.

### III. Test Coverage for Core Flows, Not Every Line

Ingestion (per channel), mapping, record storage, query/aggregation and
access control MUST have test coverage before a feature is done: unit tests
for mapping and aggregation rules, integration tests for endpoints, contract
tests for published shapes (including the legacy device payloads). Tests
target core user/device flows and contract shapes — MUST NOT be written for
every intermediate step, and non-critical edge cases MAY be deferred to a
follow-up rather than blocking the feature. Tests assert behavior, not
implementation detail.

**Rationale**: the generic model concentrates all risk in a few shared paths
(mapping, query, access); covering those well protects every domain at once
without over-testing what isn't load-bearing.

### IV. Local-First, Minimal-Ops Simplicity

Default to self-hosted, low-friction deployment (Docker Compose) at home-lab
scale. In scope: platform-managed accounts, sign-in, roles admin / editor /
viewer, dashboard sharing, user preferences. v1 serves exactly one
organization, but every owned definition and user MUST belong to it
explicitly so multiple organizations can be added later without changing
existing definitions, exports or contracts. Do not build single sign-on,
multi-organization, alerting, push-to-browser streaming, data export,
k8s/multi-host orchestration or analytics-store migrations until a concrete,
confirmed need exists. A new ingestion channel (pull, Kafka, gRPC) is built
only when a real producer for it exists. Follow YAGNI: no abstraction beyond
what the generic source → dataset → query → widget model itself needs.

**Rationale**: a user panel is now an explicit requirement (spec 004 US4), so
auth leaves the backlog; everything else stays deferred because building ahead
of a trigger wastes effort on a single-owner, home-lab product.

### V. Storage & API Modularity

All records MUST go through one record store abstraction (write records,
query by dataset/time/dimensions/bucket, list dimension values) — no
per-domain storage code or keys. The storage backend MUST remain swappable
without breaking published contracts; direction is PostgreSQL for both
definitions and records (confirmed in `/speckit-plan`), with a time-series
extension only when volume requires it. The existing Redis data MUST be left
untouched as a read-only archive: not migrated, not deleted, not read by the
new platform.

**Rationale**: generic queries need server-side aggregation over arbitrary
fields and the user panel needs relational data; one store keeps operations
minimal, and the abstraction keeps the later upgrade path open.

## Technology & Architecture Constraints

- Backend: Kotlin + Ktor, Gradle Kotlin DSL — single application module;
  `/plugin/*` modules, `StatsRepositoryRedisImpl` and Redis wiring are
  removed at cutover (spec 004 research §3).
- Frontend: separate repo `Statistics-UI`, Kotlin Multiplatform Compose (web
  + desktop); only generic widget renderers, dashboard viewer and definition
  editors — no domain DTOs, clients or screens. Talks to the backend only
  through published contracts.
- Storage: one record store (PostgreSQL direction) for definitions, users and
  records; Redis kept only as untouched archive.
- Contracts: JSON over HTTP; generic APIs versioned under `/api/v2/...`;
  legacy `POST /api/v1/stats/collect/{temperature|epidemic}` kept as aliases
  of their source definitions. RESTful, plural nouns, nesting ≤ 3 levels,
  filtering/sorting/pagination via query params or query body, not new
  endpoints.
- Ingestion: HTTP push is the baseline channel; HTTP pull, Kafka and gRPC use
  the same mapping model and are added only when a producer exists. A record
  is acknowledged only after it is durably stored.
- Validation: declarative, per source mapping, always server-side; never
  trust device/client input. Every source has its own rotatable credential
  unless an admin explicitly marks it open for trusted local devices.
- Local dev/deploy: Docker Compose; no k8s until a real multi-host need.
- Testing: JUnit + Kotest assertions, Testcontainers for storage-backed
  integration tests.
- CI: GitHub Actions for build and test.

## Development Workflow

- New or changed contracts (APIs, ingest envelope, definition format) MUST
  update OpenAPI/`.docs` in the same change.
- Cutover work MUST keep legacy device ingestion responding identically
  (contract-tested against `.docs/RPI_*_Contract.md`) at every step.
- Tests MUST accompany changes to ingestion, mapping, record store, query or
  access control; such a change without test updates fails review — scoped
  to core flows per Principle III.
- Product direction lives in `specs/004-universal-stats-platform/spec.md`
  and later specs; `user-roadmap.md` is superseded and frozen. `agent-os/` and
  `.copilot/` are legacy/frozen and MUST NOT be treated as a source of truth.
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

**Version**: 2.0.0 | **Ratified**: 2026-07-30 | **Last Amended**: 2026-10-27
