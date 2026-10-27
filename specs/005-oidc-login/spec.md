# Feature Specification: Sign-in through an OpenID Connect Provider

**Feature Branch**: `005-oidc-login`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "Users can sign in through any OpenID Connect provider — lightweight IAM providers instead of plain app passwords. Local password accounts stay alongside it and can be switched off by config; the app still works with no provider configured. Roles admin/editor/viewer stay managed in the app; the provider only authenticates. First sign-in creates a viewer account; ADMIN_EMAIL keeps bootstrapping the first admin. Device/collector source tokens stay as they are. Authelia is the reference provider for development; tests must not need it running. The Statistics-UI web and desktop apps get the sign-in flow and learn which sign-in methods are enabled."

> Extends spec 004 (US4 user panel, FR-019/FR-020). Constitution v2.2.0
> moves OIDC sign-in into scope. Technical direction: [plan.md](./plan.md).

## Clarifications

### Session 2026-10-27

- Q: One specific provider or any? → A: any OpenID Connect provider, set up by
  its issuer address and a client id; Authelia is only the reference for
  development.
- Q: Replace local accounts? → A: no. Local accounts stay and can be switched
  off by config; with no provider configured the platform works as today.
- Q: Who decides roles? → A: the platform. The provider only proves who the
  user is; admins manage roles in the existing user panel.
- Q: Do devices and collectors sign in through the provider too? → A: no.
  Source credentials for ingestion stay exactly as they are.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sign in on the web through the provider (Priority: P1)

As a person known to the configured provider, I click "Sign in with
<provider>" in the web app, authenticate at the provider, and land back in
the platform signed in — without a platform password. On my first sign-in the
platform creates my account as a viewer.

**Why this priority**: this is the feature. With it, a home-lab owner manages
people in one lightweight IAM provider instead of handing out app passwords.

**Independent Test**: with a provider configured and a user `alice` in it,
open the web app, sign in through the provider, and confirm alice is signed
in as viewer, sees dashboards shared with everyone, and appears in the
admin's user list. Sign out and in again: same account, no duplicate.

**Acceptance Scenarios**:

1. **Given** a provider is configured and alice has never signed in, **When**
   she signs in through it, **Then** an account with her provider email and
   role viewer is created and she is signed in.
2. **Given** alice already has an account from the provider, **When** she
   signs in again, **Then** she gets the same account with its current role.
3. **Given** alice cancels or is refused at the provider, **When** she returns
   to the platform, **Then** she sees the sign-in screen with a short reason
   and no account is created.
4. **Given** alice is signed in, **When** she signs out, **Then** her platform
   session ends and protected pages redirect to sign-in.

---

### User Story 2 - First admin and roles for provider users (Priority: P2)

As the owner, I set the admin email in the platform configuration; when I
sign in through the provider with that (provider-verified) email, I am admin.
As admin, I promote or demote provider users in the existing user panel.

**Why this priority**: an installation that relies on the provider still needs
an admin; without this, an installation without local accounts has none.

**Independent Test**: start an empty installation with the admin email set
and local accounts off; sign in through the provider as that email → admin;
sign in as bob → viewer; admin makes bob editor; bob signs in again and can
edit dashboards.

**Acceptance Scenarios**:

1. **Given** the admin email is configured, **When** a provider user with that
   verified email signs in for the first time, **Then** their account is admin.
2. **Given** a local account (e.g. the bootstrapped admin, or one an admin
   created) exists with email X, **When** a provider user with verified email
   X signs in for the first time, **Then** the provider identity is linked to
   that account and it keeps its role.
3. **Given** an admin changes a provider user's role, **When** the user next
   uses the platform, **Then** the new role applies; the provider has no say
   in roles.

---

### User Story 3 - Operator chooses sign-in methods (Priority: P2)

