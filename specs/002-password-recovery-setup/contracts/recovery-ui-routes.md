# Contract: Recovery UI Routes (Vaadin)

User-facing web contract for the password-recovery flow. This feature exposes **no REST API**
(FR-016); the "contract" is the set of Vaadin routes, their inputs, and their observable outcomes.
All text is resolved by message key (Principle VIII); routes are `@AnonymousAllowed` and added to the
Vaadin security permit list.

## Route 1 — Recovery request

- **Route**: `recover/password` (`PasswordRecoveryRequestView`, `@AnonymousAllowed`)
- **Entry point**: a localized "forgot password?" link on `LoginView` (`login` route).
- **Input**: one email field.
- **Action**: on submit → `IdentityActionTokenService.requestPasswordReset(email, clientKey)` (returns `void`); the view resolves `clientKey` (client IP) and passes it.

| Condition | Observable outcome |
|-----------|--------------------|
| Email is a verified channel on an existing user, cooldown clear, rate-limit clear | Reset link enqueued (`SEND_EMAIL`); **neutral confirmation** shown. |
| Email unknown | **Same** neutral confirmation; no link sent (FR-003, SC-002). |
| Email exists but channel unverified | **Same** neutral confirmation; no link sent (FR-001a). |
| Within per-email cooldown | **Same** neutral confirmation; no new link sent (FR-007). |
| Per-IP/client rate limit exceeded | **Same** neutral confirmation; request not processed further (FR-007a, SC-004a). |

**Invariants**:
- The confirmation message text is identical across every row above (`password.recovery.confirmation`).
- Request latency MUST NOT depend on email existence (same lookup path; async email send) — SC-002.
- No response, header, timing, or log distinguishes the branches (FR-003).

## Route 2 — Reset (set new password)

- **Route**: `reset/password/:id?` (`PasswordResetView`, `@AnonymousAllowed`), `:id` = the action key
  `<id>_<base64url-secret>` (read via `RouteParameters`, same pattern as `verify/email/:id?`).
- **On enter**: `IdentityActionTokenService.findResetPasswordActionInfo(actionKey)`.

| Condition | Observable outcome |
|-----------|--------------------|
| `:id` missing/blank | Localized "link missing" message (`password.reset.link.missing`). |
| Key not found / expired / tampered / already used | Single localized "invalid or expired" message (`password.reset.link.invalidOrExpired`) — indistinguishable causes (FR-011). |
| Key valid & unexpired | Render `SetPasswordForm` (password + confirm), helper text = `credentials.password.helper`. |

- **On submit** → `IdentityActionTokenProcessorService.resetPassword(actionKey, rawPassword)`:

| Condition | Observable outcome |
|-----------|--------------------|
| Password fails policy (client Binder or server `requireStrong`) | Localized `credentials.password.weak` / `exception.user.password.weak`; **link stays usable** for another attempt (FR-008, AS US1-3). |
| Password valid | Password updated (Argon2), token consumed, other sessions invalidated (FR-014); user signed in and redirected to `/` (FR-015); link no longer reusable (FR-005). |
| Concurrent second submit on same link | Exactly one succeeds; the other sees "invalid or expired" (FR-011, pessimistic lock). |

## Form contract — `SetPasswordForm`

- Collects: `password`, `confirm` (no display name — distinguishes it from `CredentialsForm`).
- Client validation reuses `PasswordPolicy::isStrong` and keys `credentials.password.required` /
  `credentials.password.weak` / `credentials.password.mismatch`.
- Exposes a `resetSubmit()` equivalent so the view can re-enable submit after a server rejection.

## Localization keys (new; en + uk_UA required)

```
password.recovery.title
password.recovery.prompt
password.recovery.email.label
password.recovery.submit
password.recovery.confirmation      # the single neutral message
password.reset.title
password.reset.prompt
password.reset.submit
password.reset.success
password.reset.link.missing
password.reset.link.invalidOrExpired
login.forgotPassword.link
```

Reused keys: `credentials.password.label`, `credentials.password.confirm.label`,
`credentials.password.helper`, `credentials.password.required`, `credentials.password.weak`,
`credentials.password.mismatch`, `exception.user.password.weak`.

## Security / audit invariants (apply to both routes)

- Both routes anonymous by necessity; the only authority is possession of the single-use token.
- No secret, raw action key, or plaintext password appears in logs (FR-013); audit events =
  `reset requested` / `reset link consumed` / `password changed` / `reset failed-or-expired`.
