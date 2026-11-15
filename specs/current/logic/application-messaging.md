# Application messaging

## Responsibility and language

Accept durable, application-owned messages for application members and deliver through that
application's Telegram bot. A **message** owns content/status and a `UniqueId`; a **command** queues
its execution. Telegram acknowledgement does not establish that a user read the message.

## Model and identity

`identity_message` stores scalar application/recipient IDs, immutable encrypted `MessageContent`, a
required immutable UUID idempotency key, status, safe failure code, version, and timestamps. No foreign
keys block application/user deletion. History and keys have no automatic expiry. Sender attribution
is audit-only; routes and credentials are ephemeral, with no route-history table.

[The API contract](../api/identity-application-api.md#application-messages) owns content validation,
JSON (`content.t`), request/result models, and HTTP responses.
[Engineering notes](../engineering/engineering-notes.md#scheduled-work-and-audit-routing) own encryption
and numeric enum mappings.

## Commands and queries

| Entry service | Application selection | Access |
| --- | --- | --- |
| `IdentityApplicationApiService` | Authenticated application, or configured trusted embedded fallback | Existing application authorization. |
| `IdentityApplicationMessageService` | Explicit application ID, without fallback | Resolved principal ID plus `app.message.send` for submission or `app.message.read` for lookup. |

Administration permissions use `AuthorityChecker`, including workspace inheritance and global OWNER.
Human and application principals follow the same permission rules; application identity alone grants
no messaging access. `CurrentPrincipalService.requirePrincipalUniqueId()` rejects only an unresolved
ID; it adds no principal-type or anonymous-token restriction. Ordinary anonymous callers resolve no ID.
Neither messaging permission is automatic; `app.read` and `app.update` alone grant neither. Member
listing continues to require `app.read`.

Check administration permissions on every invocation, including replays. SEND covers the submission
receipt/replay without READ; READ covers independent lookup. The worker does not recheck the sender:
permission revocation after acceptance does not cancel delivery.

For a new submission, require existing recipient membership. Unknown/nonmember recipients receive the
same neutral unavailable result and commit no message or command. Otherwise create a `QUEUED` message and one
`SEND_APPLICATION_MESSAGE` command together, joining the caller's transaction, even when delivery is
unavailable. The command contains only `messageUniqueId`.

Keys are application-scoped across both entry services. Same key/recipient/exact content returns the
existing message without another command or membership check; different content/recipient conflicts.
Lookup is ownership-scoped, independent of source or current recipient membership. Unknown and
other-owned messages both return empty.
Paged history uses the same READ permission and resolved-principal guard as lookup. It filters by the
selected application, orders newest creation/message ID first, and returns the existing safe metadata
model without content.

Successful administration submissions/replays register an after-commit audit event containing actor,
application, recipient, message IDs, and `CREATED`/`REPLAYED`. Rollback emits no acceptance event.
Events exclude text, keys, contact data, credentials, and raw exceptions.
[Audit routing and fields](../engineering/engineering-notes.md#scheduled-work-and-audit-routing).

`IdentityMessageService`, its constructor, and `submit`/`find`/`begin`/`finish` are package-private.
Public entry services authorize before delegation; the trusted command processor executes accepted work.
Visibility constrains callers; authorization remains the entry services' responsibility.

## Lifecycle and consistency

| State | Meaning |
| --- | --- |
| `QUEUED` | Recorded, awaiting processing. |
| `SENDING` | Start of the single attempt committed. |
| `SENT` | Telegram acknowledged the send with a positive message ID. |
| `FAILED` | Preparation failed or Telegram definitively rejected delivery. |
| `UNKNOWN` | Send acceptance is uncertain. |

1. Lock the message. Only `QUEUED` can start; already-started/final messages return without sending,
   including through duplicate commands.
2. Set `SENDING` and select a route: current membership, recorded consent, and the most recently updated
   eligible verified Telegram channel belonging to the recipient, with a positive numeric chat ID.
   The application must have a Telegram bot with a nonblank token. Delivery does not require a stored
   `telegramBotId`; missing or nonpositive numeric bot IDs do not prevent sending. The
   [webhook authentication rules](authentication-and-recovery.md#invariants-and-consistency) still require a valid stored bot ID.
3. Commit preparation, then call Telegram outside any database transaction using literal content text
   and the selected current credentials. Configuration/membership changes cannot atomically cancel a
   prepared external call. The route is not retained for historical reconstruction.
4. Commit the message outcome. The processor returns on success/duplicate, or throws a sanitized
   exception after an unsuccessful outcome. The worker completes/fails the command separately.

| Failure code | State | Cause |
| --- | --- | --- |
| `DELIVERY_UNAVAILABLE` | `FAILED` | Missing membership, consent, bot, or eligible channel. |
| `INTERNAL_ERROR` | `FAILED` | Malformed stored routing configuration. |
| `PROVIDER_REJECTED` | `FAILED` | Telegram `ok=false` or HTTP client error. |
| `DELIVERY_UNKNOWN` | `UNKNOWN` | Transport/server errors, unexpected send exceptions, or missing/malformed acknowledgement. |

`SENT` has no failure code; `FAILED` requires one of its three codes; `UNKNOWN` requires
`DELIVERY_UNKNOWN`. Finalization requires the message to be `SENDING`.

There is no automatic retry, fallback, recovery, or supported reset-to-queue resend. Duplicate commands
complete without changing an already-started message. Uncaught processing errors fail only the command
and leave the message's last committed state (`QUEUED`, `SENDING`, or final). Abrupt exit or failure to
persist command completion/failure may leave the command `RUNNING`, even with a final message outcome.
Operators investigate interrupted/uncertain work manually. A successful external send followed by
outcome rollback is not exactly-once delivery.

Provider details, text, chat IDs, and credentials do not enter public or command failure diagnostics.
Recorded delivery failures expose a safe code; malformed payloads and unexpected preparation/outcome
errors expose only the command ID, without the original exception/cause.

Submission locks the application row, serializing same-application writes, then atomically inserts a
candidate or retains the existing row under unique `(application_unique_id, idempotency_key)`. It does
not lock a missing message key before inserting. A current unmanaged projection supplies the winning
message, including when an enclosing transaction's snapshot or managed message is stale. Replays change
no stored columns, skip membership revalidation, and enqueue nothing. New candidates require membership;
rejection rolls back the candidate with the caller's transaction. Message and command creation share
that transaction, including repeated submissions before commit. Pending caller changes are flushed
without clearing or refreshing managed entities. Ordinary conflicting caller writes still obey optimistic
locking. Preparation continues to use a message row lock. Keep enclosing transactions short.
The accepted [queue polling limit](../engineering/engineering-notes.md#command-queue-and-message-storage)
can leave late-committing messages `QUEUED` indefinitely. Remaining
[queue concurrency verification](../open-decisions.md#messaging-queue-and-concurrency) is tracked separately.

## Interactions

Telegram must permit the selected bot to message the recipient; authentication alone does not establish
that permission. Delivery uses the existing queue/transport and requires neither webhook setup nor phone
disclosure. [Authentication](authentication-and-recovery.md) establishes membership and consent.

## Related contracts and evidence

- [API](../api/identity-application-api.md), [ADR-0002](../decisions/0002-durable-application-messages.md),
  and [ADR-0003](../decisions/0003-message-and-command-outcome-transactions.md).
- [Core service](../../../identity-logic/src/main/java/vg/identity/service/IdentityMessageService.java),
  [processor](../../../identity-logic/src/main/java/vg/identity/service/IdentityMessageCommandProcessor.java),
  [administration entry](../../../identity-logic/src/main/java/vg/identity/service/IdentityApplicationMessageService.java).
- [Service unit tests](../../../identity-logic/src/test/java/vg/identity/service/IdentityMessageServiceTest.java),
  [processor tests](../../../identity-logic/src/test/java/vg/identity/service/IdentityMessageCommandProcessorTest.java),
  [worker tests](../../../identity-logic/src/test/java/vg/identity/service/IdentityCommandWorkerTest.java).
- [Administration integration](../../../identity-logic/src/test/java/vg/identity/service/IdentityApplicationMessageServiceIntegrationTest.java)
  and [HTTP/embedded integration](../../../identity-integration-tests/src/test/java/vg/identity/service/ApplicationMessagingIntegrationTest.java).
- [Content contract](../../../identity-api/src/test/java/vg/identity/model/MessageContentTest.java),
  [encrypted storage/constraints](../../../identity-logic/src/test/java/vg/identity/repository/IdentityMessageRepositoryIntegrationTest.java),
  [audit callback](../../../identity-logic/src/test/java/vg/identity/audit/IdentityMessageSubmissionAuditorTest.java).
