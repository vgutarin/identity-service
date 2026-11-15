# Identity Application API

`IdentityApplicationApi` has an embedded implementation (`IdentityApplicationApiService`) and a typed
HTTP client (`IdentityApplicationApiRestClient`). The REST surface is enabled by
`identity.rest.api.enabled=true`. Administration messaging is Java-only.

## Surface

Application routes require exactly one `X-VG-Identity-API-Key` header. Authentication runs before
controllers; absent or invalid keys receive a neutral `401`. The Telegram webhook uses separate
secret-header authentication. Configuration: [reference](../engineering/configuration.md).

Shared REST advice returns `application/problem+json`, without submitted values or exception messages:

| Failure | Status | Detail |
| --- | --- | --- |
| Unreadable JSON / invalid body | `400` | `Invalid request body` |
| `IllegalArgumentException`, including malformed `UniqueId` paths | `400` | `Invalid request argument` |
| Message key reused with different content/recipient | `409` | `Idempotency key is already used for a different message`; code `MESSAGE_IDEMPOTENCY_CONFLICT` |
| Unknown/nonmember message recipient | `422` | `Message delivery is unavailable`; code `MESSAGE_DELIVERY_UNAVAILABLE` |

Operation-specific neutral responses below take precedence where documented.

## Identifiers and types

Java identifiers use `UniqueId`; HTTP represents `UniqueId` and `URI` as strings and timestamps as
ISO-8601 instants. Responses use Identity's domain names and are not OIDC-compatible. Secrets and
internal application payloads are excluded. Optional response fields may be added; the typed client
tolerates unknown properties.

## Operations

### `GET /api/v1/applications/me` → application principal

Returns `200` with `applicationUniqueId`, `workspaceUniqueId`, string `name`, and `URI uri` for the
authenticated application.

### `POST /api/v1/applications/me/authentications/telegram` → consent-gated user principal

Request: `{"telegramInitData":"<raw initData>","consentToKeepPersonalData":false}`. Consent defaults to
`false`. Invalid, unverifiable, or unresolvable data returns neutral `204` without persistence.
Successful resolution returns `200`:

| Field | Meaning |
| --- | --- |
| `userUniqueId` | Stable `UniqueId`, or null before consent. |
| `name` | Verified Telegram first/last name, falling back to username. |
| `permissions` | Set of permission strings for the calling application; empty for a provisional user. |
| `consentGiven` | Boolean, exactly `userUniqueId != null`. |

### Telegram-shared phone contact

