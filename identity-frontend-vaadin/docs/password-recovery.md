# Password Recovery & Initial Setup

Self-service password recovery for the Vaadin identity app, plus initial password setup for invited
users. Implemented per [`specs/002-password-recovery-setup`](../../specs/002-password-recovery-setup/).
This is a **web-only** flow — no REST API surface is added.

## User-facing routes

| Route | View | Access | Purpose |
|-------|------|--------|---------|
| `login` | `LoginView` | anonymous | Sign-in. Its built-in "Forgot password" button links to the recovery request page. |
| `recover/password` | `PasswordRecoveryRequestView` | anonymous | Enter an email to request a reset link. Always shows the same neutral confirmation (no account enumeration). |
| `reset/password/:id?` | `PasswordResetView` | anonymous | Opened from the emailed link (`:id` = the action key). Sets a new password, then signs the user in. Dead-end links (missing / invalid / expired / used) offer a link back to `recover/password`. |
| `verify/email/:id?` | `IdentityUserEmailVerificationView` | anonymous | **Initial setup** for invited users rides this existing link — no separate setup surface. |

## How it works

- **Recovery** reuses the existing action-token pipeline. `IdentityActionTokenService.requestPasswordReset(email, clientKey)` issues a single-use, time-limited `RESET_PASSWORD` token **only** for a *verified* email attached to a user, and enqueues a bilingual email (async, via the command queue). `IdentityActionTokenProcessorService.resetPassword(actionKey, rawPassword)` validates the key under a pessimistic lock, enforces the password policy, stores a new Argon2 hash, invalidates the user's other sessions, and consumes (hard-deletes) the token.
- **Initial setup** (invited users): `IdentityWorkspaceService.addUser(workspace, email)` creates a pending email channel and issues a `CONFIRM_EMAIL` link; the verify view provisions the account with an initial password. No `RESET_PASSWORD` token is involved.

## Configuration (`identity.action-token.*`)

| Property | Default | Meaning |
|----------|---------|---------|
| `reset-password-base-url` | `/reset/password/` | Path the reset link points to (frontend builds the absolute URL from the `PasswordResetView` route). |
| `expires-in` | `P1D` (1 day) | Link validity window (shared with email confirmation). |
| `request-cooldown` | `PT5M` (5 min) | Minimum interval between reset requests for the same email. |
| `reset-rate-limit.max-requests` | `10` | Per-IP/client request cap within the window. |
| `reset-rate-limit.window` | `PT10M` (10 min) | Rate-limit window. |

Also requires `identity.service.public-url` (to build absolute email links) and a configured mail sender
(`identity.email.from`, `spring.mail.*`).

## Security properties

- **No account enumeration**: known / unknown / unverified emails and rate-limited requests all return the
  identical neutral confirmation; email is sent asynchronously so latency does not correlate with account
  existence (best-effort constant path, not guaranteed constant-time).
- **Single-use + expiring** links; secret stored only as SHA-256, verified in constant time.
- **Abuse throttling**: per-email cooldown (DB-backed, cluster-correct) + per-IP/client rate limit
  (in-memory, per-instance — see the note below).
- **Session invalidation** on password change (`SessionRegistry`).
- **Auditable**: link requested / consumed / password changed / failed are logged with `tokenId` only —
  never a secret, raw link, or plaintext password.

## Deployment notes / limitations

- The per-IP rate limiter and the `SessionRegistry` are **in-memory (per instance)**. At the current
  single-instance deployment this is correct; if scaled horizontally, the effective rate limit multiplies by
  instance count and session invalidation only covers the local instance. Revisit with a shared store
  (e.g. Redis / Spring Session) if multi-instance. The per-email cooldown is DB-backed and stays correct.
- All links must be served over TLS in deployed environments.
