# Phase 0 Research: Password Recovery & Initial Setup

## Context discovered in the existing codebase

The service already has a complete secure **action-token** pipeline used for `CONFIRM_EMAIL` /
`BIND_TELEGRAM`, which is the natural substrate for password recovery:

- **Entity** `IdentityActionTokenEntity` (`identity-logic/.../entity/IdentityActionTokenEntity.java`)
  — `secret_hash BINARY(32)`, ORDINAL `actionType`, optional `principal` + `identityUserChannel`,
  encrypted `payload`, `createdAt`, `expireAt`. No new columns are needed for a reset token.
- **Issuer** `IdentityActionTokenService` — `confirm(channel)` issues a `CONFIRM_EMAIL` token, enforces
  a per-channel cooldown via
  `existsByActionTypeAndIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(...)`, sets
  `expireAt = createdAt + expiresIn`, and enqueues an email. `OpaqueKey` gives a 32-byte
  `SecureRandom` secret, stored only as SHA-256, formatted as `<id>_<base64url-secret>` and verified
  with constant-time `MessageDigest.isEqual`.
- **Processor** `IdentityActionTokenProcessorService.confirmEmail(...)` locks the token
  (`IdentityActionTokenRepository.findByIdForUpdate` = `@Lock(PESSIMISTIC_WRITE)`), applies the domain
  effect, and hard-deletes it (`consumeAction`).
- **Password** — `PasswordEncoder` bean = `DelegatingPasswordEncoder("argon2", …)` in
  `IdentityLogicConfig`; `PasswordPolicy.isStrong` / `requireStrong` (key `exception.user.password.weak`,
  min 10 chars + upper + lower + digit); `IdentityUserEntity.password` is a nullable, AES-GCM-encrypted
  BLOB (a passwordless account = `password == null`).
- **Email** — `EmailService` (`JavaMailSender`) + async command queue
  `IdentityCommandService.enqueue(EmailMessage)` (`SEND_EMAIL`/`QUEUED`); `ConfirmEmailMailFactory`
  loads bilingual (uk→en) templates from `resources/templates/email/`, subject from a
  `<!-- subject: … -->` header, `${webUrl}` placeholder.
- **Frontend** — `IdentityUserEmailVerificationView` (`@Route("verify/email/:id?")`) already drives
  `CredentialsForm` (display name + password×2, validated by `PasswordPolicy::isStrong`) to set an
  initial password during provisioning; `VaadinAuthenticationService.authenticate(UserDetails)` does
  programmatic login (pattern in `TelegramAuthView`); `LocalizationService` (default `uk-UA`, also `en`)
  resolves keys and maps `IllegalArgumentException` messages to keys.

Two mapping facts constrain the design: `IdentityActionType` and the command enums are **ORDINAL**,
so new values must be **appended**; and schema is owned by **Liquibase** (not Flyway), auto-including
files from `db/liquibase/`.

---

## Decision 1 — Reuse the action-token pipeline; add a `RESET_PASSWORD` type

**Decision**: Add `RESET_PASSWORD` as the next appended value of `IdentityActionType` (ordinal 2). Add
issuer methods on `IdentityActionTokenService` (`requestPasswordReset(email, clientKey)`,
`findResetPasswordActionInfo(actionKey)`, package-private `findResetPasswordActionForUpdate(actionKey)`)
and a `resetPassword(actionKey, rawPassword)` step on `IdentityActionTokenProcessorService`, mirroring
the `CONFIRM_EMAIL` methods exactly (same `OpaqueKey`, same cooldown query, same
`findByIdForUpdate` lock, same `consumeAction` hard-delete).

**Rationale**: The pipeline already satisfies FR-004/005/006/007 (unguessable + hashed + constant-time
secret, single-use consume, bounded expiry, per-channel cooldown) and is covered by existing tests.
Reusing it avoids duplicated security-critical code (Principle VII maintainability) and inherits the
locked, atomic consume that FR-011's "concurrent completion → exactly one change" needs.

