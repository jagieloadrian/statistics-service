# Contract: Platform API v2

Base path `/api/v2`, JSON only, documented in OpenAPI (existing Swagger
route). Definition bodies: [definition-format.md](./definition-format.md).

**Auth**: user calls send `Authorization: Bearer <user token>` (from
login); ingest calls send `Authorization: Bearer <source token>`. Every
`/api/v2` route except `POST /auth/login` and `POST /ingest/{sourceId}` needs
a user token.

**Errors** (all endpoints): `{"error": "<code>", "message": "<human text>", "details": [ … ]}`
with 400 validation, 401 no/invalid token, 403 role/visibility, 404 unknown
id, 409 conflict, 429 rate limited (+ `Retry-After`), 503 source paused.

## Ingest

### `POST /ingest/{sourceId}` — source token

Body: payload matching the source mapping, or native record(s) when the
source has no mapping.

| Result | Status | Body |
|--------|--------|------|
| stored (all records of the payload committed) | 200 | `{"accepted": 4, "duplicates": 0}` |
| constraint failure (nothing stored) | 400 | `details`: one reason per failed constraint |
| missing/invalid/rotated token | 401 | — |
| unknown source | 404 | — |
| over `rateLimitPerSecond` | 429 | `Retry-After` header |
| source paused | 503 | `Retry-After` header |
| source has channel `pubsub` | 409 | — |

Rejections (400) are also recorded in source status.

### Pub/sub

Sources with `"channel": {"type": "pubsub"}` take messages from the one broker
the app is configured with (`BROKER=kafka|jetstream`; `none` = no consumer).

| | Kafka | NATS JetStream |
|-|-------|----------------|
| Address | topic `KAFKA_TOPIC` (default `stats.ingest`), record key = source id | subject `<NATS_SUBJECT_PREFIX>.<sourceId>` (default `stats.ingest.<sourceId>`) in stream `NATS_STREAM` (default `STATS_INGEST`, subjects `<prefix>.>`) |
| Consumer | group `KAFKA_GROUP_ID` (default `statistics-service`), starts at the earliest offset | durable pull consumer `NATS_DURABLE` (default `statistics-service`), explicit ack; the app creates or updates the stream (file storage, `NATS_STREAM_MAX_AGE` default 7d, `NATS_STREAM_MAX_BYTES` default 256 MiB) |
| Header | `Authorization: Bearer <source token>` | `Authorization: Bearer <source token>` |
| Value | UTF-8 body exactly as for `POST /ingest/{sourceId}` | same |

Delivery is at least once. A message is acked (JetStream) or its offset
committed (Kafka) only after its records are stored; a redelivered message is
deduplicated by the source's `identity` (native records without identity are
stored again). Messages that can never be stored — unknown source, missing or
wrong token, `push` source, paused source, mapping or JSON errors — are acked,
counted and logged (without payload or token); when the source exists they are
recorded in its status like a 400. Storage failures (database down) are not
acked: the consumer backs off (1 s doubling to 30 s) and the broker redelivers.
No per-source rate limit applies; the consumer reads at its own pace.
Readiness does not depend on the broker; `/metrics` exposes
`statistics_broker_consuming`, `statistics_broker_pending_messages` and
`statistics_broker_messages_total{outcome="stored|rejected|retried"}`.

## Query

### `POST /query` — any role (viewer: only datasets used by dashboards visible to them)

```json
{
  "dataset": "temperature",
  "measures": [ { "field": "temperature", "agg": "avg", "as": "temp" },
                { "field": "humidity", "agg": "max" } ],
  "filter": { "device": ["pico-1", "pico-2"] },
  "groupBy": ["device"],
  "from": "2026-10-26T00:00:00Z", "to": "2026-10-27T00:00:00Z",
  "bucket": "auto", "maxPoints": 1000
}
```

| Field | Rules |
|-------|-------|
| `measures[].field` | declared measure, calculated measure, or `ts` (min/max/first/last only) |
| `measures[].agg` | `count`, `sum`, `avg`, `min`, `max`, `first`, `last`, `at_max`, `at_min` (+ `by`: measure whose max/min selects the row). Omitted with `bucket: "raw"` = plain value; otherwise default `avg` (number), `last` (boolean/text). |
| `as` | column name; default `<field>_<agg>` or `<field>` |
| `filter` | dimension → allowed values (OR within, AND across) |
| `groupBy` | dimensions; one series per distinct combination |
| `from`/`to` | ISO-8601 instants; or `"range": {"last": "P1D"}` (relative to now). Widget definitions never store these — the frontend fills them from the dashboard time range |
| `bucket` | `raw` (no aggregation), `none` (one row per series), `auto`, or `1m` `5m` `10m` `30m` `1h` `6h` `1d`; buckets aligned to UTC |
| `maxPoints` | used by `auto` (default 1000); `raw` returns at most 10 000 rows per query (cut → `truncated: true`) |

Response:

