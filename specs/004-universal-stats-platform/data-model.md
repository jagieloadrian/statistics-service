# Data Model: Universal Statistics Platform

Spec entities → storage. Definition document shapes (JSON) are in
[contracts/definition-format.md](./contracts/definition-format.md); this file
covers tables, relations, validation and state.

## Tables (PostgreSQL, `schema.sql`)

```sql
organizations (id smallint PK, name text)                       -- exactly one row in v1 (id = 1)

users (id bigserial PK, org_id smallint FK, email text UNIQUE, name text,
       password_hash text, role text CHECK (role IN ('admin','editor','viewer')),
       prefs jsonb DEFAULT '{}', created_at timestamptz)

auth_tokens (token_hash bytea PK, user_id bigint FK ON DELETE CASCADE,
             created_at timestamptz, last_seen timestamptz)

definitions (org_id smallint, kind text CHECK (kind IN ('dataset','source','dashboard')),
             id text, body jsonb, owner_id bigint NULL, updated_at timestamptz,
             PRIMARY KEY (org_id, kind, id))

dashboard_shares (org_id smallint, dashboard_id text, user_id bigint,
                  PRIMARY KEY (org_id, dashboard_id, user_id))

source_credentials (org_id smallint, source_id text, token_hash bytea,
                    created_at timestamptz, PRIMARY KEY (org_id, source_id))

records (org_id smallint, dataset_id text, ts timestamptz, dims jsonb, vals jsonb,
         ident text NULL, ts_estimated boolean DEFAULT false)
  INDEX records_range (org_id, dataset_id, ts)
  UNIQUE INDEX records_ident (org_id, dataset_id, ident) WHERE ident IS NOT NULL
```

Every owned row carries `org_id` (FR-020); v1 code always passes `1`.

## Entities

### Organization
Single row in v1. Future multi-org adds rows + `org_id` from the signed-in
user instead of the constant — no change to definitions or exports.

### User
- `role` drives the permission matrix (spec US4 scenario 2):

| Action | admin | editor | viewer |
|--------|:-----:|:------:|:------:|
| Users, sources, datasets (create/edit/delete), source status, import | ✓ | | |
| Dashboards: create, edit own, share own; export | ✓ | ✓ | |
| View dashboards: private-own / shared-with-me / shared-with-all | ✓ (all) | ✓ | ✓ (shared only) |
| Query + dimension values (only datasets used by visible dashboards for viewers) | ✓ | ✓ | ✓ |
| Own prefs, favourites, home dashboard | ✓ | ✓ | ✓ |

- `prefs` = `{timeZone, units: "metric"|"imperial", theme: "system"|"light"|"dark", homeDashboard, favourites: [ids]}`.
- Validation: email unique, password ≥ 10 chars, last remaining admin cannot be demoted/deleted.

### Auth token
Opaque, SHA-256 hashed. Valid while `now - last_seen < 7 days`; `last_seen`
updated at most once per minute. Sign-out deletes the row.

### Dataset (`definitions.kind = 'dataset'`)
Declares measures (name, type number|boolean|text, unit, label),
dimensions (name, label), calculated measures (name → expression),
`retentionDays` (null = forever).
- Names match `^[A-Za-z_][A-Za-z0-9_]{0,62}$`, unique across measures +
  dimensions + calculated within a dataset; `ts` is reserved.
- Expressions may reference only numeric measures of the same dataset.
- Renaming/removing a measure or dimension: API returns `409` listing the
  referencing widgets unless `?force=true` (spec edge case).

### Source (`definitions.kind = 'source'`)
Channel (`push`; later `pull`, `kafka`, `grpc`), channel settings, rate
limit, target dataset, mapping (empty = native record format, FR-025),
`paused`. Credential lives in `source_credentials`, never in the document.
- Target dataset must exist; mapping outputs only declared names.
- A source id is also its push address: `/api/v2/ingest/{id}`.

State (stored `paused` + derived from in-memory status):

```text
            create
              │
              ▼
   ┌──── waiting-for-data ──first record──► receiving ◄──┐
   │                                          │          │ record
 pause                              no record │          │
   │                              for silentAfter       │
   ▼                                          ▼          │
 paused ◄────────── pause ────────────── silent ─────────┘
   │ resume → waiting-for-data / receiving
   └ (any state) last N attempts all rejected → erroring (cleared by next accepted record)
```
Paused push sources answer `503` with `Retry-After`.

### Mapping (embedded in Source)
Ordered list of record rules; one payload → 1..N records. Each rule:
optional `each` (object/array path), `timestamp` (path + format), `dims`,
`vals`, `const`, `identity`. Constraint failure on any rule rejects the whole
payload (atomic insert of all its records).

### Record (`records`)
`ts` UTC; `ts_estimated = true` when payload had no timestamp (arrival time
used). `ident` = `identity` values joined with `|` (plus `@key` for `each`
rules); null when the source declares no identity (stored as received).

### Dashboard (`definitions.kind = 'dashboard'`)
Widgets (ordered, `w` 1–12, `h` rows), variables, default time range,
`refreshSeconds` (null or ≥ 5), `visibility`
(`private`|`users`|`all`); `owner_id` column = creator; shares in
`dashboard_shares` (only used when `visibility = 'users'`).
- Widget query must reference an existing dataset at save time; at view time
  a missing dataset/measure renders the "missing data definition" state
  instead of failing the dashboard (spec US3 scenario 4).

### Source status (in memory, not a table)
Per source: accepted/rejected per minute (60 buckets) and per hour (24),
`lastRecordAt`, last 50 rejections `{at, reason, excerpt ≤ 500 chars}`.
Resets on restart (research D10).

## Retention

Hourly coroutine: for each dataset with `retentionDays`,
`DELETE FROM records WHERE org_id = ? AND dataset_id = ? AND ts < now() - make_interval(days => ?)`.