As the operator, I choose by configuration which sign-in methods are offered:
local only (no provider configured — today's behaviour), local and provider,
or provider only. The sign-in screen shows exactly the enabled methods.

**Why this priority**: keeps existing installations working unchanged and
lets an owner retire app passwords once the provider is set up.

**Independent Test**: run the three configurations; in each, the sign-in
screen shows only the enabled methods, disabled methods are refused by the
platform even when called directly, and device ingestion keeps working.

**Acceptance Scenarios**:

1. **Given** no provider is configured, **When** a user opens the sign-in
   screen, **Then** only email + password is offered and works as today.
2. **Given** local accounts are switched off, **When** anyone tries password
   sign-in or a password change, **Then** it is refused and the sign-in screen
   shows only the provider button.
3. **Given** any configuration, **When** a device pushes data with its source
   credential, **Then** ingestion behaves exactly as before.

---

### User Story 4 - Sign in from the desktop app (Priority: P3)

As a desktop-app user, I click "Sign in with <provider>"; my system browser
opens the provider's page; after I authenticate, the desktop app is signed in
without me copying anything.

**Why this priority**: the desktop app is a supported client (spec 004), but
the web app alone already delivers the feature.

**Independent Test**: from the desktop app, sign in through the provider via
the system browser and confirm the app shows the user's dashboards.

**Acceptance Scenarios**:

1. **Given** the desktop app and a configured provider, **When** the user
   completes sign-in in the browser, **Then** the browser shows "you can close
   this window" and the desktop app is signed in.
2. **Given** the user closes the browser without finishing, **When** a few
   minutes pass or they press cancel, **Then** the app returns to the sign-in
   screen ready to retry.

---

### Edge Cases

- Provider unreachable at startup or sign-in: the platform still starts;
  local sign-in keeps working; provider sign-in fails with a clear message.
- Local accounts off and no provider configured: nobody could sign in, so the
  platform refuses to start and says why.
- Provider proof that is expired, tampered, from another issuer or issued for
  another client: refused, no account created or changed.
- Provider gives no email: sign-in refused with a message (accounts need an
  email). Email not marked verified: a new viewer account is still created,
  but it is never linked to an existing account and never made admin.
- Email belongs to an existing account but is not verified by the provider:
  sign-in refused (no takeover of an existing account).
- Email belongs to an account already linked to a different provider identity
  (e.g. the user was re-created at the provider): sign-in refused; an admin
  deletes the old account to allow a fresh one.
- Admin deletes a provider user: their sessions end; their next provider
  sign-in creates a fresh viewer account. Removing a user at the provider
  blocks new sign-ins only; to end running sessions now, the admin also
  deletes the account.
- Last-admin protection (spec 004) applies to provider users too.
- A provider-only user has no platform password; password change is not
  offered to them.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The platform MUST support sign-in through one OpenID Connect
  provider configured only by its issuer address and client id (plus a client
  secret only if the provider demands one); everything else is discovered
  from the provider. No provider-specific code.
- **FR-002**: Provider sign-in MUST use the authorization-code flow with
  proof key (PKCE) from the web and desktop apps; users never enter provider
  credentials into the platform.
- **FR-003**: The platform MUST accept a provider sign-in only after verifying
  the provider's signed proof (signature against the provider's published
  keys, issuer, intended client, expiry).
- **FR-004**: A provider user's account MUST be identified by provider
  identity (issuer + subject), not by email. First sign-in creates the account
  with the provider's email and role viewer, except as in FR-005/FR-006.
- **FR-005**: First sign-in with a verified email equal to the configured
  admin email MUST create an admin account.
- **FR-006**: First sign-in with a verified email that matches an existing
  account without a provider identity MUST link the provider identity to that
  account (role kept); an unverified matching email, or a matching account
  already linked to another identity, MUST be refused. Emails are compared
  trimmed and case-insensitively.
- **FR-007**: Roles MUST stay platform-managed: admins change provider users'
  roles in the existing user panel; provider data never changes a role.
- **FR-008**: After provider sign-in the user MUST hold the same kind of
  platform session as a password user, with the same expiry and sign-out rules
  (spec 004 FR-019); signing out ends the platform session (it need not end
  the provider session).
- **FR-009**: Local password sign-in MUST stay available by default and MUST
  be switchable off by configuration; when off, password sign-in and password
  change are refused.
- **FR-010**: With no provider configured the platform MUST behave exactly as
  before this feature. With neither method enabled it MUST refuse to start.
- **FR-011**: Clients MUST be able to learn, without signing in, which sign-in
  methods are enabled and what they need to start a provider sign-in.
- **FR-012**: Device/collector source credentials and ingestion MUST be
  unchanged; user sign-in proofs are never accepted for ingestion.
- **FR-013**: The web app MUST offer provider sign-in via browser redirect;
  the desktop app via the system browser returning to the app on the same
  machine.
- **FR-014**: Development MUST have a ready-to-run reference provider
  (Authelia) with file-based users; automated tests MUST NOT need it running.
- **FR-015**: Published contract changes (sign-in config, provider sign-in)
  MUST be documented with the v2 API contract.

### Key Entities

- **User** (existing): gains an optional provider identity (issuer +
  subject); password becomes optional (provider-only users have none).
- **Sign-in configuration**: enabled methods (local on/off, provider issuer +
  client id); public, contains no secrets.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user known to the provider signs in from web or desktop in
  under 30 seconds with no platform password and no copy-paste.
- **SC-002**: With no provider configured, all existing sign-in, user-panel
  and ingestion tests pass unchanged.
- **SC-003**: Zero changes are needed on devices or collector apps.
- **SC-004**: In automated tests, 100% of expired, tampered, foreign-issuer
  and wrong-client proofs are refused.
- **SC-005**: Switching sign-in methods or the provider needs only a config
  change and restart — no rebuild of backend or frontend.
- **SC-006**: The full automated test suite runs without a reference provider
  instance or network access to one.

## Assumptions

- Exactly one provider per installation; several providers are a later step.
- Anyone the provider authenticates may get a viewer account; who can sign in
  is controlled at the provider (e.g. Authelia's users file and access rules).
- The configured provider is trusted to verify emails it marks verified; that
  is what makes linking and admin bootstrap by email safe.
- Email and name are taken at first sign-in and not synced afterwards.
- Existing sessions of password users stay valid until they expire or sign
  out when local accounts are switched off.
- Signing out does not sign the user out at the provider; a later provider
  sign-in may therefore complete without a prompt.
- Admins can still create accounts with email + role in the user panel; such
  an account is linked on the first matching provider sign-in (FR-006), which
  is how a role is pre-assigned.
