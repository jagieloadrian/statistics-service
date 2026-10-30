# StatisticsService

Backend of the universal statistics platform (spec
[004](specs/004-universal-stats-platform/spec.md)): sources push data, mappings
turn it into records of a dataset, dashboards query them. Everything domain
specific is configuration (JSON definitions), not code. Frontend: the
`Statistics-UI` repo.

## Run locally

```bash
docker compose -f .devops/postgres-compose/compose.yaml up -d   # PostgreSQL 16, db/user/password "stats"
./gradlew runLocal
```

`runLocal` is the local profile: it starts the app with
`-config=application.yaml -config=.devops/application-local.yaml` (Ktor merges the files, the later one wins)
and `LOG_FORMAT=plain`. [.devops/application-local.yaml](.devops/application-local.yaml) turns on password
sign-in only (no OIDC), points at the compose database, allows the web dev server
(`localhost:8081`, `127.0.0.1:8081`), keeps `TRUST_PROXY` off and creates the dev admin `admin@local` /
`change-me-now` (only while there are no users). The file is outside `src/main/resources`, so the image
never contains it. Stop with Ctrl+C, then `docker compose -f .devops/postgres-compose/compose.yaml stop`.
Web UI: `Statistics-UI` README, "Run locally".

## Configuration

`./gradlew run` and the image read the environment:

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

## Pub/sub ingestion (Kafka / NATS JetStream)

Besides HTTP push, sources with `"channel": {"type": "pubsub"}` take messages
from one broker chosen by `BROKER`; switching the broker changes no definition.
Message contract: [api-v2.md, Pub/sub](specs/004-universal-stats-platform/contracts/api-v2.md#pubsub);
for collectors: [.docs/Collector_Apps.md](.docs/Collector_Apps.md).

| Env | Default | Meaning |
|-----|---------|---------|
| `BROKER` | `none` | `none`, `kafka` or `jetstream`; anything else stops the start |
| `KAFKA_BOOTSTRAP_SERVERS` | empty | required with `kafka`, e.g. `kafka:9092` |
| `KAFKA_TOPIC` | `stats.ingest` | the one topic; record key = source id |
| `KAFKA_GROUP_ID` | `statistics-service` | consumer group (starts at the earliest offset) |
| `KAFKA_SECURITY_PROTOCOL` | `PLAINTEXT` | or `SSL`, `SASL_PLAINTEXT`, `SASL_SSL` |
| `KAFKA_SASL_MECHANISM` | `PLAIN` | `PLAIN`, `SCRAM-SHA-256` or `SCRAM-SHA-512`, used with `SASL_*` |
| `KAFKA_USER`, `KAFKA_PASSWORD` | empty | required with `SASL_*`, not allowed without |
| `NATS_URL` | empty | required with `jetstream`, `nats://…` or `tls://…` |
| `NATS_STREAM` | `STATS_INGEST` | created or updated on every start: subjects `<prefix>.>`, file storage |
| `NATS_SUBJECT_PREFIX` | `stats.ingest` | subject = `<prefix>.<sourceId>` |
| `NATS_DURABLE` | `statistics-service` | durable pull consumer, explicit ack |
| `NATS_STREAM_MAX_AGE`, `NATS_STREAM_MAX_BYTES` | `7d`, `268435456` | stream limits; the oldest messages go first |
| `NATS_USER` + `NATS_PASSWORD`, or `NATS_TOKEN` | empty | broker credentials |

- At least once: a message is acked (JetStream) or committed (Kafka) only after
  its records are stored. Messages that can never be stored (bad token, unknown
  or `push` source, paused source, mapping errors) are acked, counted and shown
  in the source status; database failures are retried with back-off (1 s → 30 s).
- The consumer starts with the app, keeps retrying while the broker is down and
  never affects `/health/ready`. `/metrics`: `statistics_broker_consuming`,
  `statistics_broker_pending_messages`,
  `statistics_broker_messages_total{outcome="stored|rejected|retried"}`.
- HTTP push to a `pubsub` source answers 409.

Locally (with Postgres already up as above), pick one broker:

```bash
docker compose -f .devops/kafka-compose/compose.yaml up -d   # apache/kafka 4.4.0, KRaft, localhost:9092
BROKER=kafka KAFKA_BOOTSTRAP_SERVERS=localhost:9092 ./gradlew runLocal

docker compose -f .devops/nats-compose/compose.yaml up -d    # nats 2.15.2 with JetStream, localhost:4222
BROKER=jetstream NATS_URL=nats://localhost:4222 ./gradlew runLocal
```

With the example definitions loaded (`API`, `TOKEN` as above), switch
`temperature` to pub/sub and send one reading:

```bash
curl -s $API/sources/temperature -H "Authorization: Bearer $TOKEN" | jq '.channel.type = "pubsub"' |
  curl -s -X PUT $API/sources/temperature -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d @-
SOURCE_TOKEN=$(curl -s -X POST $API/sources/temperature/credential -H "Authorization: Bearer $TOKEN" | jq -r .token)
PAYLOAD="{\"deviceId\":\"pico-1\",\"timestamp\":\"$(date -u +%FT%TZ)\",\"temperature\":21.5,\"humidity\":40}"

# Kafka: key = source id, header Authorization (tab-separated: headers, key, value)
printf 'Authorization:Bearer %s\ttemperature\t%s\n' "$SOURCE_TOKEN" "$PAYLOAD" |
  docker compose -f .devops/kafka-compose/compose.yaml exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server localhost:9092 --topic stats.ingest --reader-property parse.key=true --reader-property parse.headers=true

# NATS: subject stats.ingest.<source id>, header Authorization
docker run --rm --network host natsio/nats-box:0.20.1 \
  nats pub -s nats://localhost:4222 stats.ingest.temperature "$PAYLOAD" -H "Authorization:Bearer $SOURCE_TOKEN"

curl -s $API/sources/temperature/status -H "Authorization: Bearer $TOKEN"   # accepted.hour = 1
```

Stop with `docker compose -f .devops/<kafka|nats>-compose/compose.yaml down` (broker data is not kept).

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
  `istio-ingress/home-gateway-istio`). Callees: Postgres (platform-managed),
  the OIDC provider and, when `BROKER` is not `none`, the broker:
  NATS `nats.apps.svc.cluster.local:4222` (user `statistics-service`: `$JS.API.>`,
  `$JS.ACK.>`, subscribe `stats.ingest.>` and `_INBOX.>`) or Kafka. Collectors
  publish to the broker, not to this service.
