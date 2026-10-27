# Tasks: Sign-in through an OpenID Connect Provider

**Input**: Design documents from `/specs/005-oidc-login/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/auth-oidc.md, quickstart.md

**Tests**: included — constitution III requires tests for access-control
changes. Backend: Kotest integration tests against an in-test fake provider
(no Authelia). Frontend: `commonTest` for the callback parser, `jvmTest` for
the desktop loopback receiver.

**Organization**: grouped by user story; each story phase ends at a checkpoint
that can be demoed on its own. Approval required before implementation.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1…US4 from spec.md

## Path Conventions

- `StatisticsService/` = `/home/diether18/IdeaProjects/StatisticsService/`
  (backend; Kotlin in `src/main/kotlin/com/anjo/statisticservice/`, tests in
  `src/test/kotlin/com/anjo/statisticservice/`).
- `Statistics-UI/` = `/home/diether18/IdeaProjects/Statistics-UI/` (frontend;
  Kotlin in `composeApp/src/{commonMain,jvmMain,webMain,commonTest}/kotlin/com/anjo/statisticsui/`).
- Shapes: `contracts/auth-oidc.md`; schema: `data-model.md`; decisions §1–§8:
  `research.md`.
- Mark deliberate shortcuts with `// ponytail:` comments naming the ceiling.

---

## Phase 1: Setup

**Purpose**: dependency, config keys, dev reference provider.

- [X] T001 Add `java-jwt` (`com.auth0:java-jwt`) and `jwks-rsa` (`com.auth0:jwks-rsa`), latest stable, to `StatisticsService/gradle/libs.versions.toml` and as `implementation` in `StatisticsService/build.gradle.kts` (research §1)
- [X] T002 [P] Add under `ktor.auth` in `StatisticsService/src/main/resources/application.yaml` and `StatisticsService/src/test/resources/application-test.yaml`: `local: "${AUTH_LOCAL:true}"`, `oidc.issuer: "${OIDC_ISSUER:}"`, `oidc.clientId: "${OIDC_CLIENT_ID:}"`, `oidc.clientSecret: "${OIDC_CLIENT_SECRET:}"`, `oidc.name: "${OIDC_NAME:SSO}"`
- [X] T003 [P] Create Authelia dev setup per research §8 in `StatisticsService/.devops/authelia-compose/`: `compose.yaml` (`authelia/authelia`, port 9091, mounts config + secrets), `configuration.yml` (file auth backend, filesystem notifier, session domain `example.com` / `authelia_url: https://auth.example.com:9091`, server TLS from `secrets/`, OIDC issuer key from `secrets/`, one public client `statistics-ui` with `require_pkce`, `pkce_challenge_method: S256`, `token_endpoint_auth_method: none`, `authorization_policy: one_factor`, `consent_mode: implicit`, redirect URIs `http://localhost:8081/` and `http://127.0.0.1/callback`, claims policy putting `email`, `email_verified`, `name` into the ID token), `users_database.yml` (`admin` → admin@example.com, `alice` → alice@example.com, argon2 hashes, documented dev passwords), `README.md` (hosts entry, `authelia crypto` commands for secrets/cert/RSA key, `keytool -importcert` into `secrets/truststore.p12`, run command); first confirm in Authelia's docs/source that a loopback redirect URI matches any port (RFC 8252 §7.3) — if not, register `http://127.0.0.1:53682/callback` and note the fixed port for T030; add `.devops/authelia-compose/secrets/` to `StatisticsService/.gitignore`

---

## Phase 2: Foundational (blocking)

**Purpose**: schema, DTOs, config wiring, public config endpoint, test fake.

**⚠️ CRITICAL**: no user story work before this phase is done.

