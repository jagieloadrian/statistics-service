# Research: OIDC Sign-in

Phase 0 decisions for [plan.md](./plan.md). Spec: [spec.md](./spec.md).
Order of preference: least code that is still correct and safe.

## 1. Backend: validate IdP tokens per request, or exchange once for a platform session?

**Decision**: exchange once. The backend redeems the authorization code at
the provider's token endpoint, verifies the returned **ID token** (RS256
signature against the provider's JWKS, `iss`, `aud` = client id, `exp`/`iat`),
finds or creates the user, and answers with the existing opaque session token
(`LoginResponse {token, user}`) — exactly what `POST /auth/login` returns.
IdP tokens are discarded.

**Rationale**: one auth path for the whole API: `authenticate("user")`, the
`auth_tokens` table, 7-day sliding expiry, logout, password-change revocation
and the access-control tests stay untouched. Code redemption on the server
needs no CORS on the provider, allows a client secret when a provider demands
one (kept server side), and the frontends never handle IdP tokens.

**Alternatives**:
- *Validate the IdP JWT on every request* (`ktor-server-auth-jwt` `jwt {}`
  provider next to `bearer`): two principal types on every route, role lookup
  per request anyway, access tokens are opaque at many providers (Authelia's
  default), and the frontend would need refresh-token handling. Rejected.
- *Frontend redeems the code and posts the ID token*: needs CORS on the
  provider's token endpoint and duplicates the token call in two clients.
  Rejected.
- *Skip the signature check* (OIDC Core §3.1.3.7 allows TLS instead when the ID
  token comes straight from the token endpoint): fewer lines, but plain-HTTP
  issuers inside Docker networks would then be unprotected. Rejected — keep
  the signature check (FR-003).

**Library**: `com.auth0:java-jwt` + `com.auth0:jwks-rsa`, declared directly
(the same libraries `ktor-server-auth-jwt` wraps; no unused Ktor plugin, no
reliance on transitive deps). Use `JwkProviderBuilder`
(cached, rate-limited, refetches on unknown `kid`) and `JWT.require(...)`;
the `jwt {}` auth provider is not installed. RS256 only — mandatory for every
OIDC provider and Authelia's default (`// ponytail: RS256 only, add ES256
when a provider needs it`). Alternatives: hand-written RS256 with JDK
`Signature` (~20 lines, but own crypto parsing — no); Nimbus JOSE (bigger).

**Discovery/HTTP**: JDK `java.net.http.HttpClient` + kotlinx.serialization for
`/.well-known/openid-configuration`, the token request and userinfo — no new
HTTP client dependency. Discovery is fetched lazily and cached after the first
success, so the platform starts while the provider is down (spec edge case).

**Claims**: `sub`, `email`, `email_verified`, `name`. If the ID token carries no
`email` (OIDC Core §5.4 lets providers return scope claims only from userinfo;
Authelia does this unless a claims policy says otherwise), GET `userinfo` with
the access token and require the same `sub`. Nonce is not used: code flow with
PKCE and an ID token received directly from the token endpoint (OIDC Core makes
nonce optional here).

## 2. PKCE and redirect handling in the clients

**Decision**: the backend starts the flow. `POST /auth/oidc/start
{redirectUri}` returns `{authorizationUrl, state, codeVerifier}`; the backend
builds the verifier with the existing `newToken()` and the S256 challenge with
the existing `sha256()`. The client keeps `state` + `codeVerifier`, sends the
user to `authorizationUrl`, checks `state` on return and posts
`{code, codeVerifier, redirectUri}` to `POST /auth/oidc`. The backend stores
nothing between the two calls.

**Rationale**: no crypto in the KMP clients (common code has no SHA-256;
`crypto.subtle` exists only in secure contexts, so a web UI on plain
`http://<lan-ip>` would break). The verifier never appears in a URL, so an
intercepted redirect (`code` + `state`) cannot be redeemed — same protection
as client-generated PKCE.

**Alternatives**: client-side PKCE (expect/actual SHA-256 + secure random, web
limited to https/localhost) — more code, rejected; verifier stored server-side
keyed by `state` — then `code` + `state` from an intercepted redirect is
enough to redeem, rejected; cookie-based BFF session — second auth mechanism,
rejected.

**Web** (js/wasm): `redirectUri` = the app's own origin + `/`. Before leaving,
put `state` + `codeVerifier` in `sessionStorage`; on load, if the URL has
`code`/`state` (or `error`), finish sign-in and clean the URL with
`history.replaceState`.

