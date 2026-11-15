# Engineering notes

## Build and module layout

The wrapper builds Java 21 sources. Run Gradle from the repository root.

| Module | Responsibility |
| --- | --- |
| `identity-api` | Public Java models and `IdentityApplicationApi`. |
| `identity-logic` | Domain services, JPA/Liquibase, encryption, and command processing. |
| `identity-rest-server` | Optional REST controllers and API-key security. |
| `identity-rest-client` | Typed HTTP client and configuration. |
| `identity-rest-app` | Standalone REST deployment, API enabled. |
| `identity-frontend-vaadin` | Vaadin deployment, REST disabled by default. |
| `identity-integration-tests` | Embedded/HTTP conformance and integration tests. |

```sh
./gradlew :identity-logic:test --tests vg.identity.service.IdentityUserServiceTest
./gradlew :identity-logic:test
./gradlew test
```

`identity-logic` and `identity-integration-tests` use MySQL 8 Testcontainers and require Docker.
Vaadin tests use the frontend module's test configuration. Distinct Spring mock sets need distinct H2
database names, otherwise repeated Liquibase initialization can fail with `databasechangelog already
exists`; webhook tests use `fe-webhook-test`, authentication views `fe-view-test`.
Application-details browserless tests use a separate `fe-application-details-test` database.

All cached integration contexts sharing a database mock the scheduled command worker, preventing races
with queue assertions and cleanup. Lifecycle tests invoke a real worker explicitly.
`ApplicationMessagingIntegrationTest` lives in `vg.identity.service` to access the package-private core
for fault injection while covering public HTTP/embedded entry points.

## Runtime entry points and persistence

- REST entry: `vg.identity.app.IdentityApplication`; Vaadin entry:
  `vg.identity.frontend.vaadin.FrontendApplication`. See [configuration](configuration.md) for activation.
- `identity-logic/src/main/resources/db/liquibase/` owns the schema, loaded from the application classpath.
- Encryption uses AES-256/GCM with a versioned keyring; retain old keys while their ciphertext exists.
  Blind indexes use a separate HMAC-SHA256 key for equality lookup. Passwords use Argon2; opaque API/action
  keys store SHA-256 secret hashes. Supply production secrets through deployment configuration.
- `CurrentPrincipalService` is a concrete Spring service. `findCurrentPrincipal()` returns authenticated
  Spring `UserDetails` or null. `findCurrentPrincipalUniqueId()` uses `Identifiable.getUniqueId()` or
  username lookup through `IdentityUserService`; `requirePrincipalUniqueId()` rejects only null.
  `hasRole()` uses authentication authorities and role normalization. It adds no principal-type or
  anonymous-token restriction. `AuthorityChecker` owns permissions; `CurrentIdentityApplicationService`
  owns application selection and embedded fallback.
