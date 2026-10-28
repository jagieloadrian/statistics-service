# Project Status

Updated 2026-10-28. Features: [spec 004 universal stats platform](specs/004-universal-stats-platform/spec.md)
(tasks: [tasks.md](specs/004-universal-stats-platform/tasks.md)), [spec 005 OIDC sign-in](specs/005-oidc-login/spec.md)
(tasks: [tasks.md](specs/005-oidc-login/tasks.md)).

## Completed

Backend (this repo, spec 004 built on branch `feature-2/update-ktor-plugin`):

| Slice | Commit | Content |
|-------|--------|---------|
| S0 | 9735ec1 | plugin modules, v1 API, Redis stack removed |
| Foundation | a603ad2 | Postgres schema, `Db`, definition store |
| US1 | e2abdb1 | push ingest, mapper, record store + query, source status, v2 API |
| US2 | 081b3d3 | calculated measures (`Expr`), `definitions/temperature.json` / `epidemic.json` |
| US3 | fbfb33a | export/import, dataset reference guard (409, `?force=true`) |
| US4 | 74bac67 | accounts, roles, bearer tokens, dashboard visibility + shares |
| US6 | a8c442b | `examples/collector.py`, `.docs/Collector_Apps.md` |
| Polish | c9fc443 | README, OpenAPI summary test, performance numbers |
| Deps | b0b3581 | version catalog `gradle/libs.versions.toml`, Kotlin 2.4.21, Ktor 3.6.0, Gradle 9.8.2 |
| Cleanup | f345b77 | specs 001-003, v1 contract archive and old notes removed |
| Refactor | c0fa477, 25fea6f, 450e935 | layered packages (controller/service/repository/model/db/di/exception), Flyway, Exposed |
| Tests | dabe96c | Kotest (FunSpec) |

`./gradlew build` green, 47 tests (Kotest, Testcontainers Postgres).

Frontend (`Statistics-UI`, branch `004-universal-stats-platform`): US1 b7d59d0, US2 ce0384f,
US3 156efe4, US4 0a3d3d2, README 9c7679b, deps f4dc599, layered packages + `model/` 79b81a0,
Kotest b8e061e, dead code removed + logic out of screens 7e55873. `jvmTest` (13) green;
js/wasm compile again.

OIDC sign-in (spec 005, branch `005-oidc-login` in both repos):

| Slice | Backend | Frontend | Content |
|-------|---------|----------|---------|
| Setup | 0039e89 | — | java-jwt + jwks-rsa, `AUTH_LOCAL` / `OIDC_*` keys, Authelia dev compose |
| Foundation | 1211cf9 | — | V2 migration (nullable password, oidc issuer/subject), `GET /auth/config`, fake provider for tests |
| US1 | 4db2aa7 | 031b6b2 | start + code exchange, ID-token check, viewer on first sign-in; web redirect flow |
| US2 | f025860 | — | link by verified email, admin via `ADMIN_EMAIL` without `ADMIN_PASSWORD` |
| US3 | f8fbaa9 | c7fd89b | `AUTH_LOCAL=false` → 403 on password routes; Account hides password change |
| US4 | — | bbe632b | desktop loopback sign-in (system browser, Cancel), `jdk.httpserver` module |
| Docs | 498aedd | 4277c10 | README sections, merged into 004 `api-v2.md` |

Backend `./gradlew build` green, 65 tests (18 new in `OidcAuthTest`, offline against an in-test
provider). Frontend `jvmTest` 20 green (callback parser, loopback receiver); js/wasm compile; `jvmJar` builds.

Cleanup (branch `feature-6/cleaning-and-refactoring`, 85f6fbe..a836372): unused Ktor modules and KHealth
dropped (plain `/health` and `/ready` routes, no jitpack repo), dead code removed; still 65 tests green.

## Decisions

- Old v1 API dropped entirely; RPi devices get the new URL + source token (`.docs/Device_Ingest.md`).
- One organisation (`org_id = 1`), built for multi-org. Redis compose removed; the old Redis data
  stays on disk only (`.devops/redis-compose/redis-data`, git-ignored, root-owned).
- No in-process plugins: custom logic lives in collector apps over the ingest API.
- Contract additions during US4: editors read datasets and `/users` (widget builder, share picker);
  `GET /dashboards/{id}/shares`; viewers query only datasets of dashboards they can see.
- Ingest rate limit is a fixed 50 requests/s per source (`ponytail:` note); batches give more records/s.
- Raw queries are capped at 10 000 rows per query, not per series.
- Schema via Flyway (`db/migration/V1__init.sql`, `baselineOnMigrate`: databases from the old
  startup schema are marked V1). SQL through Exposed DSL; analytic measures stay checked SQL text.
- Services own transactions (`db.tx`), repositories run inside them; models only in `model/`.
- Health probes are plain routes (`GET /health`, `GET /ready` → 200 `{}`), no KHealth; its `hide()` wrap
  hid the whole routing root from OpenAPI in Ktor 3.6.
- OIDC: the backend redeems the code and answers the existing opaque session token, so every route,
  expiry and logout stay as they were; IdP tokens are discarded, no refresh tokens, no RP logout.
  PKCE verifier + state are made by the backend and kept by the client (no crypto in KMP).
  ID token: RS256 via JWKS (`ponytail:` ES256 when needed), issuer/audience/expiry checked, no nonce.
  Email from userinfo when the token lacks it. Email/name are not synced after creation.
- `GET /auth/config` omits `oidc` when no provider is set (API-wide `explicitNulls = false`).
- Desktop loopback uses an ephemeral port; Authelia (its oauth2-provider) accepts any port for a
  registered `http://127.0.0.1/callback` (RFC 8252 §7.3).

## Measured

- SC-004 **missed**: 8 parallel 30-day queries on 5 M records, p95 12 s (target 2 s); single query 2.3 s.
- SC-005 met: 480 records/s for 60 s, p95 8 ms.

Details: research.md D4. Not a release gate.

## Next steps

0. OIDC manual checks (spec 005 quickstart §1–§6, T034): `/etc/hosts` entry `127.0.0.1 auth.example.com`,
   generate Authelia secrets (`.devops/authelia-compose/README.md`), then web sign-in as admin/alice,
   role change, cancel at Authelia, `AUTH_LOCAL=false`, provider down, and the desktop browser flow.
1. Manual UI run of quickstart §4/§5 in the desktop app against a live backend (T078). The sign-in,
   prefs, users and shares screens have not been exercised end to end yet.
2. Follow-up spec for query speed (SC-004): hourly pre-aggregation or TimescaleDB continuous aggregates.
3. Desktop app crashes Xorg (RENDER segfault, Intel+NVIDIA PRIME on X11): uncommitted
   `-Dsun.java2d.xrender=false` in Statistics-UI `composeApp/build.gradle.kts` waits for a manual test.
   Kotest on js/wasm needs the `io.kotest` plugin + KSP (tests compile, not discovered yet).
4. Reprogram the RPi devices (quickstart §7): new URL `/api/v2/ingest/{temperature|epidemic}` + header.
5. US5 channels (pull, Kafka, gRPC) only when a real producer exists.
6. Small UI gaps noted by `ponytail:` comments: the dashboard JSON has no owner, so the Edit button
   shows for editors on other people's dashboards (the server answers 403).