- [X] T004 Create `StatisticsService/src/main/resources/db/migration/V2__oidc.sql` per data-model.md: `password_hash` drop NOT NULL, add `oidc_issuer text`, `oidc_subject text`, `UNIQUE (oidc_issuer, oidc_subject)`, `CHECK (password_hash IS NOT NULL OR oidc_subject IS NOT NULL)`
- [X] T005 Update `Users` in `StatisticsService/src/main/kotlin/com/anjo/statisticservice/db/Tables.kt`: `passwordHash` nullable, add `oidcIssuer`, `oidcSubject` nullable text columns
- [X] T006 Add `hasPassword: Boolean = true` to `User` in `StatisticsService/src/main/kotlin/com/anjo/statisticservice/model/User.kt`; add `AuthConfig(local: Boolean, oidc: OidcInfo?)`, `OidcInfo(name: String)`, `OidcStartRequest(redirectUri)`, `OidcStartResponse(authorizationUrl, state, codeVerifier)`, `OidcLoginRequest(code, codeVerifier, redirectUri)` (all `@Serializable`) to `StatisticsService/src/main/kotlin/com/anjo/statisticservice/model/dto/Auth.kt`
- [X] T007 Update `StatisticsService/src/main/kotlin/com/anjo/statisticservice/repository/UserRepository.kt`: `credentials()`/`passwordHash()` return nullable hash, `insert(...)` takes `passwordHash: String?` plus optional `oidcIssuer`/`oidcSubject`, new `findByOidc(issuer, subject): Long?`, `findByEmail(email)` (normalized via `normalizeEmail`, returns id + whether a provider identity is set), `linkOidc(id, issuer, subject)`; `toUser` sets `hasPassword = passwordHash != null`
- [X] T008 In `StatisticsService/src/main/kotlin/com/anjo/statisticservice/service/AuthService.kt` extract `issueSession(userId): LoginResponse` from `login()` (new token, insert hash, load user) so password and provider sign-in share it; no behaviour change
- [X] T009 Wire config in `StatisticsService/src/main/kotlin/com/anjo/statisticservice/di/DependencyInjection.kt`: read `ktor.auth.local` and `ktor.auth.oidc.*`; provider enabled ⇔ issuer and clientId non-blank; if `local == false` and no provider → throw at startup with "Enable local sign-in or configure OIDC_ISSUER + OIDC_CLIENT_ID" (FR-010); pass `local`, `adminEmail` and the (nullable) provider settings to `AuthService`/routes
- [X] T010 Add public `GET /auth/config` → `AuthConfig` in `StatisticsService/src/main/kotlin/com/anjo/statisticservice/controller/AuthController.kt` (with `.describe {}`) and register it outside `authenticate("user")` in `StatisticsService/src/main/kotlin/com/anjo/statisticservice/controller/Routing.kt`
- [X] T011 [P] Create `StatisticsService/src/test/kotlin/com/anjo/statisticservice/testutils/FakeOidcProvider.kt` per research §7: JDK `HttpServer` on `127.0.0.1:0`; RSA key pair from `KeyPairGenerator`; serves `/.well-known/openid-configuration` (issuer = its own base URL), `/jwks` (kid + n/e), `/token` (form POST; records last `code`, `code_verifier`, `redirect_uri`, `client_id`; returns `{access_token, id_token}` or `400 invalid_grant` when told to), `/userinfo` (Bearer access token → configured claims); test knobs: `claims` for the next ID token, `idTokenClaims`/`userinfoClaims` split, `signWithOtherKey`, `issuer`/`audience`/`expiresAt` overrides, `failToken`, `stop()`
- [X] T012 [P] In `StatisticsService/src/test/kotlin/com/anjo/statisticservice/testutils/PostgresContainerSetup.kt` add `testApp(extraConfig: Map<String, String> = emptyMap(), block)`: merges `extraConfig` into the config; when `ktor.auth.local` is `"false"` or `ktor.auth.adminPassword` is blank, the block's `client` is the anonymous client (no admin password login); existing callers unchanged

**Checkpoint**: `./gradlew test` green, all existing tests unchanged (SC-002); `GET /api/v2/auth/config` → `{"local":true,"oidc":null}` (quickstart §1).

---

## Phase 3: User Story 1 — Sign in on the web through the provider (Priority: P1) 🎯 MVP

**Goal**: a provider user signs in from the web app; first sign-in creates a viewer.

**Independent Test**: quickstart §2 steps 1, 3, 5, 6 (alice → viewer, same account on re-sign-in, cancel → reason, no user).

### Tests for User Story 1

- [X] T013 [US1] Create `StatisticsService/src/test/kotlin/com/anjo/statisticservice/controller/OidcAuthTest.kt` (Kotest FunSpec, `FakeOidcProvider` + `testApp(extraConfig = issuer/clientId)`): `/auth/config` shows `oidc.name`; `start` returns URL with `response_type=code`, `client_id`, `redirect_uri`, `state`, `code_challenge_method=S256` and `code_challenge == base64url(sha256(codeVerifier))`; first `POST /auth/oidc` creates a viewer with `hasPassword=false` and the token works on `GET /me`; the fake saw the same `code_verifier` and `redirect_uri`; second sign-in with the same `sub` → same user id; `POST /auth/logout` then `/me` → 401; `start`/exchange without provider config → 404; provider stopped → 503
- [X] T014 [US1] Add broken-token cases to `OidcAuthTest.kt` (SC-004), each → 401 and user count unchanged: signed with another key, wrong `iss`, wrong `aud`, expired, token endpoint `invalid_grant`; claims cases: email only in userinfo → account gets that email; userinfo `sub` differs → 401; no email anywhere → 401