After a Mini App calls Telegram's `requestContact()`, the bot receives the contact through
`POST /api/v1/telegram/webhooks/{applicationUniqueId}`. The Mini App can retry the phone lookup briefly
while delivery completes. Webhook setup must have succeeded; see
[operations](../engineering/engineering-notes.md#telegram-bot-webhooks).

| Method | Application route | Response |
| --- | --- | --- |
| `GET` | `/api/v1/applications/me/users/{userUniqueId}/telegram-phone` | `200` with `{"phoneNumber":"+<E.164>"}` when disclosed to this app; otherwise `204`. |
| `DELETE` | Same route | `204`; clears only this app's disclosure permission. |

Use the established user ID from Telegram authentication. The phone is global contact data; disclosure
is application-scoped. A later share can replace the number visible to applications with disclosure
permission. This is not independent proof of phone ownership or a login/recovery credential.
[Domain rules](../logic/authentication-and-recovery.md) include acceptance and revocation limitations.

### Application messages

`POST /api/v1/applications/me/users/{userUniqueId}/messages` submits:

```json
{
  "content": {"t": "Your order is ready"},
  "idempotencyKey": "a841d4c4-1bc4-4b2f-aecf-1a4dc2ddfd92"
}
```

- `content` is required `MessageContent` from `identity-api`. Its Java `text` field uses only JSON `t`:
  required, nonblank, at most 4096 Unicode code points, preserved verbatim. Markup/placeholders are literal.
  A field named `text` does not substitute for `content.t`. There is no channel selector.
- `idempotencyKey` is a required request-body UUID. Jackson's standard handling accepts hyphenated UUIDs
  in either hex case and Base64-encoded 16-byte values. Equivalent representations identify the same key.
  Missing, null, empty, blank, or invalid keys are rejected. No header supplies the key.
- Invalid input commits neither a message nor a command. Unknown/nonmember recipients receive neutral
  `422`. Missing bot/channel/consent is resolved asynchronously after acceptance.

New messages return `202`; identical keyed replays return `200`. Both include `Location` pointing to
`/api/v1/applications/me/messages/{messageUniqueId}`, retaining any servlet context prefix, and a message body:

```json
{
  "messageUniqueId": "<UniqueId>",
  "userUniqueId": "<UniqueId>",
  "status": "QUEUED",
  "createdAt": "<timestamp>",
  "updatedAt": "<timestamp>"
}
```

`failureCode` is omitted when absent. Responses contain no message content, route, credentials, or queue ID.
The [message lifecycle](../logic/application-messaging.md#lifecycle-and-consistency) defines states,
failures, interruption, and the single-attempt policy; `SENT` means Telegram acknowledgement, not a read receipt.

Keys are application-scoped across application and administration callers. Reuse one key for retries;
a new intended message needs a new key. Equal recipient and exact content (including whitespace)
returns the existing message's current state, even after membership removal; different content/recipient
conflicts. Keys remain reserved with their records. See
[idempotency and transaction consistency](../logic/application-messaging.md#lifecycle-and-consistency).

`GET /api/v1/applications/me/messages/{messageUniqueId}` returns `200` with that representation, or `404`
for an unknown/other application's message. Ownership suffices after recipient membership removal.

Embedded/typed client operations:

```java
IdentityMessageSubmission submitMessage(UniqueId userUniqueId, IdentityApplicationMessageRequest request);
Optional<IdentityApplicationMessage> findMessage(UniqueId messageUniqueId);
```

`IdentityApplicationMessageRequest` requires `MessageContent content` and `UUID idempotencyKey`;
both request and content support builders and reject invalid construction with `IllegalArgumentException`.
There is no separate key argument or generated client key.

`IdentityMessageSubmission` contains `message` and `replayed`. The HTTP body is the message alone;
the client maps `202` to `replayed=false`, `200` to `true`, and lookup `404` to empty. Replay is independent
of delivery state. An unexpected successful status or missing submission body causes `RestClientException`.
Embedded submission throws `IdentityMessageDeliveryUnavailableException` or
`IdentityMessageIdempotencyConflictException`; the REST client currently propagates Spring HTTP errors
([open exception-parity work](../open-decisions.md#align-embedded-and-rest-client-submission-exceptions)).

### Administration messaging (Java)

`IdentityApplicationMessageService` in `identity-logic` exposes:

```java
IdentityMessageSubmission submitMessage(
    UniqueId applicationUniqueId, UniqueId userUniqueId, IdentityApplicationMessageRequest request);
Optional<IdentityApplicationMessage> findMessage(UniqueId applicationUniqueId, UniqueId messageUniqueId);
Page<IdentityApplicationMessage> findMessages(UniqueId applicationUniqueId, int page, int pageSize);
```

It uses the explicit application, the same request/result/errors and idempotency namespace, and returns
empty for unknown/other-owned messages regardless of submission source or current membership.
`findMessages` returns metadata only, newest creation first, then descending message ID. Page indexes
start at zero; page size is 1–100. It requires the same READ permission and resolved principal as lookup,
and covers all submission sources after membership removal. `Page` is Spring Data's Java result in
`identity-logic`; there is no REST/client listing operation.
Submission joins the caller's transaction.
[Messaging authorization](../logic/application-messaging.md#commands-and-queries) defines SEND/READ,
principal resolution, permission checks on replays, and commit-time sender auditing.

## Consent gate (data safety)

Without recorded or newly granted consent, resolution returns a provisional principal and persists
nothing. With consent it resolves/creates a stable user; repeated calls do not duplicate it. Legacy
Telegram identities without recorded consent remain provisional until a consented call backfills it.

## Auditability

[Audit operation](../engineering/engineering-notes.md#scheduled-work-and-audit-routing) defines events
and exclusions. Raw init data, Telegram profile/contact payloads, credentials, and message text are
excluded from those audit events. Audit values are sanitized against log injection.

## Evidence

- [Java interface](../../../identity-api/src/main/java/vg/identity/service/IdentityApplicationApi.java),
  [REST controller](../../../identity-rest-server/src/main/java/vg/identity/rest/server/controller/IdentityApplicationController.java),
  [shared advice](../../../identity-rest-server/src/main/java/vg/identity/rest/server/controller/IdentityRestControllerAdvice.java).
- [Typed client](../../../identity-rest-client/src/main/java/vg/identity/rest/v1/IdentityApplicationApiRestClient.java),
  [HTTP/embedded messaging tests](../../../identity-integration-tests/src/test/java/vg/identity/service/ApplicationMessagingIntegrationTest.java),
  [client mapping tests](../../../identity-rest-client/src/test/java/vg/identity/rest/IdentityApplicationMessageClientTest.java).
