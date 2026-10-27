# Contract delta: Sign-in methods and OIDC (API v2)

Additive changes to [004 api-v2.md § Auth & user panel](../../004-universal-stats-platform/contracts/api-v2.md#auth--user-panel);
merged there in the same change (FR-015). Paths relative to `/api/v2`.
Errors use the existing `ApiError {code, message, details}` body. No version
bump: nothing existing changes shape except one added field.

| Method + path | Role | Notes |
|---------------|------|-------|
| `GET /auth/config` | public | `{"local": true, "oidc": {"name": "Authelia"}}`; `oidc` is `null` when no provider is configured. No secrets, no URLs |
| `POST /auth/oidc/start` | public | `{redirectUri}` → `{authorizationUrl, state, codeVerifier}`; no provider configured → 404; provider unreachable → 503 (code `unavailable`); shares the login rate limit (5/min per IP) |
| `POST /auth/oidc` | public | `{code, codeVerifier, redirectUri}` → `{token, user}` (same as `POST /auth/login`); no provider configured → 404; refused by provider, invalid/expired/foreign ID token, no email, unverified email of an existing account, or an account already linked to another identity → 401 with a short reason; provider unreachable → 503; login rate limit |
| `POST /auth/login` | public | unchanged; local sign-in off → 403 "Password sign-in is disabled" |
| `PUT /me/password` | signed in | unchanged; local sign-in off or user has no password → 403 |
| `GET /me`, `GET /users`, … | — | user objects gain `hasPassword: boolean` |

`authorizationUrl` = provider `authorization_endpoint` with `response_type=code`,
`client_id`, `redirect_uri`, `scope=openid email profile`, `state`,
`code_challenge`, `code_challenge_method=S256`.

Unchanged: `POST /auth/logout`, every other `/api/v2` route (still the opaque
bearer token), `POST /ingest/{sourceId}` with source tokens (FR-012).

## Example

```http
POST /api/v2/auth/oidc/start
{"redirectUri": "http://127.0.0.1:51234/callback"}

200 {"authorizationUrl": "https://auth.example.com/api/oidc/authorization?response_type=code&client_id=statistics-ui&...",
     "state": "q8V…", "codeVerifier": "Zt3…"}

# browser → provider → http://127.0.0.1:51234/callback?code=ory_ac_…&state=q8V…

POST /api/v2/auth/oidc
{"code": "ory_ac_…", "codeVerifier": "Zt3…", "redirectUri": "http://127.0.0.1:51234/callback"}

200 {"token": "…", "user": {"id": 7, "email": "alice@example.com", "name": "Alice", "role": "viewer",
     "prefs": {…}, "hasPassword": false}}
```
