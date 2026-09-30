# Engineering notes

## Build and module layout

The Gradle wrapper builds Java 21 sources. The root project aggregates these modules:

| Module | Responsibility |
| --- | --- |
| `identity-api` | Public Java models and the `IdentityApplicationApi` interface. |
| `identity-logic` | Spring domain services, JPA entities/repositories, Liquibase migrations, encryption, and scheduled command processing. |
| `identity-rest-server` | Optional API-key-protected HTTP controller and security filter chain. |
| `identity-rest-client` | Typed HTTP client and its Spring configuration. |
| `identity-rest-app` | Standalone Spring Boot REST deployment; enables the REST API. |
| `identity-frontend-vaadin` | Spring Boot Vaadin deployment and browser UI; the REST API is disabled by default. |
| `identity-integration-tests` | End-to-end embedded-versus-HTTP conformance tests. |

Use the wrapper from the repository root. Target a module while iterating, for example:

```sh
./gradlew :identity-logic:test --tests vg.identity.service.IdentityUserServiceTest
./gradlew :identity-rest-server:test
./gradlew :identity-frontend-vaadin:test
```

`identity-logic` and `identity-integration-tests` integration bases start a MySQL 8 test container. Their
tests therefore require a usable Docker-compatible container runtime. Browser-facing Vaadin tests use the
module's test configuration and should be run through that module's Gradle task.
Vaadin tests with different Spring mock sets create separate application contexts. Give each distinct
context its own named H2 in-memory database: reusing the default database across these contexts can make
the second Liquibase initialization fail with `databasechangelog already exists`, even without parallel
execution. Webhook browserless tests use `fe-webhook-test`; authentication view tests use `fe-view-test`.
The logic integration base replaces the scheduled command worker with a mock so background processing
cannot race queue assertions or repository cleanup. Worker lifecycle tests construct and invoke a real
worker explicitly, retaining command dispatch/persistence coverage without an autonomous scheduler.

## Runtime entry points and persistence

- `vg.identity.app.IdentityApplication` is the REST application entry point and sets
  `identity.rest.api.enabled=true` in its application properties.
- `vg.identity.frontend.vaadin.FrontendApplication` is the Vaadin entry point. Its default properties keep
  the REST API disabled; deployments may enable it with `identity.rest.api.enabled=true`.
- Liquibase migrations are owned by `identity-logic` in
  `identity-logic/src/main/resources/db/liquibase/`. The application modules load them through their
  classpath changelog.
- Runtime encryption configuration is supplied through `identity.encryption.*`; production secrets belong
  in the deployment's secret source, not versioned properties. Test properties contain isolated test
  configuration only.

## Scheduled work and audit routing

- `IdentityCommandWorker` polls queued identity commands every five seconds by default. Email confirmation
  and password-reset flows enqueue mail commands rather than sending mail in the request transaction.
  Telegram-bot creation and updates enqueue `SET_TELEGRAM_WEBHOOK` commands in the same transaction as
  the application write. `SEND_EMAIL` retains ordinal `0`; webhook setup uses appended ordinal `1`.
  Failed commands remain `FAILED`, without automatic retry; enqueue a new command or save the bot
  configuration again to retry webhook setup. Resetting a failed command to `QUEUED` is insufficient while
  the worker's in-memory command cursor has passed its ID.
- `ApiAuthenticationAuditor` periodically flushes API-authentication success counters. The
  `vg.identity.security.audit` logger is the audit output; its sink and retention are deployment concerns.

## Telegram bot webhooks

