<!--
Sync Impact Report
Version change: 2.3.0 → 2.4.0 (MINOR, 2026-10-28) — the homelab platform contract
  (homelab repo, "Homelab Platform Contract — wymagania dla aplikacji") is the
  deployment target: Jib multi-arch image pushed by CI, kustomize manifests with
  HTTPRoute on home-gateway + AuthorizationPolicy, Flux image automation, secrets
  SOPS-encrypted in the homelab repo. Replaces the v2.3.0 Istio Gateway /
  VirtualService wording. New code-quality constraints: no comments in code, no
  `!!`, request validation at the HTTP boundary, ktlint and warnings-as-errors
  gate in `./gradlew check`, health/metrics/JSON-log/graceful-shutdown contract.
  Agent tooling: the ponytail `ponytail:` comment marker no longer applies.
  Expanded guidance, no principle removed, so not MAJOR.
Modified principles:
  - IV. Local-First, Minimal-Ops Simplicity — homelab k8s platform is the deploy
    target; local dev stays Docker Compose (Postgres) + `./gradlew run`.
Modified sections: Technology & Architecture Constraints (deploy, CI, code
  quality, operations), Development Workflow (agent tooling).
Templates: plan/spec/tasks templates ✅ no deployment or comment rules, no change needed.
Previous: 2.2.1 → 2.3.0 (MINOR, 2026-10-28) — principle IV and Technology
  constraints: Kubernetes + Istio manifests (plain kustomize, `.devops/k8s/`) are in
  scope as an optional deployment target; Docker Compose stays the local dev/deploy
  path; no Helm or operators. Multi-host orchestration beyond these manifests stays
  deferred. Expanded guidance, nothing removed or redefined, so not MAJOR.
Modified principles:
  - IV. Local-First, Minimal-Ops Simplicity — deferred list no longer names k8s;
    optional k8s/Istio target added with its limits.
Templates: plan/spec/tasks templates ✅ no k8s references, no change needed.
Previous: 2.2.0 → 2.2.1 (PATCH, 2026-10-27) — Development Workflow no longer
  names deleted files (user-roadmap.md, agent-os/, .copilot/).
Previous: 2.1.0 → 2.2.0 (MINOR, 2026-10-27) — principle IV: OpenID Connect
  sign-in moved from deferred to in scope (spec 005), alongside local accounts.
  Materially expanded guidance; no principle removed or redefined and deployments
  without an IdP keep working, so not MAJOR.
Previous: 2.0.0 → 2.1.0 (MINOR — legacy device-ingestion clause removed)
Modified principles (2.2.0):
  - IV. Local-First, Minimal-Ops Simplicity — OIDC sign-in in scope (generic, optional,
    IdP authenticates only); deferred list now names IdP-managed roles / provisioning
    instead of single sign-on. Other deferrals unchanged.
Added sections: none
Removed sections: none
Templates requiring updates:
  - .specify/templates/plan-template.md ✅ no change needed (generic Constitution Check)
  - .specify/templates/spec-template.md ✅ no change needed
  - .specify/templates/tasks-template.md ✅ no change needed
Follow-up TODOs:
  - "i-have-adhd" still not installed — Agent Tooling rule unchanged.
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
version bump and explicit call-out. The pre-2.0 API (`/api/v1/stats/**`,
`.docs/*_Contract.md`) is not a contract: it is removed, not emulated.

**Rationale**: devices and app clients evolve independently of the backend
once they target the generic contracts, so those must be stable; the old API
has no real consumers and the device software can be updated, so keeping it
would only add code.

### III. Test Coverage for Core Flows, Not Every Line

Ingestion (per channel), mapping, record storage, query/aggregation and
access control MUST have test coverage before a feature is done: unit tests
for mapping and aggregation rules, integration tests for endpoints, contract
tests for published shapes (ingest envelope, query API, definition format). Tests
target core user/device flows and contract shapes — MUST NOT be written for
every intermediate step, and non-critical edge cases MAY be deferred to a
follow-up rather than blocking the feature. Tests assert behavior, not
implementation detail.

**Rationale**: the generic model concentrates all risk in a few shared paths
(mapping, query, access); covering those well protects every domain at once
without over-testing what isn't load-bearing.

### IV. Local-First, Minimal-Ops Simplicity

