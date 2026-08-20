# Quickstart: Validating Password Recovery & Initial Setup

A run/validation guide proving the feature works end to end. It references
[contracts/](contracts/) and [data-model.md](data-model.md) rather than restating them; implementation
detail belongs in `tasks.md` and the code.

## Prerequisites

- JDK per the repo toolchain; Docker running (Testcontainers MySQL 8 via `Mysql8ContainerStarter`).
- Email is **not** sent to a real inbox in tests — a recovery email is verified by asserting a
  `SEND_EMAIL` / `QUEUED` `IdentityCommandEntity` was enqueued (the command queue is the seam).
- For manual UI validation, run `identity-frontend-vaadin` with `identity.email.from` set; the queued
  email is visible in the `identity_command` table (payload is encrypted — assert its presence/type,
  not its plaintext).

## Automated validation (authoritative)

Run the module tests:

```bash
./gradlew :identity-logic:test :identity-frontend-vaadin:test
```

### Logic integration tests (extend `BaseIntegrationTest`, fixed `Clock`)

Model after `IdentityActionTokenServiceIntegrationTest` /
`IdentityActionTokenProcessorServiceIntegrationTest`.

1. **US1 issue + enqueue** — create a user with a **verified** email channel; call
   `requestPasswordReset(email, clientKey)` (tests pass a fixed `clientKey`); assert exactly one `RESET_PASSWORD` `IdentityActionTokenEntity`
   (principal + channel + `expireAt = now + expiresIn`) **and** one `SEND_EMAIL`/`QUEUED`
   `IdentityCommandEntity`.
2. **Enumeration parity (SC-002)** — call `requestPasswordReset` for (a) unknown email, (b) existing but
   **unverified** channel; assert **no** token and **no** command, and that the call returns `void`
   with no distinguishing exception — identical to a caller's eyes vs. case 1's confirmation.
3. **Cooldown (FR-007)** — call `requestPasswordReset` twice within `request-cooldown` (advance the
   fixed `Clock` by < 5 min); assert the second issues no new token/command.
4. **Reset happy path (US1 / FR-010)** — issue a token, then
   `resetPassword(actionKey, "NewPass1234")`; assert success, the token row is **deleted**, the user's
   `password` changed, the new password authenticates (`loadUserByUsername` + `PasswordEncoder.matches`)
   and the old one does not.
5. **Weak password (FR-008)** — `resetPassword(actionKey, "weak")` throws
   `IllegalArgumentException("exception.user.password.weak")` and the token is **still present**
   (link reusable).
6. **Expired link (FR-006)** — advance the fixed `Clock` past `expireAt`; `findResetPasswordActionInfo`
   returns null/empty and `resetPassword` fails without changing the password.
7. **Reused link (FR-005)** — after a successful reset, a second `resetPassword` with the same key
   fails ("invalid or expired").
8. **Tampered key (FR-004/011)** — flip a byte in the secret; `findResetPasswordActionInfo` returns
   null via constant-time mismatch.

### Frontend tests

9. **Neutral confirmation (FR-003)** — `PasswordRecoveryRequestView` shows the same
   `password.recovery.confirmation` for known / unknown / unverified / rate-limited inputs.
10. **Rate limit (FR-007a / SC-004a)** — exceed the per-IP limit; assert the same neutral confirmation
    and that no additional token is issued beyond the limit.
11. **Reset view routing (FR-011)** — missing `:id` → `password.reset.link.missing`; bad/expired key →
    `password.reset.link.invalidOrExpired`; valid key → `SetPasswordForm` renders.
12. **Sign-in after reset (FR-015)** — on successful submit the view calls `VaadinAuthenticationService`
    and redirects to `/`.
13. **Localization (Principle VIII / SC-006)** — every new key resolves in `messages.properties` and
    `messages_uk_UA.properties`; no raw key leaks; missing non-default falls back to default.

### US2 — initial setup via the verification link

14. Create a **pending workspace email-channel invitation** (no password). Drive the existing
    `verify/email/:id?` flow (`confirmEmail(actionKey, UserProvisioningDetails(displayName, password,
    true))`); assert the invited user is provisioned with an Argon2 password and can sign in. Confirm
    **no** `RESET_PASSWORD` token or separate setup surface is involved (FR-001b/FR-017).

## Manual smoke test (UI)

1. Start `identity-frontend-vaadin`; open `login` → click **"forgot password?"** → `recover/password`.
2. Submit a verified user's email → see the neutral confirmation.
3. Read the issued token from `identity_action_token` (type `RESET_PASSWORD`); build the link
   `/<resetPasswordBaseUrl>/<id>_<secret>` — for a real end-to-end check, capture the secret only at
   issue time in a debug build (secrets are not stored). Open the link → set a compliant password →
   confirm redirect to `/` and that the new password signs in while the old one fails.
4. Re-open the same link → "invalid or expired".

## Success criteria mapping

| SC | Validated by |
|----|--------------|
| SC-001 (regain access < 5 min) | Manual smoke test + happy-path tests (4, 12). |
| SC-002 (no enumeration, incl. timing) | Tests 2, 9 (same path, async send). |
| SC-003 (rejected after use/expiry) | Tests 6, 7. |
| SC-004 / SC-004a (cooldown / per-IP limit) | Tests 3, 10. |
| SC-005 (one-way hash only) | Test 4 (assert stored value is an argon2 hash, never plaintext). |
| SC-006 (localization + fallback) | Test 13. |
| SC-007 (support-ticket reduction) | Post-release ops metric — not unit-testable; noted only. |
