# Identity administration

## Responsibility and language

Administer users, workspace-scoped applications, and access. A **principal** is a user or application;
a **workspace** groups applications, roles, and memberships. A **role** bundles permission strings and
can be assigned to a principal for a resource. An **API key** authenticates one application.

## Model and identity

- Users/applications share `identity_principal` identifiers. Public contracts use `UniqueId`.
  Usernames are trimmed/lower-cased and exclude `:`; application names are absolute URIs, keeping
  their namespaces disjoint in the shared name index.
- Users have email/Telegram channels with encrypted identifiers, lookup hashes, and verification state.
  Personal-data consent belongs to the user.
- Application-user claims are application-local strings. The API unfolds the `permissions` scope;
  these values are never Spring authorities and grant no platform permission.
- Encrypted `IdentityApplicationUserMetadata` stores application-local flags as ordinal values in `f`;
  `TELEGRAM_PHONE_DISCLOSURE=0`. Malformed stored metadata fails hydration.

## Commands and queries

Workspace services manage workspaces, applications, roles, and member channels. `addUser` creates/reuses
an email channel: known users attach immediately; unknown users remain pending until confirmation
provisions their account. Unverified email channels receive confirmation actions. `addChannel` attaches
an existing email or verified Telegram channel; `getUserChannels` exposes pending/verified membership.
Workspace creation seeds roles from workspace-independent role templates.
Global system roles are `OWNER` and `VERIFIED_USER`; OWNER grants all platform permissions.

Application services manage applications/bots and their API keys. Issuance requires `app.update`, returns
`<uuid>.<secret>` once, and stores only the secret hash. Authentication checks expiry, revocation, and
an ACTIVE application using constant-time secret comparison. Authentication returns a principal without
key material or granted authorities. Later queries expose label, creation/expiry/revocation times, not the key.
Role/permission/template and user-authority services manage platform access; `AuthorityChecker` evaluates
resource-scoped grants with workspace inheritance and global OWNER access.

[Administration messaging](application-messaging.md#commands-and-queries) owns SEND/READ checks,
application selection, shared idempotency, and auditing. Its
[Java contract](../api/identity-application-api.md#administration-messaging-java) also provides paged
message history. Submission and history are Java service operations; no administration messaging
REST operation exists.

### Application-user claims

`IdentityApplicationClaimService.grantClaim`/`revokeClaim`/`getUserClaims` require respectively
`app.claim.create`, `app.claim.delete`, and `app.read` for the selected application. Management is exposed
through administration, not the application REST API. Scope/claim values are nonblank, trimmed, and
lower-cased; scope length is at most 64. Lookups return sets grouped by scope.

Grants in `identity_application_user_claim` are unique by application/user/scope/claim. Scope and claim
strings are interned per workspace in an encrypted, blind-indexed dictionary and referenced by ID.
Membership records authentication history (`lastAuthenticatedAt`) independently of claim grants.

### Telegram bot lifecycle and webhook administration

Bot configuration stores the Telegram ID validated by `getMe` with its encrypted token. Configured bot
creation/update queues SET in the application transaction; it configures the current bot at execution.
[Engineering notes](../engineering/engineering-notes.md#telegram-bot-webhooks) own transport and setup.

| Operation | Permission | Behavior |
| --- | --- | --- |
| `getTelegramWebhookStatus(UniqueId)` | `app.read` | Inspect Telegram's URL: empty → `NO`, exact expected URL → `YES`, other nonempty URL → `YES_BUT_URI_INCORRECT`. |
| `setTelegramWebhookEnabled(UniqueId, int expectedVersion, boolean enabled)` | `app.update` | Reject stale version before credential access/mutation; set/delete the explicit desired state, then confirm by re-querying. |

Status includes application version and toggle eligibility, never the remote URL. Missing expected URL
or invalid Telegram response fails inspection. Toggle eligibility requires webhook configuration and
update permission. A missing bot ID allows enabling from `NO`; removal requires a positive validated ID.
URL agreement does not establish secret correctness or delivery health.

Enabling without an ID validates `getMe`, saves the ID without changing the token, and commits before
registration. Backfill increments the version returned to the caller; existing IDs are reused. Lookup/
persistence failure prevents registration. Registration and confirmation run outside the database
transaction: failures retain the backfilled ID and cannot undo remote changes. Refresh before an explicit
retry; there is no automatic retry. Manual deletion retains pending updates and treats `401` as failure.

## Invariants and consistency

- Same-bot token rotation skips webhook deletion. Different-bot replacement deletes the previous webhook
  synchronously before saving; old-token `401` is non-blocking, other failures abort. Missing bot IDs
  skip cleanup on replacement/deletion. External deletion cannot roll back with database writes.
- Manual webhook removal changes Telegram only. It does not persist a disable switch or change the
  application version; bot saves, pending commands, or startup registration may restore the webhook.
- Telegram binding rejects conflicts with another user. Consent, confirmation, and password rules belong
  to [Authentication and recovery](authentication-and-recovery.md).

## Interactions and evidence

[Application API](../api/identity-application-api.md) supplies application-scoped metadata and user access.

- [Workspace service](../../../identity-logic/src/main/java/vg/identity/service/IdentityWorkspaceService.java),
  [application service](../../../identity-logic/src/main/java/vg/identity/service/IdentityApplicationService.java),
  [user authorities](../../../identity-logic/src/main/java/vg/identity/service/IdentityUserAuthorityService.java).
- [Application permission tests](../../../identity-logic/src/test/java/vg/identity/service/IdentityApplicationServicePermissionIntegrationTest.java),
  [API-key permission tests](../../../identity-logic/src/test/java/vg/identity/service/IdentityApiKeyServicePermissionIntegrationTest.java).
- [Claim management](../../../identity-logic/src/main/java/vg/identity/service/IdentityApplicationClaimService.java),
  [claim dictionary](../../../identity-logic/src/main/java/vg/identity/entity/IdentityWorkspaceScopeClaimDictionaryEntity.java),
  [claim integration tests](../../../identity-logic/src/test/java/vg/identity/service/IdentityApplicationClaimGrantIntegrationTest.java).
- [Webhook lifecycle](../../../identity-logic/src/test/java/vg/identity/service/TelegramWebhookLifecycleIntegrationTest.java)
  and [administration integration](../../../identity-logic/src/test/java/vg/identity/service/TelegramWebhookAdministrationIntegrationTest.java).