### Implementation for User Story 1

- [X] T015 [US1] Create `StatisticsService/src/main/kotlin/com/anjo/statisticservice/service/Oidc.kt` (one class, no interface) per research §1–§2: ctor `(issuer, clientId, clientSecret)`; lazy discovery via JDK `HttpClient` (5 s timeout) cached after first success, any failure → `ApiException(ServiceUnavailable, "unavailable", "Sign-in provider unavailable")`; `start(redirectUri)` → `OidcStartResponse` using `newToken()` for state and verifier and base64url(`sha256(verifier)`) as S256 challenge, `scope=openid email profile`, URL-encoded params; `redeem(code, verifier, redirectUri): OidcClaims(subject, email?, emailVerified, name)` — form POST to `token_endpoint` (HTTP Basic when secret set), non-2xx → 401 "Provider refused sign-in"; verify `id_token` with `JWT.require(Algorithm.RSA256(...))` + `JwkProviderBuilder(jwks_uri).cached(...).rateLimited(...)`, `withIssuer(discovery issuer)` which must equal the configured issuer, `withAudience(clientId)`, 60 s leeway, any `JWTVerificationException` → 401; if no `email` claim GET `userinfo_endpoint` with the access token and require same `sub`; `// ponytail: RS256 only, add ES256 when a provider needs it`; `// ponytail: no nonce — code+PKCE, ID token straight from the token endpoint`
- [X] T016 [US1] Add `oidcLogin(claims, issuer): LoginResponse` to `StatisticsService/src/main/kotlin/com/anjo/statisticservice/service/AuthService.kt`: email missing → 401; in one `db.tx`: `findByOidc` → sign in; else email already used by another account → 401 (replaced by linking in T025); else insert viewer with no password and the OIDC identity (name = claim `name` or email); then `issueSession`; `// ponytail: email/name not synced after creation`
- [X] T017 [US1] Add `POST /auth/oidc/start` and `POST /auth/oidc` inside `rateLimit(LOGIN)` in `StatisticsService/src/main/kotlin/com/anjo/statisticservice/controller/AuthController.kt` (404 when no provider; `.describe {}` summaries); construct `Oidc` in `StatisticsService/src/main/kotlin/com/anjo/statisticservice/di/DependencyInjection.kt` and pass it to the routes in `StatisticsService/src/main/kotlin/com/anjo/statisticservice/controller/Routing.kt`
- [X] T018 [P] [US1] Add `@Serializable` `AuthConfig(local, oidc: OidcInfo?)`, `OidcInfo(name)`, `OidcStart(authorizationUrl, state, codeVerifier)`, `OidcLoginRequest(code, codeVerifier, redirectUri)` and `hasPassword: Boolean = true` on `User` in `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/model/User.kt`
- [X] T019 [US1] Add `authConfig()`, `oidcStart(redirectUri)`, `oidcLogin(code, codeVerifier, redirectUri): User` (stores the session like `login()`) to `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/api/PlatformApi.kt`
- [X] T020 [P] [US1] Create `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/auth/ProviderSignIn.kt`: `data class Callback(code: String?, state: String?, error: String?)`, pure `parseCallback(query: String): Callback?` (accepts leading `?`, URL-decodes, `error_description` preferred as message, null when no `code`/`error`), `expect suspend fun providerSignIn(api: PlatformApi): User?` and `expect suspend fun resumeProviderSignIn(api: PlatformApi): User?`
- [X] T021 [P] [US1] Create `Statistics-UI/composeApp/src/commonTest/kotlin/com/anjo/statisticsui/CallbackTest.kt`: code+state, error+description, encoded values, empty/unrelated query → null
- [X] T022 [US1] Create `Statistics-UI/composeApp/src/webMain/kotlin/com/anjo/statisticsui/auth/ProviderSignIn.web.kt`: `providerSignIn` — `redirectUri = window.location.origin + "/"`, `api.oidcStart`, save `state`, `codeVerifier`, `redirectUri` in `sessionStorage`, `window.location.href = authorizationUrl`, return null; `resumeProviderSignIn` — `parseCallback(window.location.search)` ?: return null, `history.replaceState` to drop the query, clear `sessionStorage` keys, state mismatch → error "Sign-in expired, try again", provider `error` → error with its message, else `api.oidcLogin`. Also create `Statistics-UI/composeApp/src/jvmMain/kotlin/com/anjo/statisticsui/auth/ProviderSignIn.jvm.kt` with `resumeProviderSignIn` = null and `providerSignIn` throwing "Desktop provider sign-in: see US4" (replaced in T030)
- [X] T023 [US1] Update `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/ui/login/LoginScreen.kt`: on start load `api.authConfig()` and run `resumeProviderSignIn(api)?.let(onSignedIn)`; show the email/password form only when `config.local`; show "Sign in with {oidc.name}" when `config.oidc != null` calling `providerSignIn`; show backend/provider errors (incl. 503 "Sign-in provider unavailable") in the existing error text

