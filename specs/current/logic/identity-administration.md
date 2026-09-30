# Identity administration

## Responsibility and language

The identity domain administers workspace-scoped applications and access. A **workspace** groups
applications, roles, and user membership. An **application** is a principal associated with a workspace;
its URI identifies it within the service. A **user** is a principal with one or more channels. A
**permission** is an authority string; a **role** groups permissions and can be assigned to a principal for
a resource. An **API key** authenticates one application principal.

## Model and identity

- Principals carry the stable service identifier used by users and applications. `UniqueId` is exposed by
  the public Java and HTTP contracts instead of an untyped numeric identifier.
- Workspaces, applications, roles, API keys, user channels, role assignments, permissions, and
  application-user claims are persisted through the JPA entities and repositories in `identity-logic`.
- User channels support email and Telegram identities. Email values and principal names are handled through
  the encryption service and associated lookup hashes; API-key secrets are stored as hashes, not as their
  issued raw value.
- An application user can hold claims in the application's workspace. Its encrypted membership metadata is
  materialized as `IdentityApplicationUserMetadata` by its JPA converter. It holds Identity-owned
  application-local flags as ordinal values in the compact `f` set; ordinal `0` controls Telegram-phone
  disclosure. Malformed persisted metadata fails entity hydration rather than being treated as an empty value.
  The application API unfolds the caller application's `permissions` claim into its established user principal.

## Commands and queries

- Workspace services create, update, list, and delete workspaces, and expose their applications, roles,
  and user channels.
- Application services manage applications and Telegram-bot applications. API-key issuance returns the
  raw credential only when it is created; later management queries expose key metadata and revocation state.
  Bot configuration retains the stable Telegram ID obtained from `getMe`, independently of its replaceable token.
  Creating or updating a bot application queues webhook setup together with the application write when
  webhook support is configured. Each setup command identifies only the application and configures its
  current bot, including after replacement. Deleted applications and bots without a stored ID are skipped.
  Bot reads are shared through the read-only lookup service described in
  [Telegram bot webhooks](../engineering/engineering-notes.md#telegram-bot-webhooks); application writes and
  webhook lifecycle operations remain separate responsibilities.
- `getTelegramWebhookStatus(UniqueId)` requires `app.read`. It calls Telegram `getWebhookInfo`, classifying
  an empty URL as `NO`, an exact match with Identity's generated URL as `YES`, and another nonempty URL
  as `YES_BUT_URI_INCORRECT`. The builder-backed status includes the observed application version and
  whether toggling is allowed, gated by webhook configuration and the caller's `app.update` permission.
  A missing bot ID permits enabling from `NO`; removal still requires a stored positive validated ID.
  Missing expected public URL or invalid Telegram responses fail inspection; no URL is exposed in the status.
- `setTelegramWebhookEnabled(UniqueId, int expectedVersion, boolean enabled)` requires `app.update`.
  It rejects a stale application version before accessing the bot credentials or mutating Telegram.
  The explicit desired state, rather than a server-side inversion, selects set/delete. Registration
  reuses the existing URL, secret and maximum-connections configuration; deletion retains pending updates.
  Enabling with no stored bot ID resolves it through validated `getMe`, replaces the encrypted bot payload
  without changing its token, and commits persistence before registration. Lookup or persistence failures
  prevent registration. Registration and outcome confirmation run outside the database transaction;
  either failure retains the validated ID, but cannot undo an external Telegram change. An explicit retry
  uses the persisted ID without another lookup.
  A successful backfill increments the application version; the returned status carries that version
  for subsequent webhook actions. A stored ID is reused without another `getMe` call.
  Both actions re-query and fail unless the requested state is confirmed. Manual deletion treats `401`
  as failure, unlike old-token lifecycle cleanup. Remote mutations cannot roll back if confirmation fails;
  callers must refresh instead of automatically retrying them.
- Role, permission, role-template, and authority services manage permissions and resource-scoped role
  assignments. Authority checks are made against the authenticated principal and the relevant resource.
- User and channel services create or resolve users, attach channels, bind Telegram accounts, load
  authentication details, and maintain application-user relationships.

## Invariants and consistency

- API keys identify an application principal and are evaluated for validity by the REST security path before
  application API operations run.
- Resource permissions are workspace-scoped through the role-assignment and workspace relationships; the
  services perform their write operations in Spring transactions.
- Same-bot token rotation skips webhook deletion. A different bot is accepted only after synchronous cleanup
  of the previous webhook succeeds, or the old token is rejected with `401`. Other cleanup failures abort
  the write. Configurations without a stored bot ID skip cleanup on both bot replacement and application
  deletion. The new configuration and its SET command commit or roll back together; external Telegram
  deletion cannot roll back.
- Manual webhook removal changes Telegram only: it writes no application disable flag, changes no
  application version, and removes no pending setup command. Bot saves, queued setup commands, and
  startup re-registration retain their automatic registration behavior and can restore the webhook.
  Webhook administration adds no REST endpoint and does not alter phone disclosure.
- A Telegram account can be bound only when it does not conflict with another user. Email and Telegram
  binding flows are completed by the authentication workflows described in
  [Authentication and recovery](authentication-and-recovery.md).

## Interactions

The administration domain supplies the current application principal to the [Application API](../api/identity-application-api.md)
and supplies users, channels, and authorization data to the Vaadin UI. Its administrative routes are
described in [Administration UI](../ui/administration.md).

## Evidence

- `identity-logic/src/main/java/vg/identity/service/IdentityWorkspaceService.java`
- `identity-logic/src/main/java/vg/identity/service/IdentityApplicationService.java`
- `identity-logic/src/main/java/vg/identity/service/IdentityApiKeyService.java`
- `identity-logic/src/main/java/vg/identity/service/IdentityUserAuthorityService.java`
- `identity-logic/src/main/resources/db/liquibase/001-identity-db-init.yaml`
- `identity-logic/src/test/java/vg/identity/service/IdentityApplicationServicePermissionIntegrationTest.java`
- `identity-logic/src/test/java/vg/identity/service/TelegramWebhookLifecycleIntegrationTest.java`
- `identity-logic/src/test/java/vg/identity/service/TelegramWebhookAdministrationTest.java`
- `identity-logic/src/test/java/vg/identity/service/TelegramWebhookAdministrationIntegrationTest.java`
- `identity-logic/src/test/java/vg/identity/service/IdentityApiKeyServicePermissionIntegrationTest.java`
