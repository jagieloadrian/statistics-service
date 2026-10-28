# StatisticsService

Backend of the universal statistics platform (spec
[004](specs/004-universal-stats-platform/spec.md)): sources push data, mappings
turn it into records of a dataset, dashboards query them. Everything domain
specific is configuration (JSON definitions), not code. Frontend: the
`Statistics-UI` repo.

## Run

```bash
docker compose -f .devops/postgres-compose/compose.yaml up -d   # PostgreSQL 16, db/user/password "stats"
ADMIN_EMAIL=admin@local ADMIN_PASSWORD='change-me-now' ./gradlew run
```

| Env | Default | Meaning |
|-----|---------|---------|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/stats`, `stats`, `stats` | database; the schema is created on start |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | empty | first admin, created only while there are no users; see sign-in methods |
| `CORS_HOSTS` | `localhost:8081` | comma-separated frontend hosts |
| `TRUST_PROXY` | `false` | `true` behind a reverse proxy (e.g. the Istio gateway): the login rate limit keys on the last `X-Forwarded-For` entry; keep `false` without one, clients could spoof it |
| `LOG_FORMAT` | `json` | `plain` for human-readable local logs |

API: `http://localhost:8080/api/v2`, Swagger UI at `/api/v2/swagger`.

## Sign-in methods / OIDC

Users sign in with a password, through one OpenID Connect provider, or both.
Either way they get the same session token; roles stay managed in the app, and
devices keep using source tokens.

| Env | Default | Meaning |
|-----|---------|---------|
| `AUTH_LOCAL` | `true` | password sign-in; `false` = provider only (start fails without a provider) |
| `OIDC_ISSUER` | empty | provider issuer URL, e.g. `https://auth.example.com:9091`; provider on when issuer and client id are set |
| `OIDC_CLIENT_ID` | empty | client registered at the provider |
| `OIDC_CLIENT_SECRET` | empty | only for confidential clients (sent as HTTP Basic) |
| `OIDC_NAME` | `SSO` | button label: "Sign in with …" |

- Register the client with PKCE (S256) and the redirect URIs of the frontends:
  the web UI's origin + `/` (dev: `http://localhost:8081/`) and
  `http://127.0.0.1/callback` for the desktop app (any port).
- First provider sign-in creates a viewer. An existing account with the same
  email is linked only if the provider marks the email verified.
- `ADMIN_EMAIL` without `ADMIN_PASSWORD`: no local admin; the first sign-in
  through the provider with that (verified) email becomes admin.
- A self-signed provider certificate needs a JVM truststore
  (`-Djavax.net.ssl.trustStore=…`).
- Dev provider: [.devops/authelia-compose/README.md](.devops/authelia-compose/README.md).

## Load the example definitions

```bash
API=http://localhost:8080/api/v2
TOKEN=$(curl -s $API/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@local","password":"change-me-now"}' | jq -r .token)
for f in definitions/*.json; do
  curl -s -X POST $API/import -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d @$f; echo
done
curl -s -X POST $API/sources/temperature/credential -H "Authorization: Bearer $TOKEN"   # source token for the device
```

Devices and collector apps: [.docs/Device_Ingest.md](.docs/Device_Ingest.md),
[.docs/Collector_Apps.md](.docs/Collector_Apps.md).

## Kubernetes (homelab platform)

Deployed per the homelab platform contract ("Homelab Platform Contract —
wymagania dla aplikacji"). [.devops/k8s/](.devops/k8s/) (kustomize, namespace
`apps`) is read directly by Flux from `main` of this repo (registered in
homelab-infra `clusters/home/apps/statistics-service.yaml`); CI rewrites the
`image:` tag there on every push to `main`.

- Image: `ghcr.io/jagieloadrian/statistics-service`, built by Jib
  (linux/amd64 + linux/arm64, eclipse-temurin:21-jre, user 10001). CI pushes
  `main-<yyyyMMddHHmmss>-<sha7>` from `main` and the semver version from `v*`
  tags, never `latest`; only `main` builds are deployed automatically.
- Route: HTTPRoute on `home-gateway`, host `statistics.home.arpa`, path
  `/api/`; everything else belongs to `statistics-ui` (same origin).
- Callers: `home-gateway` only (AuthorizationPolicy admits
  `istio-ingress/home-gateway-istio`). Callees: Postgres (platform-managed)
  and the OIDC provider.