**Checkpoint**: backend tests green; quickstart §2 steps 1, 3, 5, 6 in the web app against Authelia.

---

## Phase 4: User Story 2 — First admin and roles for provider users (Priority: P2)

**Goal**: `ADMIN_EMAIL` bootstraps the admin through the provider; existing accounts are linked by verified email; roles stay in the app.

**Independent Test**: quickstart §2 steps 2 and 4 (admin@example.com → admin; admin promotes alice).

### Tests for User Story 2

- [X] T024 [US2] Add to `StatisticsService/src/test/kotlin/com/anjo/statisticservice/controller/OidcAuthTest.kt`: verified `ADMIN_EMAIL` on an empty install (no `ADMIN_PASSWORD`) → admin; same email unverified → viewer; local account (created via `POST /users`) + verified same email → linked: same id, role kept, password still works; unverified same email → 401, account unchanged; admin `PUT /users/{id}` role editor → provider user's next sign-in still editor; email of an account already linked to another `sub` → 401, account unchanged; `ADMIN_EMAIL` in different case still → admin; last-admin protection: demoting/deleting the only (provider) admin → 409 as for local admins; admin deletes a provider user → their token gets 401 and next sign-in creates a new viewer; `bootstrapAdmin` with blank password creates no user

### Implementation for User Story 2

- [X] T025 [US2] Extend `oidcLogin` in `StatisticsService/src/main/kotlin/com/anjo/statisticservice/service/AuthService.kt` per research §6 / data-model state table: email of an existing account → `linkOidc` if `emailVerified` and the account has no provider identity, else 401 ("Email not verified by the provider" / "Account is linked to another sign-in"); new account role = admin when `emailVerified` and normalized email == normalized `adminEmail`, else viewer (`bootstrapAdmin` already skips a blank password — keep, note it in KDoc)

**Checkpoint**: US1 + US2 tests green; quickstart §2 complete.

---

## Phase 5: User Story 3 — Operator chooses sign-in methods (Priority: P2)

**Goal**: local only / local + provider / provider only, by config; disabled methods refused server-side; ingestion untouched.

**Independent Test**: quickstart §1, §4, §5.

### Tests for User Story 3

- [ ] T026 [US3] Add mode tests to `StatisticsService/src/test/kotlin/com/anjo/statisticservice/controller/OidcAuthTest.kt`: `/auth/config` for no provider / both / provider only; with `AUTH_LOCAL=false` `POST /auth/login` → 403 and `PUT /me/password` (provider user) → 403; provider-only user with local on: `PUT /me/password` → 403; `local=false` without provider → app start throws; with provider configured and local off, `POST /ingest/{id}` with the source token → 200 and with a user session token → 401 (FR-012)

### Implementation for User Story 3

- [ ] T027 [US3] In `StatisticsService/src/main/kotlin/com/anjo/statisticservice/service/AuthService.kt` make `login()` and `changePassword()` throw `forbidden("Password sign-in is disabled")` when `local` is off, and `changePassword()` throw `forbidden("Account has no password")` when the stored hash is null
- [ ] T028 [P] [US3] In `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/ui/account/AccountScreen.kt` hide the password section when `!session.user.hasPassword` or `api.authConfig().local == false`

**Checkpoint**: all backend tests green; quickstart §1, §4, §5.

---

## Phase 6: User Story 4 — Sign in from the desktop app (Priority: P3)

**Goal**: desktop sign-in through the system browser via RFC 8252 loopback.

**Independent Test**: quickstart §3.

### Implementation for User Story 4