**Alternatives considered**:
- *A dedicated `password_reset_token` table* — rejected: adds a schema migration and a parallel
  hashing/expiry/lock implementation with no behavioral benefit.
- *Reuse `CONFIRM_EMAIL` for reset* — rejected: conflates two effects on one type (a confirm token
  could then change a password), weakening the action's meaning and its audit trail.

**Consequence**: **No Liquibase migration** — ordinal 2 fits the existing `action_type INT` column and
every needed field already exists. The reset token carries the `principal` (target user) and the
verified email `identityUserChannel`, exactly like a confirm token.

---

## Decision 2 — Recovery eligibility & neutral (non-enumerating) response

**Decision**: `requestPasswordReset(email, clientKey)` canonicalizes the email (lower/trim, same as username
hashing), looks up the email channel by blind index, and issues a token **only** when the channel is
verified and attached to a user with a resolvable credential path (FR-001a). In every other case
(unknown address, unverified channel, cooldown/rate-limit hit) it performs the same lookup work and
returns `void`. The view always shows the identical `password.recovery.confirmation` message. The
method never throws a "not found" that the UI could surface differently.

**Rationale**: SC-002 requires that registered and unregistered emails be indistinguishable in both
message text and observable timing. Running the same lookup path and sending email **asynchronously**
(enqueue, not inline SMTP) keeps request latency independent of whether an email will actually be sent.

**Alternatives considered**:
- *Return a boolean / throw when not found* — rejected: leaks account existence (enumeration).
- *Send email synchronously* — rejected: makes latency depend on the existence branch and couples the
  request to SMTP availability; the existing async queue already decouples this.

**Best-effort note**: True constant-time is not guaranteed (JIT, GC, DB variance). The mitigation is a
single shared code path plus async send; documented as best-effort in the contract.

---

## Decision 3 — Per-IP/client request rate limit (FR-007a)

**Decision**: Add an in-memory `RequestRateLimiter` (fixed-window or token-bucket keyed by client IP)
guarding the recovery **request** action, in addition to the per-email cooldown. The client IP is taken
from the Vaadin servlet request (`getRemoteAddr()`, honoring a configured forwarded-header only if the
app is deployed behind a trusted proxy). Exceeding the limit returns the **same neutral confirmation**
(SC-004a) — it does not surface a distinct error that could be used as an oracle. Limits
(requests/window) are configurable via `IdentityActionTokenProperties` (or a small dedicated
properties holder) with safe defaults.

**Rationale**: The per-email cooldown does nothing against a script sweeping many distinct addresses;
Principle IV explicitly wants rate limiting on auth surfaces. An in-memory limiter needs no new
external dependency (spec Assumption: no new external services) and fits the current single-instance
deployment.

**Alternatives considered**:
- *Bucket4j / Resilience4j* — rejected for now: a new dependency for one endpoint; revisit if a
  distributed limiter is needed.
- *Redis/DB-backed counter* — rejected: introduces new infra/round-trips; overkill at current scale.

**Caveat (documented)**: In-memory state is per-instance. If the app is ever scaled horizontally, the
effective limit multiplies by instance count; this is acceptable at current single-instance scale and
flagged for revisit. The per-email cooldown (DB-backed) is already cluster-correct.

---

## Decision 4 — Applying the new password & invalidating other sessions (FR-010, FR-014)

**Decision**: In the locked `resetPassword` step, call `PasswordPolicy.requireStrong(rawPassword)`
(server-side net), encode via the existing `PasswordEncoder`, set it on the `IdentityUserEntity`, save,
then consume the token. Immediately after a successful change, expire the user's **other** active
sessions by registering a Spring Security `SessionRegistry` bean in the Vaadin app and calling
`expireNow()` on that principal's sessions (the current flow then does a fresh programmatic login).

**Rationale**: FR-010 requires the old password to stop working (encode+save does this). FR-014 requires
killing stale sessions so a pre-reset session cannot outlive the reset — a standard credential-change
control (Principle I/V).

