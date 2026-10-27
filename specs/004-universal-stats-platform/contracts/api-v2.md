# Contract: Platform API v2

Base path `/api/v2`, JSON only, documented in OpenAPI (existing Swagger
route). Definition bodies: [definition-format.md](./definition-format.md).

**Auth**: user calls send `Authorization: Bearer <user token>` (from
login); ingest calls send `Authorization: Bearer <source token>`. Until slice
S5 user auth is not enforced (LAN only).

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

Rejections (400) are also recorded in source status.

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
| `maxPoints` | used by `auto` (default 1000); `raw` returns at most 10 000 rows per series |

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
| `GET /datasets`, `/sources`, `/dashboards` | dashboards: all roles (filtered by visibility); others: admin | list `{id, label/title, updatedAt}` |
| `GET /{kind}/{id}` | as above | full document |
| `PUT /{kind}/{id}` | datasets, sources: admin; dashboards: owner editor or admin | upsert; validated against format + references; dataset change removing/renaming a referenced name → 409 with `details` = widget refs, unless `?force=true` |
| `DELETE /{kind}/{id}` | same as PUT | dataset with sources/widgets referencing it → 409 unless `?force=true`; records of a deleted dataset are deleted |
| `POST /sources/{id}/credential` | admin | rotate; returns `{"token": "…"}` once, previous token invalid immediately |
| `GET /sources/{id}/status` | admin | `{state, lastRecordAt, accepted:{hour,day}, rejected:{hour,day}, rejections:[{at, reason, excerpt}]}` |
| `POST /sources/{id}/mapping-test` | admin | body `{payload, mapping?}` → `{records:[…], errors:[…]}`; stores nothing |
| `PUT /dashboards/{id}/shares` | owner editor or admin | `{"userIds": [ … ]}` (used when `visibility = "users"`) |
| `GET /export` | admin, editor (own + shared dashboards only) | bundle |
| `POST /import` | admin | bundle → `{"created": n, "updated": m, "errors": [ … ]}`; all-or-nothing |

## Auth & user panel

| Method + path | Role | Notes |
|---------------|------|-------|
| `POST /auth/login` | public | `{email, password}` → `{token, user}`; 401 with one generic message; 5 attempts/min per IP |
| `POST /auth/logout` | signed in | deletes current token |
| `GET /me` | signed in | `{id, email, name, role, prefs}` |
| `PUT /me/prefs` | signed in | `{timeZone, units, theme, homeDashboard, favourites}` |
| `PUT /me/password` | signed in | `{current, new}`; other tokens of the user revoked |
| `GET /users`, `POST /users` | admin | create = invite with initial password (no email sending); password < 10 chars → 400 (also on `PUT /me/password`) |
| `PUT /users/{id}`, `DELETE /users/{id}` | admin | role/name; last admin cannot be demoted or deleted (409) |