- REST advice applies only to `vg.identity.rest.server.controller`. It handles invalid JSON/arguments
  and messaging failures with the [API's sanitized responses](../api/identity-application-api.md#surface),
  before servlet error dispatch can enter browser security.
- `MainView` resolves `HasDynamicTitle` before its localized `@PageTitle` fallback. Application details
  supplies a dynamic title; parameterized translations use `LocalizationService.i18n(key, arguments)`.
  Message enums have explicit Ukrainian/English UI labels; the API/database enum contracts are unchanged.
- Application details uses `History.replaceState` with a typed `Location` for tab URLs. It keeps query
  parameters from navigation and replaces only `tab`, without a navigation callback. Tab permissions are
  rechecked on refresh, but only changed membership causes insertion/removal; unchanged tabs stay attached.

Evidence: [principal tests](../../../identity-logic/src/test/java/vg/identity/service/CurrentPrincipalServiceTest.java),
[advice tests](../../../identity-rest-server/src/test/java/vg/identity/rest/server/controller/IdentityRestControllerAdviceTest.java),
[REST activation tests](../../../identity-rest-server/src/test/java/vg/identity/rest/server/RestApiToggleTest.java).

### Authentication runtime

Password-recovery client rate limits and the frontend `SessionRegistry` are per-instance/in-memory;
horizontal scaling multiplies the effective client allowance and limits session invalidation to local
registered sessions. Per-email cooldown is database-backed. Reset consumes its token under a lock;
the Vaadin reset view expires other registered sessions and attempts automatic sign-in. Invitations
use confirmation actions for initial credentials. Deliver action links over HTTPS.

Email wording lives in `identity-logic/src/main/resources/templates/email/`: bilingual Ukrainian/English
templates with a `<!-- subject: ... -->` header and `${webUrl}` link placeholder. Confirmation also has
a Telegram variant using `${telegramUrl}`; password reset is web-only. The default action-link builder
returns host-relative links; the Vaadin override uses `identity.service.public-url` for absolute links.
Mail delivery uses the optional `JavaMailSender`; `EmailService` also validates email-shaped usernames.

## Scheduled work and audit routing

### Command queue and message storage

`IdentityCommandWorker` uses the configured fixed delay (default in [configuration](configuration.md)).
Commands move `QUEUED → RUNNING → COMPLETED | FAILED` with optimistic version checks; processing and
completion/failure are separate transactions. There is no automatic retry of already-started work.
Claiming first selects the oldest visible `QUEUED` command with `id > cursor`. Only when none exists,
it searches with `id > cursor - 100`. After flushing the claim, the in-memory cursor becomes the selected
ID, including when fallback moves it backward. Both searches exclude `RUNNING`, `FAILED`, and `COMPLETED`.

This is a best-effort late-commit mitigation: gaps of 1–99 behind the current cursor are eligible;
exactly 100 and larger are excluded from that poll. IDs are shared by all command types and rolled-back
inserts consume IDs too. Sustained newer work can prevent fallback; accepted messages can therefore
remain `QUEUED` indefinitely and require manual investigation. A process restart resets the cursor,
without making already-started commands eligible. Resetting a command to `QUEUED` does not reset the
cursor and is not a supported delivery-retry mechanism.

The project owner accepts this residual risk to avoid additional queue-index maintenance.
[Unit tests](../../../identity-logic/src/test/java/vg/identity/service/IdentityCommandServiceTest.java)
and [MySQL integration tests](../../../identity-integration-tests/src/test/java/vg/identity/service/ApplicationMessagingIntegrationTest.java)
cover the exclusive boundary, fallback priority, claim rollback, and exclusion of already-started work.

Queue models (`IdentityCommand`, status/type enums, both payload models) live in
`vg.identity.model.command`; `EmailMessage` lives in `vg.identity.model`, services in `vg.identity.service`.
Payload JSON contains no Java type/package discriminator. Command type ordinals:

| Type | Ordinal | Payload |
| --- | ---: | --- |
| `SEND_EMAIL` | 0 | `EmailMessage`. |
| `SET_TELEGRAM_WEBHOOK` | 1 | `SetTelegramWebhookCommand`: only `applicationUniqueId`. |
| `SEND_APPLICATION_MESSAGE` | 2 | `SendApplicationMessageCommand`: only `messageUniqueId`. |

Command status ordinals: `QUEUED=0`, `RUNNING=1`, `COMPLETED=2`, `FAILED=3`.
[Type](../../../identity-logic/src/test/java/vg/identity/model/command/IdentityCommandTypeTest.java) and
[status tests](../../../identity-logic/src/test/java/vg/identity/model/command/IdentityCommandStatusTest.java)
freeze those contracts. Follow [persisted enum rules](../../../TESTING.md#persisted-enum-contracts).

`identity_message.content` is immutable, non-null `MEDIUMBLOB`.
`MessageContentConverter` serializes `MessageContent` to `{"t":"..."}` and encrypts it; hydration decrypts
and deserializes. Malformed/null stored JSON fails with sanitized diagnostics, without a plaintext-bearing
cause. Converter null inputs remain null for schema/entity nullability enforcement.
Telegram receives the literal Java text in its provider `text` field.
Outbound messaging uses the configured token and recipient chat ID, without checking `telegramBotId`
or calling `getMe`. Webhook authentication continues to require a positive stored bot ID.

Message `status` and nullable `failure_code` use ordinal `SMALLINT` mappings:

| Enum | Name/ordinal contract |
| --- | --- |
| Status | `QUEUED=0`, `SENDING=1`, `SENT=2`, `FAILED=3`, `UNKNOWN=4`. |
| Failure code | `DELIVERY_UNAVAILABLE=0`, `PROVIDER_REJECTED=1`, `INTERNAL_ERROR=2`, `DELIVERY_UNKNOWN=3`. |

Public JSON uses enum names. [Status](../../../identity-api/src/test/java/vg/identity/model/IdentityMessageStatusTest.java),
[failure-code](../../../identity-api/src/test/java/vg/identity/model/IdentityMessageFailureCodeTest.java),
and [persistence tests](../../../identity-logic/src/test/java/vg/identity/repository/IdentityMessageRepositoryIntegrationTest.java)
cover compatibility, numeric storage, null failure codes, and absence of route metadata.

`IdentityMessageSubmissionRepository.insertOrGet` requires the caller's transaction (`MANDATORY`).
It flushes pending JPA writes, then uses MySQL `INSERT ... ON DUPLICATE KEY UPDATE` with a no-op duplicate
assignment and the existing application/key unique constraint. The following `SELECT ... FOR UPDATE`
projects scalar columns from a now-existing row, bypassing stale JPA entities and repeatable-read
snapshots. Comparing the winning ID with the generated candidate ID distinguishes creation from replay;
affected-row counts are not used. There is no independent submission transaction or isolation override.

Native insertion explicitly calls `MessageContentConverter`, stores UUIDs as 16 bytes and enum ordinals,
initializes version zero, and uses the injected UTC clock with microsecond precision for both timestamps.
Duplicate submissions preserve every column, including ciphertext, version, and timestamps. The native
read uses typed scalars and the same converter to produce an unmanaged `IdentityMessageSnapshot`;
it never clears the persistence context. [Persistence tests](../../../identity-logic/src/test/java/vg/identity/repository/IdentityMessageRepositoryIntegrationTest.java)
cover encryption, JPA compatibility, replay immutability, and shared commit/rollback.
[Messaging integration tests](../../../identity-integration-tests/src/test/java/vg/identity/service/ApplicationMessagingIntegrationTest.java)
coordinate different-application inserts and stale-snapshot/entity replays under MySQL repeatable read.
The flow avoids the reproduced missing-key gap-lock cycle; unrelated database deadlocks remain possible.

The package-private `IdentityMessageService` remains a transactional Spring bean. Calls must cross bean
boundaries for class-based proxy interception: submit message/command together, commit preparation
before HTTP, then record the message outcome; the worker finalizes the command separately.
[Application messaging](../logic/application-messaging.md#lifecycle-and-consistency) owns delivery,
idempotency, and interrupted-state rules; [ADR-0003](../decisions/0003-message-and-command-outcome-transactions.md)
records the transaction choice.

### Audit and diagnostic logs

All audit emitters use `vg.identity.security.audit`; its sink, tamper resistance, and retention are
deployment concerns. Route it to the required durable/append-only sink.

Log internal `UniqueId` values using their canonical string representation, including identifiers
loaded as `Long` from entities. SLF4J formats `UniqueId` arguments through `toString()`; convert numeric
entity IDs before passing them to a logger. Absent IDs remain null. Queue command IDs and Telegram
provider IDs retain their numeric representation.

| Emitter | Package | Events |
| --- | --- | --- |
| `TelegramAuthenticationAuditor` | `vg.identity.audit` | Immediate provisioning/authentication events, identified by `userUniqueId`. |
| `IdentityMessageSubmissionAuditor` | `vg.identity.audit` | `event=message.user.submit` only after transaction commit, including replays/enclosing transactions. |
| `ApiAuthenticationAuditor` | `vg.identity.rest.server.audit` | Immediate failures and periodically aggregated authentication successes. |

Message audit outcomes are `CREATED`/`REPLAYED`; fields are `actorUserUniqueId`, `applicationUniqueId`,
`userUniqueId`, and `messageUniqueId`. The actor field holds the resolved principal ID, including an
application ID. Rollback emits no acceptance event. Events exclude text, idempotency keys, contact data,
credentials, and exception details.

API failures include sanitized reason (`missing_header`, `multiple_headers`, `invalid_key`), method,
path, remote address, and available key UUID only. Success counts group by application/method/path
with window bounds; excess distinct keys use an overflow bucket. Bounds/flush settings are in
[configuration](configuration.md#rest-api-server). Raw keys and Telegram init/profile payloads are excluded.

`IdentityMessageService` logs rejections, unavailable routes, invalid transitions, and skipped channels
at WARN with fixed reason codes/internal IDs. This includes missing consent or verification. DEBUG adds
replays, duplicate-attempt skips, route selection, and outcomes; enable
`logging.level.vg.identity.service.IdentityMessageService=DEBUG`. Logs exclude text, keys, raw chat/contact
values, credentials, and exception details. Transactional diagnostics do not prove commit.

Phone-normalization logs contain reason/error codes and input shape (leading `+`, digit count, letters,
formatting characters), never raw/parsed numbers or parsing exceptions. Missing/blank input produces
no normalizer log; no default country is inferred.

## Telegram bot webhooks

With webhook support and a public URL configured, bot creation/update queues setup of
`${identity.service.public-url}/api/v1/telegram/webhooks/{applicationUniqueId}`. Use textual `UniqueId`,
not the numeric key. The public URL must include any externally visible servlet prefix.

The header secret is unpadded URL-safe Base64 of
`HMAC-SHA256(masterSecret, "{applicationUniqueId}:{telegramBotId}")`. `getMe` validates the stored bot ID;
its result overwrites any supplied ID. Bot replacement invalidates the former secret after commit,
including under the previous master secret; same-bot token rotation retains it. Telegram sends the
derived value in `X-Telegram-Bot-Api-Secret-Token`. The master secret remains in deployment configuration.
Phone sharing requires an externally reachable HTTPS webhook URL.

The webhook bypasses API-key authentication using the context-relative path. Decodable updates receive
neutral `204` for invalid IDs, secrets, or content; unreadable JSON receives the shared sanitized `400`.
[Context-path tests](../../../identity-integration-tests/src/test/java/vg/identity/rest/TelegramWebhookContextPathIntegrationTest.java)
cover prefixed deployments. Phone acceptance/disclosure rules belong to
[authentication](../logic/authentication-and-recovery.md).

`TelegramBotLookupService` shares read-only decoding and lookup by application ID or case-insensitive
bot username (also returning the public bot URI). Absence returns null only from the nullable lookup;
strict lookup rejects absence, and malformed payloads fail with sanitized errors. It has no administration/
webhook-registration dependency. Webhook phone handling ignores bad/absent configuration without
marking its caller's transaction rollback-only.

SET commands use current stored credentials/configuration, skip deleted/non-Telegram applications and
missing bot IDs, and fail if registration/public URL is unavailable. Pending commands may configure a
replacement bot repeatedly. No credential snapshot is queued. Failed setup needs a new command or bot save.

Same-bot token rotation skips deletion. Different-bot replacement synchronously deletes the previous
webhook before saving; deletion `401` is non-blocking, other failures abort. Missing bot IDs skip old-token
lookup/deletion on replacement or application deletion. Successful external deletion cannot roll back
with a failed database write; retry the application update to recover. Telegram errors are sanitized.

### Background administration inspection and toggle

[Administration logic](../logic/identity-administration.md) defines inspection, version checks, backfill,
and confirmation. [Administration UI](../ui/administration.md) defines visible states and interactions.

The frontend `telegramWebhookExecutor` has two workers, queue capacity 100, and non-blocking abort
rejection. Tasks propagate/restore the caller's security context. Refresh batches query sequentially;
explicit toggles run separately. Telegram calls run outside the UI/session lock; callbacks use
`UI.accessLater` with detach handling. Cached results and generation/operation IDs reject stale callbacks;
obsolete batches stop before their next call. Already-started mutations can still complete remotely.

Pending work temporarily enables one-second UI polling, retaining any faster existing interval and
restoring it on completion/deactivation/detach. Polling delivers callbacks only. A toggle that changes
the application version reloads/replaces its row in the background under the same stale-result checks.
Registration logs the configured callback URI at INFO; inspections do not expose remote URLs or secrets.

### Emergency webhook-secret rotation

1. Set the previous master secret as `identity.telegram.webhook.previous-secret` and configure the new
   active secret. Both must satisfy [validation](configuration.md#telegram); values are not trimmed.
2. Set `identity.telegram.webhook.reregister-on-startup=true` for the rollout with webhook/public URL
   configured. Startup re-registers every stored Telegram bot using the active derived secret, backfilling
   validated bot IDs before registration. The operation is idempotent, retains no rotation state, and
   fails when registration is unavailable.
3. After successful registration, return the startup option to `false`. Keep the previous secret for
   the grace period, then remove it.

The previous-secret setting accepts prior master secrets only with bot-bound derivation; it does not
accept application-only derivation. Such webhooks require explicit re-registration.
[Lifecycle tests](../../../identity-logic/src/test/java/vg/identity/service/TelegramWebhookLifecycleIntegrationTest.java)
cover registration transactions and rotation; [administration integration tests](../../../identity-logic/src/test/java/vg/identity/service/TelegramWebhookAdministrationIntegrationTest.java)
cover inspection/toggling.

## Verification references

- [Compact content converter](../../../identity-logic/src/main/java/vg/identity/entity/MessageContentConverter.java)
  and [converter tests](../../../identity-logic/src/test/java/vg/identity/entity/MessageContentConverterTest.java);
  [message schema](../../../identity-logic/src/main/resources/db/liquibase/002-identity-message.yaml)
  and [client JSON integration](../../../identity-integration-tests/src/test/java/vg/identity/rest/IdentityApplicationMessageClientIntegrationTest.java).
- [Phone normalization](../../../identity-logic/src/test/java/vg/identity/service/PhoneNumberNormalizerTest.java)
  and [webhook phone integration](../../../identity-logic/src/test/java/vg/identity/service/TelegramPhoneWebhookServiceIntegrationTest.java).
- [Webhook UI task integration](../../../identity-frontend-vaadin/src/test/java/vg/identity/frontend/vaadin/service/TelegramWebhookUiTasksIntegrationTest.java)
  covers context propagation, detached/stale callbacks, and row reloads.

## Related contracts

[Configuration](configuration.md), [administration](../logic/identity-administration.md),
[authentication](../logic/authentication-and-recovery.md), and [Application API](../api/identity-application-api.md).
