# Phase 1 Data Model: Password Recovery & Initial Setup

This feature adds **no new table and no new column**. It reuses the existing `identity_action_token`
table with a new ORDINAL enum value, plus existing `identity_user`, `identity_user_channel`, and
`identity_command` tables. The only persistent change is a new **enum value** (a new integer in an
existing `INT` column).

## Enum change (the only persistent change)

### `IdentityActionType` (ORDINAL-mapped, `identity_action_token.action_type INT`)

| Ordinal | Value | Status |
|---------|-------|--------|
| 0 | `CONFIRM_EMAIL` | existing |
| 1 | `BIND_TELEGRAM` | existing |
| 2 | `RESET_PASSWORD` | **NEW — appended** |

**Rule**: MUST be appended (never inserted/reordered) — ordinals are persisted; reordering corrupts
existing rows. No migration is needed because ordinal 2 is a valid value in the existing `INT` column.

## Reused entity: Password reset token (`identity_action_token`)

A `RESET_PASSWORD` row is a specialization of the existing action token; all columns already exist.

| Field (entity) | Column | Type | Rules for a RESET_PASSWORD token |
|----------------|--------|------|----------------------------------|
| `id` | `id` | BIGINT PK | Generated; the `<id>` half of the public action key. |
| `secretHash` | `secret_hash` | BINARY(32), not null | SHA-256 of a 32-byte `SecureRandom` secret (`OpaqueKey`). The raw secret is never stored. |
| `actionType` | `action_type` | INT, not null | `RESET_PASSWORD` (ordinal 2). |
| `principal` | `principal_unique_id` | BIGINT FK → `identity_principal` | The target user's principal. |
| `principalType` | `principal_type` | INT | As set by the issuer (USER). |
| `identityUserChannel` | `identity_user_channel_unique_id` | BIGINT FK → `identity_user_channel` | The **verified** email channel the link was sent to. |
| `payload` | `payload` | BLOB (encrypted) | Unused for reset (null) unless a future need arises. |
| `createdAt` | `created_at` | DATETIME, not null | Issue time; basis for the per-email cooldown window. |
| `expireAt` | `expire_at` | DATETIME, not null | `createdAt + identity.action-token.expires-in` (default 1 day). |

**Identity / uniqueness**: A row is identified by `id`; the caller must also present the matching raw
secret (constant-time verified) to act on it.

**Lifecycle / state transitions**:

```text
(request, eligible + cooldown clear + rate-limit clear)
        │  issue: create row, enqueue SEND_EMAIL
        ▼
   PENDING ──(open link, secret verifies, not expired)──▶ CONSUMABLE
        │                                                     │
        │ (expireAt passes)                                   │ resetPassword(): lock (FOR UPDATE),
        │                                                     │ requireStrong + encode + save password,
        ▼                                                     │ invalidate other sessions
    EXPIRED  ── open ──▶ "invalid or expired"                 ▼
                                                          CONSUMED (row hard-deleted)
                                                              │
                                                       any later open ──▶ "invalid or expired"
```

- **Single active link** (spec Assumption): issuing a new reset token supersedes older outstanding ones
  for the same email; older ones resolve to "invalid or expired" when opened.
- **Concurrent completion**: the pessimistic `findByIdForUpdate` lock guarantees exactly one successful
  password change; the loser sees the token already consumed → "invalid or expired".

## Reused entity: `IdentityUserEntity` (`identity_user`)

| Field | Column | Relevance |
|-------|--------|-----------|
| `password` | `password` | BLOB, **nullable**, AES-GCM-encrypted; holds the Argon2 hash. Recovery/setup writes a new Argon2 hash here. A "no usable password" account (initial-setup candidate) has `password == null`. |
| `uniqueId` | `unique_id` | Shared PK with `identity_principal`; links the token's `principal` to the credential. |
| `version` | `version` | `@Version` optimistic lock on the user row (independent of the token's pessimistic lock). |

**Validation rules on write** (both recovery and initial setup):
- `PasswordPolicy.requireStrong(rawPassword)` server-side (≥10 chars, ≥1 upper, ≥1 lower, ≥1 digit);
  violation → `IllegalArgumentException("exception.user.password.weak")`, localized by the UI.
- Encoded with the `argon2` `DelegatingPasswordEncoder`; never stored in plaintext or reversible form.

## Reused entity: `IdentityUserChannelEntity` (`identity_user_channel`)

- Carries the email address (blind-index hash for lookup) and its **verified** state.
- **Recovery eligibility (FR-001a)**: only a *verified* channel attached to a user yields a reset link.
- **Initial setup (FR-001b/FR-017)**: a *pending* invitation channel is handled by the existing
  `CONFIRM_EMAIL` verification flow, not by a `RESET_PASSWORD` token.

## Reused entity: `IdentityCommandEntity` (`identity_command`)

- The recovery email is enqueued as a `SEND_EMAIL` / `QUEUED` command whose encrypted `payload` is a
  serialized `EmailMessage` (to/subject/body/html). No change to this entity or its enums.

## Non-persistent (runtime) structures

| Structure | Where | Purpose |
|-----------|-------|---------|
| `IdentityAction.ResetPasswordInfo` (record) | `identity-logic .../model/IdentityAction.java` | Read-model returned by `findResetPasswordActionInfo(actionKey)` for the view (e.g. the raw action key to submit); mirrors `ConfirmEmailInfo`. |
| `SetPasswordForm.Credentials`-style payload | `identity-frontend-vaadin .../ui` | The password entered on the reset screen (raw password only; no display name). |
| Rate-limit counters | `RequestRateLimiter` (in-memory) | Per-IP/client request counts within a window (FR-007a); not persisted (see research.md Decision 3 caveat). |
| Session registry entries | Spring Security `SessionRegistry` (in-memory) | Active sessions per principal, used to expire other sessions on reset (FR-014). |

## Referenced-but-unchanged

`IdentityPrincipalEntity`, `IdentityWorkspaceEntity` (pending-invitation channels), `EmailMessage`,
`OpaqueKey`, `PasswordEncoder` — used as-is, no schema or contract change.
