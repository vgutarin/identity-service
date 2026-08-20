# Contract: Reset-Token Service Methods (identity-logic)

Domain-layer contract for the `RESET_PASSWORD` action token. These live in `identity-logic` behind the
existing services, mirroring the `CONFIRM_EMAIL` methods. Signatures below are the intended shape;
`@Validated` null-checks and `@Transactional` boundaries match the existing methods.

## `IdentityActionTokenService`

### `void requestPasswordReset(@NotBlank String email, @NotBlank String clientKey)`  *(new, `@Transactional`)*

> **Signature seam (U1)**: the client IP is only available at the Vaadin/servlet layer, so the caller
> passes it in — `requestPasswordReset(@NotBlank String email, @NotBlank String clientKey)`, where
> `clientKey` is the caller-resolved client identifier (IP from `VaadinServletRequest.getRemoteAddr()`,
> honoring a trusted forwarded header only behind a trusted proxy). The logic layer never reads the
> servlet request directly. The `PasswordRecoveryRequestView` resolves `clientKey` and passes it.

**Preconditions**: none (unauthenticated caller).

**Behavior**:
1. Canonicalize `email` (lower/trim) and resolve the email channel by blind index.
2. Enforce the per-IP/client rate limit via `RequestRateLimiter`, keyed by the supplied `clientKey`
   (default **10 requests / 10-minute window**, configurable — FR-007a) — if exceeded, return without
   issuing (no distinct signal).
3. **Eligibility (FR-001a)**: proceed only if the channel is *verified* and attached to a user. If not,
   return.
4. **Cooldown (FR-007)**: if a `RESET_PASSWORD` token exists for this channel within
   `identity.action-token.request-cooldown`, return (reuse
   `existsByActionTypeAndIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual`).
5. Otherwise issue a token: `OpaqueKey.newSecret()`, store `sha256(secret)`, set `actionType =
   RESET_PASSWORD`, `principal`, `identityUserChannel`, `createdAt`, `expireAt = createdAt +
   expiresIn`; build the link via `IdentityActionLinkBuilder.resetPasswordUri(actionKey)`; enqueue a
   `SEND_EMAIL` command built by `ResetPasswordMailFactory`.

**Postconditions**: at most one new token + one queued email; **always returns `void`** — the caller
cannot distinguish which branch ran (FR-003 / SC-002).

**Security invariants**: raw secret exists only transiently in memory; only its SHA-256 is persisted;
no branch throws a caller-visible "not found".

### `ResetPasswordInfo findResetPasswordActionInfo(@NotNull String actionKey)`  *(new)*

- Parses `actionKey` (`OpaqueKey.parse`), loads the token by id, verifies the secret with constant-time
  `OpaqueKey.secretMatches`, checks `expireAt > now`, and checks `actionType == RESET_PASSWORD`.
- Returns a `ResetPasswordInfo` (at least the validated action key) when usable; returns `null`
  (or empty) for missing/expired/tampered/wrong-type — the view maps all of these to one
  "invalid or expired" message (FR-011).

### `IdentityActionTokenEntity findResetPasswordActionForUpdate(@NotNull String actionKey)`  *(new, package-private)*

- Locked lookup for the processor: parse key → `IdentityActionTokenRepository.findByIdForUpdate(id)`
  (`@Lock(PESSIMISTIC_WRITE)`) → verify secret + expiry + type. Basis for atomic single-use consume.

## `IdentityActionTokenProcessorService`

### `ResetResult resetPassword(@NotNull String actionKey, @NotNull String rawPassword)`  *(new, `@Transactional`)*

**Behavior (in order)**:
1. `token = actionTokenService.findResetPasswordActionForUpdate(actionKey)` — if null → return a
   failed result (view shows "invalid or expired").
2. `PasswordPolicy.requireStrong(rawPassword)` — throws `IllegalArgumentException(
   "exception.user.password.weak")` on violation; the token is **not** consumed (link remains usable —
   FR-008).
3. Resolve the target `IdentityUserEntity` from `token.principal` and set the new password via
   `IdentityUserService.setPassword(entity, rawPassword)` (encode with `argon2`; save).
4. Consume the token: `actionTokenService.consumeAction(token.id)` (hard delete) — single-use (FR-005).
5. Signal the caller to invalidate the user's other sessions (FR-014) and sign in (FR-015). *(Session
   invalidation uses the Vaadin-layer `SessionRegistry`; the service returns the principal so the view
   can drive `VaadinAuthenticationService`.)*

**Concurrency**: the pessimistic lock from step 1 guarantees exactly one successful change for a given
token; a concurrent second call finds it consumed and returns failure (FR-011).

**Postconditions**: `user.password` = new Argon2 hash; old password no longer authenticates (FR-010);
token row deleted; other sessions expired.

**Return**: `ResetResult` (e.g. `record ResetResult(boolean success, UserDetails user)`), analogous to
`ConfirmationResult`.

## `IdentityUserService`

### `void setPassword(IdentityUserEntity entity, String rawPassword)`  *(new, package-private)*

- `entity.setPassword(passwordEncoder.encode(rawPassword))`; save. Used by the reset processor. Keeps
  password mutation in one place rather than reusing the `@PreAuthorize`-guarded public `update(...)`
  (recovery is unauthenticated).

## Configuration

### `IdentityActionTokenProperties` (`identity.action-token.*`)  *(add one property)*

| Property | Key | Default |
|----------|-----|---------|
| `resetPasswordBaseUrl` | `identity.action-token.reset-password-base-url` | `/reset/password/` (`@NotBlank`) |
| rate-limit max requests | `identity.action-token.reset-rate-limit.max-requests` | `10` |
| rate-limit window | `identity.action-token.reset-rate-limit.window` | `PT10M` (10 minutes) |

Reuses existing `expires-in` (1 day) and `request-cooldown` (5 min). The per-IP/client rate-limit
default is **10 requests per 10-minute window** (FR-007a), configurable via the keys above; the
`RequestRateLimiter` reads these (see research.md Decision 3).

## Mail

### `ResetPasswordMailFactory.create(String recipientEmail, URI resetUrl)` → `EmailMessage`  *(new)*

- Loads `templates/email/reset-password.html.template` (bilingual uk→en, subject from
  `<!-- subject: … -->` header), substitutes `${webUrl}` = `resetUrl` (HTML-attribute-escaped), returns
  an `EmailMessage` for `IdentityCommandService.enqueue(...)`. No Telegram variant.

## Conformance / test hooks

- Issuance + enqueue asserted at the command-queue level (a `SEND_EMAIL`/`QUEUED` `IdentityCommandEntity`
  exists), matching `IdentityActionTokenServiceIntegrationTest`.
- Expiry/cooldown asserted using `BaseIntegrationTest`'s fixed `Clock`.
- Enumeration parity asserted by calling `requestPasswordReset` for verified / unverified / unknown
  emails and asserting identical outcomes (a token+command only for the verified case) with no
  caller-visible difference.