See the [Telegram configuration reference](configuration.md#telegram) for property defaults and validation. When
webhook support is enabled with a public URL and valid secret, creating or updating a Telegram-bot application
queues setup of Telegram's sole webhook at `${identity.service.public-url}/api/v1/telegram/webhooks/{applicationUniqueId}`.
`{applicationUniqueId}` is Identity's textual external identifier, not its numeric database primary key.
Identity derives a distinct Telegram webhook secret with
`HMAC-SHA256(masterSecret, "{applicationUniqueId}:{telegramBotId}")`, encoded as unpadded URL-safe Base64, and
validates Telegram's `X-Telegram-Bot-Api-Secret-Token` header before processing an update. The shared public
URI builder also uses `identity.service.public-url` for browser action links; keep the webhook secret in the
deployment secret source and never version it.

The webhook endpoint acknowledges all input with `204`; invalid input is deliberately not distinguishable to
the sender. Operators must configure an externally reachable HTTPS base URL before relying on Telegram phone
sharing.

The API-key filter's Telegram-webhook exemption uses the request path relative to the servlet context.
For `server.servlet.context-path=/identity`, requests to
`/identity/api/v1/telegram/webhooks/{applicationUniqueId}` reach Telegram-secret validation without an
application API key; application endpoints and lookalike webhook paths still require an API key. Configure
`identity.service.public-url` with the externally visible prefix so webhook registration uses the correct
public URL. Context-path routing is covered by
[`ApiKeyAuthenticationFilterTest`](../../../identity-rest-server/src/test/java/vg/identity/rest/server/security/ApiKeyAuthenticationFilterTest.java)
and
[`TelegramWebhookContextPathIntegrationTest`](../../../identity-integration-tests/src/test/java/vg/identity/rest/TelegramWebhookContextPathIntegrationTest.java).

Phone-normalization rejection logs contain only a reason code and input-shape diagnostics: whether the
first non-whitespace character is `+`, the digit count, whether letters are present, and whether formatting
characters (whitespace, parentheses, hyphens, or dots) are present. Parsing failures use libphonenumber's
error type; parsed-but-rejected numbers use its possible-number failure reason or `INVALID_NUMBER` when
the length is plausible. Neither raw input, parsed phone objects, nor parsing exceptions are logged.
Missing or blank inputs are rejected without a normalizer log. These diagnostics do not change validation
or cause Identity to infer a default country. Coverage is in
[`PhoneNumberNormalizerTest`](../../../identity-logic/src/test/java/vg/identity/service/PhoneNumberNormalizerTest.java)
and
[`TelegramPhoneWebhookServiceIntegrationTest`](../../../identity-logic/src/test/java/vg/identity/service/TelegramPhoneWebhookServiceIntegrationTest.java).

`TelegramBot.telegramBotId` is validated through `getMe` and stored alongside the token in the encrypted
application payload. Supplied bot IDs are overwritten by the validated ID. Ingress derives its expected
secret using that application's currently stored bot ID; a bot replacement invalidates the previous bot's
secret immediately after commit, including secrets derived from the previous master secret.

`TelegramBotLookupService` centralizes stored bot deserialization and lookups by application `UniqueId`
or case-insensitive bot username. Username lookup also returns the stored public bot URI for login and
action links. Authentication and phone handling use this read-only service directly; administration and
webhook registration reuse its decoder for application entities they already loaded, without an extra
query. The lookup has no dependency on application administration or webhook registration, avoiding a
service cycle. Backfill persistence remains owned by webhook registration. Parsing failures expose only
a stable error message, not a cause containing decrypted payload fragments.

The SET command contains only `applicationUniqueId` and configures that application's current bot. The
worker loads the stored bot ID and token, public URL, active secret, and maximum connections when executing.
It skips deleted applications, non-Telegram applications, and bots without a stored ID. Queued commands
created before a bot replacement also configure the current bot; multiple pending commands may repeat
the same webhook setup. Setup fails if webhook registration is disabled or the public URL is unavailable.
Telegram errors are sanitized before the worker logs or persists them.

Changing the token on the same bot skips deletion. Replacing the bot synchronously deletes the previous
webhook before saving the new configuration; deletion `401` is non-blocking, while other failures abort the
update. A bot without a stored ID has no Identity-owned webhook, so deletion and old-token lookup
are skipped on both bot replacement and application deletion. Successful external deletion cannot roll back
if the database transaction subsequently fails; retry the application update to recover. No token snapshots
are stored in command payloads.

### Background administration inspection and toggle

`TelegramWebhookRegistrationService` owns inspection and manual changes as well as lifecycle registration.
`TelegramApiClient.getWebhookInfo` uses the existing Telegram transport configuration; `TelegramService`
validates the response and exposes only its URL to the registration service. The URL must match the generated
Identity URL exactly (including any external context prefix). That observation establishes neither the
configured webhook secret nor successful delivery. Inspection does not log or display returned URLs.
Successful webhook registration logs Identity's configured callback URI at INFO; bot tokens, webhook
secrets and raw remote errors remain excluded from logs and UI messages. Service failures have sanitized
stable messages without a remote-error cause.

The frontend's Spring-managed `telegramWebhookExecutor` has two workers and a bounded queue of 100.
Its abort rejection policy is non-blocking and never executes rejected work on the submitting UI thread.
`TelegramWebhookUiTasks` captures the submitting caller's security context for domain authorization and
restores/clears the worker context after execution. One refresh task queries bots sequentially and publishes
each result; individual explicit toggles are separate tasks. Telegram calls never hold the Vaadin UI/session
lock. Callbacks capture the UI at submission and enter it through `UI.accessLater` with safe detach handlers.
A successful toggle that changes the application version also reloads that application on the worker.
Its callback replaces the cached application and grid row without starting another webhook inspection;
the same generation and operation checks discard obsolete model reloads.

The UI uses cached states and refresh-generation/per-application operation identifiers to reject stale
callbacks. Obsolete refresh batches stop before their next call; queued obsolete toggles do not start,
while an already-started mutation can complete remotely. Temporary one-second polling is active only
while this tab has pending work, restoring the previous interval on completion, deactivation or detach
(retaining any pre-existing faster polling). Polling delivers callbacks and is not a Telegram refresh timer.

Manual deletion does not drop pending updates and treats Telegram `401` as failure; lifecycle cleanup
continues ignoring `401` for revoked old credentials. A toggle requires the displayed application version
to match before acting and confirms its outcome with another inspection. It is not automatically retried.
Manual enabling commits a retrieved bot ID before registering the webhook, because Telegram may start
delivering updates immediately. Registration failure leaves the validated ID stored for an explicit retry.
Deletion is remote-only, with no schema change or persistent disable flag: existing bot saves, queued
commands and startup re-registration may restore the webhook.

Coverage: [`TelegramWebhookAdministrationIntegrationTest`](../../../identity-logic/src/test/java/vg/identity/service/TelegramWebhookAdministrationIntegrationTest.java),
[`TelegramWebhookIndicatorsTest`](../../../identity-frontend-vaadin/src/test/java/vg/identity/frontend/vaadin/admin/TelegramWebhookIndicatorsTest.java),
and [`TelegramWebhookUiTasksIntegrationTest`](../../../identity-frontend-vaadin/src/test/java/vg/identity/frontend/vaadin/service/TelegramWebhookUiTasksIntegrationTest.java).

### Emergency webhook-secret rotation

During an emergency rotation, configure the old value as the optional previous secret. While it is present, Identity
accepts headers derived from either secret. Identity rejects a misconfigured value rather than trimming and changing
key material.

Set the startup re-registration option to `true` for one rollout while the webhook feature and public URL are
configured. After the application is ready, it re-applies Telegram's webhook configuration for
every stored Telegram-bot application using the active secret. The operation is idempotent and keeps no
database rotation state, so an operator may repeat the rollout after a failed attempt. It fails rather than
silently skipping the operation when webhook registration is unavailable. Turn the startup option back off
after successful re-registration, retain the previous secret for the selected grace period, then remove it.

Bulk re-registration also resolves missing bot IDs with `getMe` and persists them in the encrypted payload
before applying the bot-bound secret. Bots without a stored ID cannot authenticate webhook deliveries.
Deployments upgrading from application-only secret derivation must explicitly re-register existing bot
webhooks; the previous-master-secret setting does not enable acceptance of the old derivation format.

Lifecycle and transaction behavior are covered by
[`TelegramWebhookLifecycleIntegrationTest`](../../../identity-logic/src/test/java/vg/identity/service/TelegramWebhookLifecycleIntegrationTest.java).

## Related contracts

See [Identity administration](../logic/identity-administration.md),
[Authentication and recovery](../logic/authentication-and-recovery.md), and the
[Application API](../api/identity-application-api.md).