- [ ] T029 [P] [US4] Add `modules("jdk.httpserver")` to `nativeDistributions` in `Statistics-UI/composeApp/build.gradle.kts`
- [ ] T030 [US4] Implement `Statistics-UI/composeApp/src/jvmMain/kotlin/com/anjo/statisticsui/auth/ProviderSignIn.jvm.kt` per research §2: `HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))`, `redirectUri = "http://127.0.0.1:<port>/callback"`, `api.oidcStart`, `Desktop.getDesktop().browse(URI(authorizationUrl))`, await one `/callback` via `CompletableDeferred` with `withTimeout(5.minutes)`, reply a small HTML "Signed in — you can close this window" (or the error), check `state`, `api.oidcLogin`; `server.stop(0)` in `finally` so cancellation frees the port; `// ponytail: ephemeral port, fixed port if a provider requires exact match`
- [ ] T036 [US4] Make the loopback receiver in `ProviderSignIn.jvm.kt` a small internal function (`awaitCallback(server, expectedState)`) and cover it in `Statistics-UI/composeApp/src/jvmTest/kotlin/com/anjo/statisticsui/auth/LoopbackTest.kt` (Kotest): `/callback?code&state` → code; wrong state → error; provider `error` → its message; timeout → error and port released
- [ ] T031 [US4] In `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/ui/login/LoginScreen.kt` show "Waiting for the browser…" with a Cancel button while `providerSignIn` runs (cancel the coroutine job; back to the sign-in form)

**Checkpoint**: quickstart §3 on desktop.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T032 [P] Merge contracts/auth-oidc.md into the "Auth & user panel" table of `StatisticsService/specs/004-universal-stats-platform/contracts/api-v2.md` (FR-015) and confirm `StatisticsService/src/test/kotlin/com/anjo/statisticservice/controller/OpenApiTest.kt` still passes with the new routes
- [ ] T033 [P] Add a "Sign-in methods / OIDC" section to `StatisticsService/README.md` (env vars `AUTH_LOCAL`, `OIDC_ISSUER`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_NAME`, `ADMIN_EMAIL` rules, link to `.devops/authelia-compose/README.md`) and update "Sign in" in `Statistics-UI/README.md` (provider button, desktop browser flow, redirect URIs to register)
- [ ] T034 Run `./gradlew test` in `StatisticsService/` (Authelia stopped, SC-006) and `./gradlew :composeApp:jvmTest :composeApp:jvmJar` in `Statistics-UI/`, then walk quickstart §1–§6 against Authelia
- [ ] T035 Update `StatisticsService/PROJECT-STATUS.md` (completed work, decisions: code exchange → opaque session, backend-generated PKCE, RS256 via JWKS, no refresh tokens; next steps)

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (T001–T003) → Foundational (T004–T012) → user stories → Polish.
- T003 (Authelia) is needed only for manual quickstart checks, not for tests.

### User Story Dependencies

- **US1 (P1)**: after Foundational. MVP.
- **US2 (P2)**: after US1 (extends `oidcLogin`, T016 → T025).
- **US3 (P2)**: after Foundational; backend part independent of US1, but its
  provider-user tests (T026) use the US1 exchange → do after US1.
- **US4 (P3)**: after US1 frontend (T019, T020, T023); independent of US2/US3.

### Within Each User Story

- Tests first and failing (T013/T014 before T015–T017; T024 before T025;
  T026 before T027).
- Backend before frontend for the same endpoint (T017 before T019).

### Parallel Opportunities

- T002 ∥ T003 (after T001).
- T011 ∥ T012 ∥ T004–T008 (different files).
- US1: frontend T018, T020, T021 ∥ backend T015–T017.
- After US1: US2 (backend), US3 frontend T028 and US4 (frontend) can run in
  parallel — different files and repos.
- T032 ∥ T033.

## Parallel Example: User Story 1

```text
Backend:  T013 → T014 → T015 → T016 → T017
Frontend: T018 ∥ T020 ∥ T021  →  T019 (needs T018)  →  T022 → T023
```

## Implementation Strategy

### MVP First (User Story 1 only)

Setup + Foundational + US1 → web sign-in through Authelia works next to
passwords; installs without a provider are unchanged. Stop and validate
(quickstart §0–§2 minus admin bootstrap).

### Incremental Delivery

1. MVP (US1) → demo web sign-in.
2. US2 → provider-only admin possible; linking existing accounts.
3. US3 → `AUTH_LOCAL=false` mode.
4. US4 → desktop sign-in.
5. Polish → contract merge, READMEs, full quickstart.

## Notes

- One atomic conventional commit per task group, per repo; backend and
  frontend never share a commit.
- No interface for `Oidc`: the fake provider is an HTTP server.
- Deferred (constitution IV): several providers, refresh tokens,
  RP-initiated logout, syncing email/name, ES256.
