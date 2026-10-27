CREATE TABLE IF NOT EXISTS organizations (
    id   smallint PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id            bigserial PRIMARY KEY,
    org_id        smallint    NOT NULL REFERENCES organizations (id),
    email         text        NOT NULL UNIQUE,
    name          text        NOT NULL DEFAULT '',
    password_hash text        NOT NULL,
    role          text        NOT NULL CHECK (role IN ('admin', 'editor', 'viewer')),
    prefs         jsonb       NOT NULL DEFAULT '{}',
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS auth_tokens (
    token_hash bytea PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    last_seen  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS definitions (
    org_id     smallint    NOT NULL REFERENCES organizations (id),
    kind       text        NOT NULL CHECK (kind IN ('dataset', 'source', 'dashboard')),
    id         text        NOT NULL,
    body       jsonb       NOT NULL,
    owner_id   bigint      REFERENCES users (id) ON DELETE SET NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, kind, id)
);

CREATE TABLE IF NOT EXISTS dashboard_shares (
    org_id       smallint NOT NULL,
    dashboard_id text     NOT NULL,
    user_id      bigint   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (org_id, dashboard_id, user_id)
);

CREATE TABLE IF NOT EXISTS source_credentials (
    org_id     smallint    NOT NULL,
    source_id  text        NOT NULL,
    token_hash bytea       NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (org_id, source_id)
);

CREATE TABLE IF NOT EXISTS records (
    org_id       smallint    NOT NULL,
    dataset_id   text        NOT NULL,
    ts           timestamptz NOT NULL,
    dims         jsonb       NOT NULL,
    vals         jsonb       NOT NULL,
    ident        text,
    ts_estimated boolean     NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS records_range ON records (org_id, dataset_id, ts);
CREATE UNIQUE INDEX IF NOT EXISTS records_ident ON records (org_id, dataset_id, ident) WHERE ident IS NOT NULL;

INSERT INTO organizations VALUES (1, 'default') ON CONFLICT DO NOTHING;