**Alternatives considered**:
- *Spring Session (JDBC/Redis)* — rejected now: heavier, needs a store; the in-memory `SessionRegistry`
  matches current single-instance scale (same caveat as Decision 3).
- *Do nothing about sessions* — rejected: violates FR-014; a forgotten-password reset is exactly when
  an attacker-held session should be cut.

---

## Decision 5 — Reset UI: password-only form + two anonymous routes

**Decision**: Add `PasswordRecoveryRequestView` (`@Route("recover/password")`) with an email field and
a submit that always shows the neutral confirmation, and `PasswordResetView`
(`@Route("reset/password/:id?")`) that reads the action key like the verify view
(`RouteParameters.get(ID_PARAM)`), validates via `findResetPasswordActionInfo`, and renders a
password-only form. Because `CredentialsForm` **requires a display name**, introduce a
`SetPasswordForm` (password + confirm, reusing `PasswordPolicy::isStrong` and the existing
`credentials.password.*` keys) — or add a no-display-name mode to `CredentialsForm`. On success the view
programmatically signs the user in (`VaadinAuthenticationService`) and redirects, matching
`email.verification`/`TelegramAuthView` behavior (FR-015). Add a localized "forgot password?" link on
`LoginView` pointing at the request route.

**Rationale**: Recovery has no display name to set, so the existing provisioning form does not fit
directly; a small password-only component keeps validation identical to the rest of the product
(Principle VIII) without contorting `CredentialsForm`. Two anonymous routes are required because a
locked-out user cannot authenticate first (Principle III note).

**Alternatives considered**:
- *Reuse `CredentialsForm` as-is* — rejected: forces an irrelevant required display-name field on reset.
- *Single combined route with a query flag* — rejected: separate request vs. reset routes are clearer
  to secure (`@AnonymousAllowed`, permit list) and to test.

---

## Decision 6 — Initial setup (US2) rides the existing verification link

**Decision**: Treat US2 as **verification/coverage** of the existing invitation → email-verification →
`CredentialsForm` provisioning path (`IdentityUserEmailVerificationView` +
`confirmEmail(actionKey, UserProvisioningDetails)`), which already sets an Argon2 password. Add no new
setup-link surface (FR-001b/FR-017). The only work is confirming an invited user reached via a **pending
workspace email channel** receives a `CONFIRM_EMAIL` link and can set a password through it, and adding
tests that assert this end to end.

**Rationale**: The clarified spec fixes the initial-setup entry point as the verification link; building
a second surface would duplicate the provisioning flow and contradict FR-001b.

**Alternatives considered**:
- *A dedicated "set your password" invite view* — rejected by clarification (Session 2026-08-19, Q3).

---

## Decision 7 — Email template, link building, and i18n

**Decision**: Add `ResetPasswordMailFactory` + a bilingual `templates/email/reset-password.html.template`
(uk then en, `<!-- subject: … -->` header, single `${webUrl}` placeholder — no Telegram variant). Add a
`resetPasswordBaseUrl` (default `/reset/password/`) to `IdentityActionTokenProperties` and a
`resetPasswordUri(actionKey)` to `IdentityActionLinkBuilder`, with the Vaadin
`IdentityActionLinkBuilderVaadin` building the absolute URL from the `PasswordResetView` route (single
source of truth, like the verify view). Add `password.recovery.*` and `password.reset.*` keys to
`messages.properties` + `messages_uk_UA.properties`; reuse `credentials.password.*` and
`exception.user.password.weak` for the form.

**Rationale**: Mirrors the proven `ConfirmEmailMailFactory` + link-builder pattern; satisfies Principle
VIII (all new strings keyed with default-locale value in the same change) with graceful fallback.

**Alternatives considered**:
- *Hard-code English copy / build the URL from a string* — rejected: violates Principle VIII and
  duplicates the route as a magic string.

---

## Open questions

None blocking. Two items are explicitly deferred with documented caveats (single-instance scope of the
in-memory rate limiter and session registry — Decisions 3 & 4); both are correct at the current
deployment scale and flagged for revisit if the app is scaled horizontally.
