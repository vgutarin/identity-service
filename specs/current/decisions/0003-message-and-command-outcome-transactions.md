# ADR-0003: Separate message and command outcome transactions

[ADR policy](../../README.md#architecture-decision-records-adrs)

| Field | Value |
| --- | --- |
| Status | Accepted |
| Created | 2026-11-15 |
| Decision date | 2026-11-15 |
| Decision makers | Project owner (user), through explicit approval of separate transactions |
| Supersedes | [ADR-0002](0002-durable-application-messages.md), outcome transaction ownership only |
| Superseded by | None |

## Context

Atomic recording of message and queue-command outcomes required the message processor to own command
completion and failure, with command-type-specific lifecycle checks in the worker. The project owner
prefers the original worker loop and accepts separate outcome transactions.

## Alternatives

| Option | Benefits | Costs and risks |
| --- | --- | --- |
| Processor commits message and command outcomes together | Both outcomes advance atomically | Processor owns queue lifecycle and the worker needs special handling |
| Processor commits message outcome; worker finalizes command separately | One queue lifecycle owner and the original generic worker loop | Message and command outcomes can differ if the command update fails |

## Decision

The worker dispatches processing, completes commands after normal return, and fails commands after a
processing exception. The message processor commits message state only. It returns after success or
when a message attempt has already started; it throws a sanitized exception after recording a delivery
failure or uncertain outcome. Unexpected processing exceptions lose their original message and cause
before reaching the worker's logging and persistence.

Message creation and queue insertion remain atomic. Preparation commits before the HTTP call. Final
message state and command completion/failure commit separately. No automatic retries or recovery are added.

## Consequences

The message processor has no dependency on command completion/failure. A committed message outcome
survives later command-write failure. A `RUNNING` command can coexist with `SENT`, `FAILED`, or `UNKNOWN`;
earlier interruptions can leave `QUEUED` or `SENDING`. Operators investigate these states manually.
Already-started messages are never sent again, including through duplicate queue commands.

## Related contracts and evidence

- [Application messaging](../logic/application-messaging.md#lifecycle-and-consistency)
- [Queue operation](../engineering/engineering-notes.md#scheduled-work-and-audit-routing)
- [Worker](../../../identity-logic/src/main/java/vg/identity/service/IdentityCommandWorker.java)
- [Processor](../../../identity-logic/src/main/java/vg/identity/service/IdentityMessageCommandProcessor.java)
- [Integration tests](../../../identity-integration-tests/src/test/java/vg/identity/service/ApplicationMessagingIntegrationTest.java)

## Resolution and supersession

2026-11-15: Accepted through the user's explicit request to restore the original worker loop, keep
message updates in the processor, and allow separate transactions without retries.
