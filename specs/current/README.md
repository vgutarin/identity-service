# Current identity-service documentation

This directory describes the behavior implemented in the repository. Historical feature records remain
under [`specs/`](../README.md) and are not current-state contracts.

## Contracts

- [Application API](api/identity-application-api.md) — API-key-authenticated application metadata and
  Telegram principal resolution.
- [Identity administration](logic/identity-administration.md) — workspaces, applications, users, roles,
  permissions, and API keys.
- [Authentication and recovery](logic/authentication-and-recovery.md) — email actions, password recovery,
  Telegram sign-in, consent, and queued mail commands.
- [Authentication UI](ui/authentication.md) — public Vaadin sign-in, recovery, verification, and Telegram
  routes.
- [Administration UI](ui/administration.md) — authenticated Vaadin workspace and access-management routes.
- [Configuration reference](engineering/configuration.md) — authoritative Identity-owned server and client
  configuration properties.
- [Engineering notes](engineering/engineering-notes.md) — modules, runtime entry points, database migrations,
  operational procedures, and verification facts.

## Maintenance

Follow [Documentation authoring and maintenance](../README.md#documentation-authoring-and-maintenance).
Update the applicable contract with any behavior, public-contract, configuration, or data-flow change.
