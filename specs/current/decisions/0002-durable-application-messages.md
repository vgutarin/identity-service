# ADR-0002: Durable application messages with server-owned routing

[ADR policy](../../README.md#architecture-decision-records-adrs)

| Field | Value |
| --- | --- |
| Status | Superseded |
| Created | 2026-11-15 |
| Decision date | 2026-11-15 |
| Decision makers | Project owner (user), through explicit implementation-plan approval |
| Supersedes | None |
| Superseded by | [ADR-0003](0003-message-and-command-outcome-transactions.md), outcome transaction ownership only |

## Context

Applications need a stable message identifier and a status resource. The initial local implementation
returned empty acceptance and stored the message inside a Telegram-specific command. A queue job's
execution state is insufficient as the public identity and lifecycle of an application message.

## Alternatives

| Option | Benefits | Costs and risks |
| --- | --- | --- |
| Expose queue-command IDs and state | Reuses existing persistence directly | Couples clients to execution internals and provider-specific payloads; no independent message lifecycle |
| Durable message resource, existing queue as execution mechanism | Stable public identity, ownership, status, encrypted content, and submission deduplication | Additional table and transaction coordination |
| Client-selected channel and immediate route rejection | Caller controls delivery and sees unavailable routes synchronously | Couples clients to routing and omits tracked history for otherwise valid submissions |

## Decision

Create a message resource identified by `UniqueId`, with server-owned channel selection. Persist valid,
authorized submissions and their ID-only queue commands atomically. Return a status representation and
application-scoped lookup. Resolve route availability asynchronously, initially using Telegram only.

Support optional application-scoped UUID idempotency keys. Replay returns the original message's current
state; a key reused for different content conflicts. Store scalar application and user identifiers
without foreign keys so history does not prevent existing deletion operations.

Keep selected route data only in memory for the delivery attempt. Do not persist `telegramBotId`,
`channelType`, or `channelUniqueId` on the message or in a separate metadata table. Retaining typed route
fields or moving them into a separate JSON-backed table was considered for historical diagnostics, but
neither has a production reader. The smaller message model avoids extra storage and maintenance.
Remove the columns from the initial create script; the unreleased design needs no migration.

A message receives at most one automatic delivery attempt. Distinguish definite failure from an uncertain
send outcome. Persist message and command outcomes atomically after an HTTP call outside a database
transaction. Interrupted work retains its last recorded state for manual investigation, with no automatic
recovery, retry, channel fallback, or history expiry in this version.

## Consequences

Applications can safely recover submission receipts with a key and inspect status without knowing the
transport. Message text has one encrypted authoritative location, referenced by the application-message
command. The feature is unreleased and requires no compatibility handler for its earlier local draft.

A message can outlive its application or recipient. Retention and operational cleanup remain explicit
maintenance concerns. There is no exactly-once external-delivery guarantee: a crash after sending and
before recording the outcome leaves manual investigation necessary. Keyed requests serialize on the
application row within the caller's transaction.

Delivery still resolves the current bot and eligible channel during preparation and commits `SENDING`
before the outbound call. Message history cannot reconstruct the selected route after configuration
changes. Operational logs may help investigate an attempt, but do not guarantee historical reconstruction
after configuration changes or log expiry. Interrupted and uncertain messages still require manual investigation.

## Related contracts and evidence

- [Application API](../api/identity-application-api.md#application-messages)
- [Message lifecycle](../logic/application-messaging.md)
- [Queue operation](../engineering/engineering-notes.md#scheduled-work-and-audit-routing)
- [Integration coverage](../../../identity-integration-tests/src/test/java/vg/identity/service/ApplicationMessagingIntegrationTest.java)
- [Message entity](../../../identity-logic/src/main/java/vg/identity/entity/IdentityMessageEntity.java)
- [Persistence coverage](../../../identity-logic/src/test/java/vg/identity/repository/IdentityMessageRepositoryIntegrationTest.java)

## Resolution and supersession

2026-11-15: Accepted through the user's explicit request to implement the revised application messaging
plan, including its recorded channel-selection, acceptance, idempotency, retry and manual-recovery choices.

2026-11-15 clarification: The project owner confirmed that the earlier Telegram-specific command was
never committed. Its compatibility path is removed before release; this corrects the initial compatibility
assumption and leaves the durable-message, routing and recovery decisions unchanged.

2026-11-15: [ADR-0003](0003-message-and-command-outcome-transactions.md) supersedes the atomic
message/command outcome rule. Resource ownership, server-owned routing and the single-attempt policy
remain in effect; current contracts describe the implemented API and validation.

2026-11-15 refinement: The project owner approved omitting persisted route history and folding that
choice into this unreleased messaging design. Routes are resolved per attempt and retained only in memory;
no metadata table or migration is introduced. The durable message, server-owned routing, and single-attempt
policies remain in effect.