- Backup: the service is stateless; the database is backed up by the platform.
- One replica: login rate limits and source status are kept in memory.

ConfigMap `statistics-service-config` ([configmap.yaml](.devops/k8s/configmap.yaml)):
`DB_URL`, `DB_USER`, `ADMIN_EMAIL`, `AUTH_LOCAL`, `OIDC_ISSUER`,
`OIDC_CLIENT_ID`, `OIDC_NAME`, `CORS_HOSTS`, `TRUST_PROXY`, `LOG_FORMAT`.
Secret `statistics-service-secrets` (SOPS/age-encrypted in
`.devops/k8s/secrets.sops.yaml`, decrypted by Flux in the cluster):
`DB_PASSWORD`, `ADMIN_PASSWORD`, `OIDC_CLIENT_SECRET`.

- Database: role and schema `statistics` in database `statistics` on the
  Postgres running on the Wyse host, reached as
  `postgres.apps.svc.cluster.local:5432` (homelab-infra `docs/postgres.md`).
- Deploy: a push to `main` builds the image and CI commits the new tag to
  `.devops/k8s/`; Flux (homelab-infra
  `clusters/home/apps/statistics-service.yaml`) reads that directory and rolls
  it out.

- OIDC redirect URI for the web UI: `http://statistics.home.arpa/`. The
  gateway serves plain HTTP, so the provider must accept an `http` redirect
  URI for this host.
- `TRUST_PROXY=true`: the login rate limit keys on the client IP the gateway
  appends to `X-Forwarded-For`.

### Operations

| Endpoint / env | Meaning |
|----------------|---------|
| `GET /health/live` | 200 while the process runs; touches no dependency |
| `GET /health/ready` | 200 when the database answers, 503 otherwise |
| `GET /metrics` | Prometheus metrics (not under `/api/`, so not routed by the gateway) |
| `LOG_FORMAT` | `json` (default): one JSON object per line with `timestamp`, `level`, `message`, `logger`, `traceId`; `plain` for local reading |

- SIGTERM: stops taking new calls, waits up to 15 s for in-flight calls and
  exits 0; `preStop` sleeps 3 s, `terminationGracePeriodSeconds` is 30.
- Outbound OIDC calls: connect timeout 2 s, request timeout 10 s, W3C
  `traceparent` propagated; `traceId` comes from the incoming `traceparent`.
- Invalid configuration (e.g. `DB_URL` not `jdbc:postgresql://`, `AUTH_LOCAL`
  not a boolean, only one of `OIDC_ISSUER`/`OIDC_CLIENT_ID`) logs one JSON
  error line and exits 1.

### Definition of Ready

- [x] Multi-arch image via Jib, non-root 10001, no secrets in image — verified
  with `docker buildx imagetools inspect` and a local registry
- [x] Probes, `/metrics`, JSON logs, SIGTERM exit 0 with drain — verified
  against the image (read-only root filesystem, 384 Mi limit)
- [x] Readiness 503 while Postgres is down, 200 after it returns — verified
- [x] Fail-fast config, single JSON error line, exit 1 — verified
- [x] `kustomize build` (v5.4.3) + `kubeconform -strict` incl. HTTPRoute,
  AuthorizationPolicy and Flux CRDs — 8/8 valid; `istioctl validate` — valid
- [x] CI workflow — `actionlint` clean
- [x] `DB_URL` points at the platform Postgres (`postgres.apps.svc.cluster.local`)
- [ ] Role + schema `statistics` created on the Wyse Postgres
- [ ] Secret `statistics-service-secrets` in `.devops/k8s/secrets.sops.yaml` (SOPS)
- [ ] OIDC client registered with redirect URI `http://statistics.home.arpa/`
- [x] Registered in homelab-infra (`clusters/home/apps/statistics-service.yaml`)

## Build & test

`./gradlew check` runs ktlint and all tests (warnings are errors); tests start
PostgreSQL through Testcontainers, so Docker must be running.
`./gradlew jibDockerBuild` builds the image into the local Docker daemon.

## Docs

- Spec, plan, contracts: [specs/004-universal-stats-platform/](specs/004-universal-stats-platform/)
- API contract: [contracts/api-v2.md](specs/004-universal-stats-platform/contracts/api-v2.md)
- Definition format: [contracts/definition-format.md](specs/004-universal-stats-platform/contracts/definition-format.md)