- Backup: the service is stateless; the database is backed up by the platform.
- One replica: login rate limits and source status are kept in memory.

Environment comes from two dotenv files turned into a ConfigMap and a Secret by
kustomize generators (a change rolls the Deployment):

- [config.env](.devops/k8s/config.env), plain → ConfigMap
  `statistics-service-config`: `DB_URL`, `DB_USER`, `ADMIN_EMAIL`,
  `AUTH_LOCAL`, `OIDC_ISSUER`, `OIDC_CLIENT_ID`, `OIDC_NAME`, `CORS_HOSTS`,
  `TRUST_PROXY`, `LOG_FORMAT`, `BROKER` (`none`), `KAFKA_*` (no password) and
  `NATS_*` (no password/token; `NATS_URL` already points at the in-cluster NATS).
  Turning on JetStream = `BROKER=jetstream` here + `NATS_PASSWORD` in the secret.
- `.devops/k8s/secrets.sops.env`, SOPS/age-encrypted, decrypted by Flux →
  Secret `statistics-service-secrets`: `DB_PASSWORD`, `ADMIN_PASSWORD`,
  `OIDC_CLIENT_SECRET`, and with a broker `NATS_PASSWORD` (or `NATS_TOKEN`) or
  `KAFKA_PASSWORD`. Create/edit only with `sops .devops/k8s/secrets.sops.env`
  (plaintext never touches the disk); the age key is in [.sops.yaml](.sops.yaml).

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
| `GET /health/ready` | 200 when the database answers, 503 otherwise; the broker is never checked |
| `GET /metrics` | Prometheus metrics (not under `/api/`, so not routed by the gateway), incl. `statistics_broker_*` |
| `LOG_FORMAT` | `json` (default): one JSON object per line with `timestamp`, `level`, `message`, `logger`, `traceId`; `plain` for local reading |

- SIGTERM: waits up to 8 s for in-flight calls, closes Netty (`shutdownTimeout`
  2 s), gives the broker consumer 3 s to finish its message and ack/commit,
  then closes the database pool and exits 0: at most ~17 s, measured 12.1 s
  with a query and a broker message both blocked on a table lock (unfinished
  messages are redelivered). `preStop` sleeps 3 s, `terminationGracePeriodSeconds` is 30.
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
- [ ] Secret `statistics-service-secrets` in `.devops/k8s/secrets.sops.env` (SOPS)
- [ ] OIDC client registered with redirect URI `http://statistics.home.arpa/`
- [x] Registered in homelab-infra (`clusters/home/apps/statistics-service.yaml`)
- [ ] Pub/sub (optional): `BROKER=jetstream` in config.env, `NATS_PASSWORD` in
  the secret (not digits-only), NATS AuthorizationPolicy admits
  `cluster.local/ns/apps/sa/statistics-service`

## Build & test

`./gradlew check` runs ktlint and all tests (warnings are errors); tests start
PostgreSQL, Kafka and NATS through Testcontainers, so Docker must be running.
`./gradlew jibDockerBuild` builds the image into the local Docker daemon.

## Docs

- Spec, plan, contracts: [specs/004-universal-stats-platform/](specs/004-universal-stats-platform/)
- API contract: [contracts/api-v2.md](specs/004-universal-stats-platform/contracts/api-v2.md)
- Definition format: [contracts/definition-format.md](specs/004-universal-stats-platform/contracts/definition-format.md)