```json
{
  "bucket": "5m",
  "series": [
    { "dims": { "device": "pico-1" },
      "columns": ["ts", "temp", "humidity_max"],
      "rows": [ ["2026-10-26T00:00:00Z", 21.3, 40.1], … ] }
  ],
  "truncated": false
}
```

`ts` column is the bucket start (absent for `bucket: "none"`). Unknown
dataset/field/dimension → 400 naming it.

### `GET /datasets/{id}/dimensions/{dim}/values?search=&limit=50&offset=0`

`[{"value": "5", "firstSeen": "…", "lastSeen": "…", "count": 132}]`,
ordered by `lastSeen` desc.

## Definitions

| Method + path | Role | Notes |
|---------------|------|-------|
| `GET /datasets`, `/sources`, `/dashboards` | dashboards: all roles (filtered by visibility); datasets: admin, editor (to build widgets); sources: admin | list `{id, label/title, updatedAt}` |
| `GET /{kind}/{id}` | as above; a viewer may get a dataset used by a dashboard visible to them | full document |
| `PUT /{kind}/{id}` | datasets, sources: admin; dashboards: owner editor or admin | upsert; validated against format + references; dataset change removing/renaming a referenced name → 409 with `details` = widget refs, unless `?force=true` |
| `DELETE /{kind}/{id}` | same as PUT | dataset with sources/widgets referencing it → 409 unless `?force=true`; records of a deleted dataset are deleted |
| `POST /sources/{id}/credential` | admin | rotate; returns `{"token": "…"}` once, previous token invalid immediately |
| `GET /sources/{id}/status` | admin | `{state, lastRecordAt, accepted:{hour,day}, rejected:{hour,day}, rejections:[{at, reason, excerpt}]}` |
| `POST /sources/{id}/mapping-test` | admin | body `{payload, mapping?}` → `{records:[…], errors:[…]}`; stores nothing |
| `GET`/`PUT /dashboards/{id}/shares` | owner editor or admin | `{"userIds": [ … ]}` (used when `visibility = "users"`); dashboards without owner (imported) are admin-only |
| `GET /export` | admin (everything), editor (dashboards visible to them + the datasets they use, no sources) | bundle |
| `POST /import` | admin | bundle → 200 `{"created": n, "updated": m, "errors": []}`; any invalid document → 400 with the same shape, `errors` listing every problem, nothing stored |

## Auth & user panel

| Method + path | Role | Notes |
|---------------|------|-------|
| `GET /auth/config` | public | enabled sign-in methods: `{"local": true, "oidc": {"name": "Authelia"}, "pubsub": {"broker": "kafka", "address": "stats.ingest"}}`; `oidc` omitted (null) when no provider is configured; `pubsub` omitted when `BROKER=none`, else `broker` is `kafka` (`address` = topic) or `jetstream` (`address` = subject prefix), used by the source editor to show where a `pubsub` source's collector publishes. No secrets, no URLs |
| `POST /auth/login` | public | `{email, password}` → `{token, user}`; 401 with one generic message; 5 attempts/min per IP; local sign-in off (`AUTH_LOCAL=false`) → 403 |
| `POST /auth/oidc/start` | public | `{redirectUri}` → `{authorizationUrl, state, codeVerifier}`; the client keeps `state` + `codeVerifier` and sends the user to `authorizationUrl` (provider `authorization_endpoint` with `response_type=code`, `client_id`, `redirect_uri`, `scope=openid email profile`, `state`, `code_challenge`, `code_challenge_method=S256`); no provider → 404; provider unreachable → 503 (`unavailable`); shares the login limit |
| `POST /auth/oidc` | public | `{code, codeVerifier, redirectUri}` → `{token, user}` like `/auth/login`. Known provider identity → that account; else the account with the same email is linked if the provider verified the email and it has no other identity; else a new account (admin when the verified email is `ADMIN_EMAIL`, otherwise viewer; no password). Code refused, invalid/expired/foreign ID token, no email, unverified email of an existing account, account linked to another identity → 401; no provider → 404; provider unreachable → 503; shares the login limit |
| `POST /auth/logout` | signed in | deletes current token → 204 |
| `GET /me` | signed in | `{id, email, name, role, prefs, hasPassword}`; `hasPassword` false for provider-only accounts |
| `PUT /me/prefs` | signed in | `{timeZone, units, theme, accent, homeDashboard, favourites}` |
| `PUT /me/password` | signed in | `{current, new}` → 204; wrong `current` → 400; other tokens of the user revoked; local sign-in off or no password → 403 |
| `GET /users` | admin, editor (share picker) | `[{id, email, name, role, prefs, hasPassword}]` |
| `POST /users` | admin | `{email, name?, role, password}` → 201 user; create = invite with initial password (no email sending); password < 10 chars → 400 (also on `PUT /me/password`); email in use → 409 |
| `PUT /users/{id}`, `DELETE /users/{id}` | admin | `{name?, role?}`; delete → 204; last admin cannot be demoted or deleted (409) |