Default to self-hosted, low-friction deployment at home-lab scale. The
deployment target is the homelab Kubernetes platform, following its platform
contract (homelab repo): plain kustomize manifests in `.devops/k8s/`, no Helm
charts or operators; the platform owns Postgres, the gateway and secrets.
Local development stays Docker Compose for dependencies plus `./gradlew run`;
the app MUST keep running outside the cluster. In scope: platform-managed accounts, sign-in,
roles admin / editor / viewer, dashboard sharing, user preferences, and
sign-in through any OpenID Connect provider (generic: issuer + client id) next
to local password accounts. Local accounts MAY be switched off by config; the
app MUST work with no IdP configured; the IdP only authenticates — roles stay
managed in the app; device/collector source credentials stay separate from
user sign-in. v1 serves exactly one organization, but every owned definition
and user MUST belong to it explicitly so multiple organizations can be added
later without changing existing definitions, exports or contracts. Do not
build provider-specific IdP code, IdP-managed roles or user provisioning,
multi-organization, alerting, push-to-browser streaming, data export,
multi-host orchestration beyond the platform manifests (Helm, operators,
autoscaling) or analytics-store migrations until a concrete, confirmed need
exists. A new ingestion channel (pull, Kafka, gRPC) is built only when a real
producer for it exists. Follow YAGNI: no abstraction beyond what the generic
source → dataset → query → widget model itself needs.

**Rationale**: a user panel is now an explicit requirement (spec 004 US4), so
auth leaves the backlog; OIDC sign-in (spec 005) lets users sign in through a
lightweight IAM provider instead of app passwords, without vendor lock-in.
The homelab platform contract fixes image, routing, probes and secrets once
for every app, so the service follows it instead of carrying its own Istio
Gateway, Postgres or secret files. Everything else stays deferred
because building ahead of a trigger wastes effort on a single-owner, home-lab
product.

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
  `/plugin/*` modules, the v1 API, `StatsRepositoryRedisImpl` and Redis
  wiring are removed up front (spec 004 FR-023).
- Frontend: separate repo `Statistics-UI`, Kotlin Multiplatform Compose (web
  + desktop); only generic widget renderers, dashboard viewer and definition
  editors — no domain DTOs, clients or screens. Talks to the backend only
  through published contracts.
- Storage: one record store (PostgreSQL direction) for definitions, users and
  records; Redis kept only as untouched archive.
- Contracts: JSON over HTTP; generic APIs versioned under `/api/v2/...`
  (v2 because v1 was the removed plugin API). RESTful, plural nouns, nesting ≤ 3 levels,
  filtering/sorting/pagination via query params or query body, not new
  endpoints.
- Ingestion: HTTP push is the baseline channel; HTTP pull, Kafka and gRPC use
  the same mapping model and are added only when a producer exists. A record
  is acknowledged only after it is durably stored.
- Validation: declarative, per source mapping, always server-side; never
  trust device/client input. Every source has its own rotatable credential;
  data without a valid one is rejected.
- Deploy: homelab platform contract. Jib builds a linux/amd64 + linux/arm64
  image (eclipse-temurin:21-jre, user 10001); `.devops/k8s/` holds
  ServiceAccount, Deployment, Service, HTTPRoute on `home-gateway`,
  AuthorizationPolicy and ConfigMap for namespace `apps`;
  `.devops/flux/` holds the Flux ImageRepository/ImagePolicy. Secrets live
  SOPS-encrypted in the homelab repo, never in this repo or the image. No
  Helm, no operators. Local dev: Docker Compose for Postgres.
- Operations: `/health/live` (no dependencies), `/health/ready` (database,
  503 when down), Prometheus `/metrics`, JSON logs with `traceId` (plain via
  `LOG_FORMAT=plain`), SIGTERM drains in-flight calls and exits 0, outbound
  calls time out (connect ≤ 2 s, request ≤ 10 s) and carry `traceparent`,
  invalid configuration fails fast at startup.
- Code quality: Kotlin coding conventions; no comments in code (names and
  small functions carry intent); no `!!`; request bodies validated at the HTTP
  boundary (Ktor RequestValidation, 400 `ApiError`); ktlint and
  `allWarningsAsErrors` gate `./gradlew check`.
- Testing: Kotest + Testcontainers for storage-backed integration tests.
- CI: GitHub Actions runs `./gradlew check`; on `main` or `v*` tags it pushes
  `ghcr.io/jagieloadrian/statistics-service` tagged
  `main-<yyyyMMddHHmmss>-<sha7>` or the semver version, never `latest`.
  Renovate keeps dependencies current.

## Development Workflow

- New or changed contracts (APIs, ingest envelope, definition format) MUST
  update OpenAPI/`.docs` in the same change.
- Tests MUST accompany changes to ingestion, mapping, record store, query or
  access control; such a change without test updates fails review — scoped
  to core flows per Principle III.
- Product direction lives in `specs/004-universal-stats-platform/spec.md`
  and later specs.
- **Agent tooling**: every Claude Code skill invocation on this project MUST
  run with the `ponytail` and `caveman` skills active (lazy/minimal-diff
  implementation, terse output), plus `i-have-adhd` when that plugin is
  installed in the working environment. If a named skill isn't installed,
  proceed without it and say so rather than blocking the task. The ponytail
  `ponytail:` comment marker does not apply here (no comments in code):
  record deliberate simplifications in the commit message or
  `PROJECT-STATUS.md` instead.

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

**Version**: 2.4.0 | **Ratified**: 2026-07-30 | **Last Amended**: 2026-10-28
