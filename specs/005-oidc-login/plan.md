# Implementation Plan: Sign-in through an OpenID Connect Provider

**Branch**: `005-oidc-login` | **Date**: 2026-10-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-oidc-login/spec.md`

## Summary

Add generic OIDC sign-in next to local passwords without a second auth path:
the backend starts the Authorization Code + PKCE flow, redeems the code at the
provider, verifies the ID token against the provider's JWKS, finds/links/
creates the user, and returns the **existing opaque session token** — every
other route, expiry, logout and ingestion stays as is. A public
`GET /auth/config` tells clients which methods are on. Statistics-UI gets a
provider button: web via browser redirect, desktop via RFC 8252 loopback with
the system browser; no crypto in the clients. Authelia (compose, file users)
is the dev reference; tests use an in-test fake provider. Decisions:
[research.md](./research.md).

## Technical Context

**Language/Version**: Kotlin 2.4.21 / JVM (backend); Kotlin 2.4.21
Multiplatform — wasmJs, js, JVM desktop (frontend)

**Primary Dependencies**: backend — Ktor 3.6.0 (existing server stack incl.
`ktor-server-auth`, rate-limit); NEW `ktor-server-auth-jwt` only for its
`java-jwt` + `jwks-rsa` (ID-token verification); JDK `HttpClient` for
discovery/token/userinfo. Frontend — no new deps (JDK `HttpServer` +
`java.awt.Desktop` on desktop, browser `window`/`sessionStorage` on web).

**Storage**: PostgreSQL — Flyway `V2__oidc.sql` on `users`
([data-model.md](./data-model.md)); no new tables.

**Testing**: Kotest FunSpec + `ktor-server-test-host` + Testcontainers
Postgres (existing); NEW `testutils/FakeOidcProvider` (JDK `HttpServer`,
in-test RSA key, java-jwt signing) — no Authelia, offline. Frontend:
`commonTest` for the callback-query parser only.

**Target Platform**: Linux server; browsers (wasm/js) and desktop JVM

**Project Type**: web service + separate multiplatform client (two repos)

**Performance Goals**: sign-in < 30 s end to end (SC-001); discovery + JWKS
cached, so one token call + optional userinfo per sign-in

**Constraints**: no IdP required to run (FR-010); source tokens untouched
(FR-012); config-only switching, no rebuild (SC-005); verifier never in a URL

**Scale/Scope**: ≤ 10 users, one provider; backend ~1 new service file + 1
migration + small edits to Auth/User code and routes; frontend ~3 new files
(common + jvm + web actuals) + login/account screen edits; Authelia compose
folder

## Constitution Check

*GATE: checked against `.specify/memory/constitution.md` v2.2.0 — before
Phase 0 and re-checked after Phase 1 design.*

- **I. Configuration Over Code** — PASS. Provider chosen by config (issuer +
  client id); nothing domain- or provider-specific in code.
- **II. Generic, Versioned Contracts** — PASS. Additive `/api/v2` endpoints
  and one added user field ([contracts/auth-oidc.md](./contracts/auth-oidc.md));
  merged into 004 `api-v2.md` + route `describe {}` in the same change.
- **III. Test Coverage for Core Flows** — PASS. Access-control path change →
  integration tests for config, start, exchange, linking, admin bootstrap,
  broken tokens, modes; existing auth/ingest tests must stay green unchanged.
- **IV. Local-First, Minimal-Ops** — PASS (v2.2.0 puts OIDC in scope). Works
  with no IdP; local accounts default on; roles app-managed; source credentials
  separate. Deferred: several providers, IdP roles/provisioning, refresh
  tokens, RP-initiated logout.
- **V. Storage & API Modularity** — PASS. Two nullable columns on `users`;
  record store untouched.

Post-design re-check (after data-model + contracts): PASS, no new violations.

## Project Structure

### Documentation (this feature)

```text
specs/005-oidc-login/
├── spec.md
├── plan.md
├── research.md            # 8 decisions: exchange vs JWT, PKCE, clients, expiry, config, linking, tests, Authelia
├── data-model.md          # users delta (V2 migration), DTO field
├── quickstart.md
├── contracts/auth-oidc.md # delta to 004 contracts/api-v2.md
├── checklists/requirements.md
└── tasks.md               # /speckit-tasks
```

### Source Code — backend (this repo)

```text
.devops/authelia-compose/                      # NEW compose.yaml, configuration.yml, users_database.yml,
                                               #   README.md (hosts entry, secret/cert/truststore generation);
                                               #   secrets/ git-ignored