**Desktop** (JVM): RFC 8252 loopback. JDK `com.sun.net.httpserver.HttpServer`
on `127.0.0.1:0` (ephemeral port), `redirectUri =
http://127.0.0.1:<port>/callback`, open the system browser with
`java.awt.Desktop.browse`, wait for one callback (5 min timeout, cancellable),
answer "you can close this window", stop the server. No new dependency; the
packaged runtime needs `modules("jdk.httpserver")`. Authelia's OAuth library
(fosite) matches loopback IP redirect URIs regardless of port (RFC 8252 §7.3);
if a provider insists on an exact port, register a fixed one and set it in
the desktop build config (`// ponytail: ephemeral port, fixed port if a
provider requires exact match`).

The callback query (`code`, `state`, `error`, `error_description`) is parsed by
one common function used by both targets.

## 3. Refresh, expiry, logout

**Decision**: no IdP refresh tokens (no `offline_access` scope). After sign-in
the user holds a normal platform session: valid while used within 7 days
(existing rule). Logout = existing `POST /auth/logout` (deletes the platform
token). No RP-initiated logout at the provider.

**Rationale**: zero new session code; same behaviour for both sign-in methods
(FR-008). Ceiling: removing a user at the provider blocks new sign-ins but not
a running session — the admin deletes the account to end it now (spec edge
case). Upgrade path if needed: absolute lifetime for provider sessions, or
`end_session_endpoint` redirect on logout.

## 4. How clients learn the enabled methods

**Decision**: public `GET /api/v2/auth/config` →
`{"local": true, "oidc": {"name": "Authelia"} | null}`. No secrets, no provider
URLs (the start endpoint resolves those). Provider unreachable → the start call
answers 503 with a message and the UI shows it (spec edge case).

**Alternatives**: put issuer/endpoints in the config and let clients run
discovery — needs CORS on the provider and duplicates discovery; build-time
client config — violates SC-005 (no rebuild).

## 5. Configuration

`application.yaml` under `ktor.auth`: `local: ${AUTH_LOCAL:true}`,
`oidc.issuer: ${OIDC_ISSUER:}`, `oidc.clientId: ${OIDC_CLIENT_ID:}`,
`oidc.clientSecret: ${OIDC_CLIENT_SECRET:}` (sent as HTTP Basic when set),
`oidc.name: ${OIDC_NAME:SSO}`. Provider enabled ⇔ issuer and client id set.
Startup fails if local is off and no provider is set (FR-010). `ADMIN_EMAIL`
keeps its meaning; `ADMIN_PASSWORD` stays optional — without it no local admin
is created and the first verified provider sign-in with `ADMIN_EMAIL` becomes
admin (FR-005).

## 6. Account linking rules

On `POST /auth/oidc`, in one transaction:
1. user with (`oidc_issuer`, `oidc_subject`) exists → sign in.
2. else user with the same (normalized) email exists → link if `email_verified`
   and the account has no provider identity yet, else 401 (no silent relink).
3. else create: role `admin` if `email_verified` and email = `ADMIN_EMAIL`,
   otherwise `viewer`; no password.

Missing email (token and userinfo) → 401. Email and name are not synced after
creation (spec assumption).

## 7. Tests without a running Authelia

**Decision**: an in-test fake provider: JDK `HttpServer` on a random port
serving discovery, JWKS, token and userinfo; RSA key pair from
`KeyPairGenerator`; ID tokens signed with `java-jwt` (already on the classpath
via §1). The test sets the claims (or a bad key/issuer/audience/expiry) the
token endpoint returns next. ~50 lines in `testutils/`, no image, no Docker for
the IdP, runs offline (SC-006).

**Alternatives**: `navikt/mock-oauth2-server` via Testcontainers — real
provider, but another image plus an extra authorize hop in every test, and less
direct control over broken tokens; running Authelia in Testcontainers — slow,
needs TLS and secrets. Rejected for tests; Authelia stays the manual
reference (quickstart).

## 8. Reference provider: Authelia (dev only)

`.devops/authelia-compose/`: `compose.yaml` (`authelia/authelia`),
`configuration.yml`, `users_database.yml` (file backend, argon2 hashes), dev
secrets and keys generated by a short README step, not committed. Constraints
from Authelia's docs: it must be served over **https** and its cookie domain
must contain a dot and must not be `localhost` → use `auth.example.com` mapped
to `127.0.0.1` in `/etc/hosts` and Authelia's own TLS with a self-signed
certificate; the backend JVM trusts it via
`-Djavax.net.ssl.trustStore=...` (no code). One public client
`statistics-ui`: `public: true`, `require_pkce: true`,
`pkce_challenge_method: S256`, `token_endpoint_auth_method: none`,
`authorization_policy: one_factor`, `consent_mode: implicit`, redirect URIs
`http://localhost:8081/` and `http://127.0.0.1/callback`; a claims policy that
puts `email`, `email_verified`, `name` into the ID token (userinfo fallback
covers providers without it).
