# ADR-0001: Telegram-shared phone disclosure boundary

| Field | Value |
| --- | --- |
| Status | Accepted |
| Created | 2026-10-01 |
| Decision date | 2026-10-02 |
| Decision makers | Product owner |
| Supersedes | None |
| Superseded by | None |

## Context

Telegram Mini Apps can authenticate a user through signed init data, but a phone
number is available only after the user shares their own contact with the bot.
Identity must receive that contact without giving every application a public,
unverified callback surface or turning the phone number into a login credential.

## Alternatives

| Option | Benefits | Costs and risks |
| --- | --- | --- |
| Identity owns one webhook per application bot; stores the phone globally and controls disclosure per application-user membership | One validation and persistence boundary; application access remains explicit and revocable without duplicating membership state | Requires webhook configuration and an Identity-owned metadata key |
| Each Mini App receives and validates its own callback | Applications control their own integration | Duplicates Telegram validation, secrets, and contact handling across applications |
| Store a separate phone value per application | Simple application reads | Duplicates sensitive data and allows values to drift |

## Decision

Identity configures the Telegram webhook for each registered application bot.
It accepts only a private-chat, self-shared contact from a consented, already
established Telegram identity, using Telegram's per-webhook secret header.

The normalized E.164 number is kept in the encrypted global Telegram channel
payload. Ordinal `0` (the append-only `TELEGRAM_PHONE_DISCLOSURE` enum value) in the
compact `f` flag set of the encrypted existing application-user membership
controls disclosure. It is set only by a valid contact delivered through that
application's bot and may be cleared by that application without deleting the
global value or altering another application's metadata.
`IdentityApplicationUserMetadata` is the typed compact metadata format and
omits null fields. Flag enum values must never be reordered or deleted; new
flags are appended only.

## Consequences

Mini Apps authenticate normally, request contact from a user gesture, and retry
the application phone endpoint briefly after Telegram reports that it sent the
contact. A `204` means absent, not yet delivered, unavailable, or not disclosed
to the calling application.

The active webhook master secret remains deployment-managed. An optional previous
master secret is accepted only during an emergency rotation while existing bot
webhooks are re-registered with the active derived value; no secret or rotation
progress is persisted in Identity.

Webhook secrets bind both the application and validated Telegram bot ID. Same-bot token rotation retains
the secret; bot replacement invalidates the previous bot's deliveries. Application writes enqueue setup
using only the application ID, and the worker configures that application's current bot with current
credentials. Pending commands may repeat setup after replacement. Different-bot replacement cleans up the
previous webhook synchronously; an unauthorized old token is non-blocking. Configurations without a stored
bot ID skip deletion. Other cleanup failures abort the write. This avoids credential snapshots in queued
commands while leaving external cleanup outside database rollback guarantees.

The value is classified as `TELEGRAM_SHARED`, not independently phone-verified.
It must not support phone login, account recovery, or high-assurance identity
verification. Manual self-contact messages satisfying the same checks are
accepted intentionally. Repeated delivery is safe because it replaces the same
phone and retains the same application-user metadata flag; update-ID persistence is deferred
until webhook processing gains a non-idempotent side effect. Raw updates,
secrets, bot tokens, and phone values are not logged.

## Related contracts and evidence

- [Application API](../api/identity-application-api.md)
- [Authentication and recovery logic](../logic/authentication-and-recovery.md)
- [Identity administration logic](../logic/identity-administration.md)
- [Engineering notes](../engineering/engineering-notes.md)

## Resolution

Accepted on 2026-10-02 as part of the approved simplified Telegram phone-sharing plan.
The per-application state is stored on the existing application-user membership,
not in a separate grant record, because that relationship is already unique and
the feature needs only a boolean disclosure state.