gradle/libs.versions.toml, build.gradle.kts    # MODIFIED + ktor-server-auth-jwt
src/main/resources/application.yaml            # MODIFIED ktor.auth.local, ktor.auth.oidc.{issuer,clientId,clientSecret,name}
src/main/resources/db/migration/V2__oidc.sql   # NEW
src/main/kotlin/com/anjo/statisticservice/
├── service/Oidc.kt                            # NEW discovery (lazy, cached), start (state, verifier, URL),
│                                              #   redeem code, verify ID token (JWKS), userinfo fallback → claims
├── service/AuthService.kt                     # MODIFIED oidcLogin (find/link/create, admin email), local on/off,
│                                              #   bootstrapAdmin without password = no-op
├── repository/UserRepository.kt, db/Tables.kt # MODIFIED nullable hash, oidc columns, findByOidc, link
├── model/User.kt, model/dto/Auth.kt           # MODIFIED hasPassword; AuthConfig, OidcStart{Request,Response}, OidcLogin
├── controller/AuthController.kt               # MODIFIED GET /auth/config, POST /auth/oidc/start, POST /auth/oidc;
│                                              #   403 on login/password when local off
└── di/DependencyInjection.kt                  # MODIFIED read config, fail fast if no method, wire Oidc
src/test/kotlin/com/anjo/statisticservice/
├── testutils/FakeOidcProvider.kt              # NEW
├── testutils/PostgresContainerSetup.kt        # MODIFIED testApp with extra config
└── controller/OidcAuthTest.kt                 # NEW
specs/004-universal-stats-platform/contracts/api-v2.md  # MODIFIED auth table (FR-015)
README.md                                      # MODIFIED sign-in config section
```

### Source Code — frontend (`/home/diether18/IdeaProjects/Statistics-UI`)

```text
composeApp/src/commonMain/kotlin/com/anjo/statisticsui/
├── model/User.kt                      # MODIFIED AuthConfig, OidcStart, OidcLogin; User.hasPassword
├── api/PlatformApi.kt                 # MODIFIED authConfig(), oidcStart(), oidcLogin()
├── auth/ProviderSignIn.kt             # NEW expect providerSignIn(api) / resumeProviderSignIn(api);
│                                      #   common parseCallback(query)
├── ui/login/LoginScreen.kt            # MODIFIED methods from /auth/config, provider button, errors
└── ui/account/AccountScreen.kt        # MODIFIED hide password change when !hasPassword or local off
composeApp/src/jvmMain/.../auth/ProviderSignIn.jvm.kt  # NEW loopback HttpServer + Desktop.browse
composeApp/src/webMain/.../auth/ProviderSignIn.web.kt  # NEW sessionStorage + location redirect/resume
composeApp/src/commonTest/.../CallbackTest.kt          # NEW
composeApp/build.gradle.kts            # MODIFIED nativeDistributions modules("jdk.httpserver")
README.md                              # MODIFIED sign-in section
```

**Structure Decision**: same repos and layers as 004. One new backend service
file (`Oidc.kt`, no interface — the fake provider is an HTTP server, not a
second implementation); frontend follows the existing `TokenStore`
expect/actual pattern (jvmMain + webMain).

## Delivery Slices

| Slice | Stories | Content | Exit check |
|-------|---------|---------|------------|
| S0 | — | Dependency, config keys, V2 migration, fake provider, `GET /auth/config`, startup check | `./gradlew test` green; quickstart §1 |
| S1 | US1 | Backend start + exchange + create/sign-in; web provider button + redirect/resume; Authelia compose | quickstart §0, §2 steps 1–3, 5–6 |
| S2 | US2 | Linking by verified email, admin bootstrap by email, `hasPassword` | quickstart §2 steps 2, 4 |
| S3 | US3 | `AUTH_LOCAL=false`: 403s, UI hides password form/section | quickstart §4, §5 |
| S4 | US4 | Desktop loopback sign-in | quickstart §3 |

## Complexity Tracking

No constitution violations — nothing to justify.
