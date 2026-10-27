# Quickstart: Validate OIDC Sign-in

Shapes: [contracts/auth-oidc.md](./contracts/auth-oidc.md). Decisions:
[research.md](./research.md).

## Prerequisites

- 004 prerequisites (Docker, JDK, `curl`, `jq`, Postgres compose up).
- `/etc/hosts`: `127.0.0.1 auth.example.com` (Authelia refuses `localhost`).
- Authelia dev setup per `.devops/authelia-compose/README.md`: generate
  secrets + self-signed cert, `docker compose -f .devops/authelia-compose/compose.yaml up -d`,
  open `https://auth.example.com:9091` once and accept the certificate.
  Users `alice` (alice@example.com) and `admin` (admin@example.com) from
  `users_database.yml`.

```bash
API=http://localhost:8080/api/v2
TRUST="-Djavax.net.ssl.trustStore=$PWD/.devops/authelia-compose/secrets/truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit"
```

## §0 Automated (no Authelia needed)

`./gradlew test` green with Authelia stopped and offline (SC-006): fake-provider
tests cover sign-in, linking, admin bootstrap, broken tokens (SC-004), sign-in
modes; existing auth/ingest/access tests pass unchanged (SC-002).
Frontend: `./gradlew :composeApp:jvmTest` in Statistics-UI.

## §1 No provider — unchanged behaviour (US3)

`ADMIN_EMAIL=admin@local ADMIN_PASSWORD='change-me-now' ./gradlew run`
→ `GET $API/auth/config` = `{"local":true,"oidc":null}`; password sign-in works;
UI shows only the password form.

## §2 Web sign-in through Authelia (US1, US2)

```bash
JAVA_TOOL_OPTIONS="$TRUST" ADMIN_EMAIL=admin@example.com \
  OIDC_ISSUER=https://auth.example.com:9091 OIDC_CLIENT_ID=statistics-ui OIDC_NAME=Authelia \
  ./gradlew run
```

1. `GET $API/auth/config` → `{"local":true,"oidc":{"name":"Authelia"}}`.
2. Web UI (`:composeApp:wasmJsBrowserDevelopmentRun`, port 8081) → "Sign in
   with Authelia" → Authelia login as `admin` → back in the app as **admin**.
3. Private window, sign in as `alice` → **viewer**, sees only shared dashboards.
4. Admin: Users → alice → editor. Alice reloads → can edit dashboards.
5. Alice signs out and in again → same account (no duplicate in Users).
6. Cancel at Authelia → sign-in screen with a reason, no new user.

## §3 Desktop sign-in (US4)

`./gradlew :composeApp:run` → "Sign in with Authelia" → system browser opens,
log in → browser says "you can close this window"; app shows dashboards.
Close the browser tab instead → Cancel in the app returns to sign-in.

## §4 Provider only (US3)

Restart with `AUTH_LOCAL=false` added: UI shows only the provider button;
`POST $API/auth/login` → 403; Account screen has no password section.
`AUTH_LOCAL=false` without `OIDC_ISSUER` → backend refuses to start.

## §5 Devices untouched (FR-012)

In every mode, `POST $API/ingest/<source>` with the source token still → 200;
a user session token on ingest → 401.

## §6 Provider down

`docker compose -f .devops/authelia-compose/compose.yaml stop` → backend keeps
running, password sign-in works, provider button shows "Sign-in provider
unavailable".
