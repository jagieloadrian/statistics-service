# Data Model: OIDC Sign-in

Delta to [004 data-model](../004-universal-stats-platform/data-model.md).
One Flyway migration, no new tables.

## `users` (changed) — `V2__oidc.sql`

| Column | Change | Rule |
|--------|--------|------|
| `password_hash` | `DROP NOT NULL` | null = provider-only user; password sign-in and password change refused (`Passwords.verify(_, null)` is already false) |
| `oidc_issuer` | NEW `text NULL` | issuer the identity came from |
| `oidc_subject` | NEW `text NULL` | provider `sub`; never reused for another user |
| — | NEW `UNIQUE (oidc_issuer, oidc_subject)` | one platform account per provider identity (FR-004); NULLs don't collide |
| — | NEW `CHECK (password_hash IS NOT NULL OR oidc_subject IS NOT NULL)` | every account can sign in some way |

`email` stays `NOT NULL UNIQUE`: a provider user without an email is refused.
Both OIDC columns are set together (linking sets them on an existing row).

## `User` DTO (changed)

Adds `hasPassword: Boolean` (derived from `password_hash IS NOT NULL`) so the
UI hides "change password" for provider-only users. No other DTO changes;
`auth_tokens` and sessions unchanged.

## Sign-in configuration (not stored)

From `application.yaml` (`ktor.auth`): `local`, `oidc.issuer`, `oidc.clientId`,
`oidc.clientSecret`, `oidc.name`, `adminEmail`, `adminPassword`. Exposed
publicly only as `{local, oidc: {name} | null}`.

## State transitions — provider sign-in

```text
(iss, sub) known ─────────────────────────────▶ sign in
unknown, email matches unlinked account, verified ▶ link (set iss, sub) ▶ sign in
unknown, email matches account, not verified ─▶ 401
unknown, email matches account linked elsewhere ▶ 401
unknown, no match, verified email = ADMIN_EMAIL ▶ create admin ▶ sign in
unknown, no match ────────────────────────────▶ create viewer ▶ sign in
no email ─────────────────────────────────────▶ 401
```
