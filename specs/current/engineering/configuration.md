# Configuration reference

This is the authoritative reference for configuration properties contributed by Identity modules. Properties use
[Spring Boot external configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html), so
they can be supplied as application properties, YAML, environment variables, or command-line arguments.

Do not put secret values in source-controlled configuration. Use the deployment's secret source for encryption keys,
webhook secrets, and REST-client API keys.

This reference intentionally excludes infrastructure properties owned by Spring Boot or its dependencies, such as
`spring.datasource.*`, `spring.mail.*`, and `spring.liquibase.*`.

## Implementation and verification

- Core and action-token properties are bound by
  [`EncryptionProperties`](../../../identity-logic/src/main/java/vg/identity/EncryptionProperties.java),
  [`EmailProperties`](../../../identity-logic/src/main/java/vg/identity/EmailProperties.java), and
  [`IdentityActionTokenProperties`](../../../identity-logic/src/main/java/vg/identity/IdentityActionTokenProperties.java).
- Telegram validation and request serialization are covered by
  [`TelegramWebhookSecretServiceTest`](../../../identity-logic/src/test/java/vg/identity/service/TelegramWebhookSecretServiceTest.java)
  and [`TelegramApiClientTest`](../../../identity-logic/src/test/java/vg/identity/service/TelegramApiClientTest.java).
- REST server and typed REST-client binding are covered by
  [`IdentityRestApiPropertiesBindingTest`](../../../identity-rest-server/src/test/java/vg/identity/rest/server/IdentityRestApiPropertiesBindingTest.java)
  and [`IdentityRestClientAutoConfigTest`](../../../identity-rest-client/src/test/java/vg/identity/rest/IdentityRestClientAutoConfigTest.java).

## Core services

| Property | Default | Requirements | Effect |
| --- | --- | --- | --- |
| `identity.encryption.blind-index-key` | none | Required, secret, nonblank | HMAC key for blind indexes over encrypted values. |
| `identity.encryption.current-key-id` | none | Required integer `0..255`; must name an entry in `identity.encryption.keys` | Selects the key used for new encryption. |
| `identity.encryption.keys.<id>` | none | Required nonempty keyring; each entry is a Base64-encoded 32-byte AES key, with `<id>` in `0..255` | Decrypts existing values and supplies the active encryption key. Retain old entries while their ciphertext remains. |
| `identity.email.from` | none | Required valid, nonblank email address | Sender address for Identity email. |
| `identity.service.public-url` | empty | Absolute public origin when absolute browser action links or Telegram webhooks are needed | Base URL used by the Vaadin action-link builder and Telegram webhook registration. |
| `identity.command.worker.fixed-delay-ms` | `5000` | Spring scheduled fixed-delay value in milliseconds | Delay between command-worker polls. |

## Action tokens

| Property | Default | Requirements | Effect |
| --- | --- | --- | --- |
| `identity.action-token.verify-email-base-url` | `/verify/email/` | Nonblank | Path or base URL used for email-confirmation links. |
| `identity.action-token.reset-password-base-url` | `/reset/password/` | Nonblank | Path or base URL used for password-reset links. |
| `identity.action-token.expires-in` | `1d` | Spring `Duration`, nonnull | Lifetime of a confirmation, reset, or Telegram-binding action. |
| `identity.action-token.request-cooldown` | `5m` | Spring `Duration`, nonnull | Minimum interval between action-token requests for an email. |
| `identity.action-token.telegram-start-app-param` | `startapp` | Nonblank | Telegram deep-link query parameter that carries an action key. |
| `identity.action-token.reset-rate-limit.max-requests` | `10` | Integer, nonnull | Password-recovery requests allowed per client within the configured window. |
| `identity.action-token.reset-rate-limit.window` | `10m` | Spring `Duration`, nonnull | Window for the password-recovery client rate limit. |

## Telegram

| Property | Default | Requirements | Effect |
| --- | --- | --- | --- |
| `identity.telegram.bot.name` | empty | Registered Telegram bot username for Telegram login and Telegram links | Enables plain Telegram login and Telegram links in confirmation flows. A blank value leaves those paths unavailable. |
| `identity.telegram.bot.auth-date-ttl` | `PT1H` | Spring `Duration` | Maximum age accepted for Telegram Mini App `auth_date` data. |
| `identity.telegram.webhook.enabled` | `false` | Boolean | Enables queued registration and validation of Identity-owned Telegram webhooks. |
| `identity.telegram.webhook.secret` | empty | Required when webhook support is enabled; secret with 32-256 characters from `A-Z`, `a-z`, `0-9`, `_`, `-` | Active master secret used to derive the application-and-bot-bound Telegram webhook header token. |
| `identity.telegram.webhook.previous-secret` | empty | Optional; when present, same format as `secret`; secret | Previous master secret accepted during an emergency rotation. |
| `identity.telegram.webhook.max-connections` | `2` | Integer `1..100` | Maximum concurrent webhook deliveries requested from Telegram for each application bot. |
| `identity.telegram.webhook.reregister-on-startup` | `false` | Boolean | Re-registers every stored Telegram-bot webhook with the active derived secret after application startup. Use for a rotation rollout, then return it to `false`. |

When enabled, a missing or invalid webhook secret prevents startup. A missing `identity.service.public-url` leaves
webhook scheduling disabled; explicit bulk re-registration and queued SET commands for a current bot with a stored ID
fail instead of silently skipping work. Queued commands use the configuration current at execution time. See
[Telegram bot webhooks](engineering-notes.md#telegram-bot-webhooks) for the webhook route and rotation procedure.

## REST API server

| Property | Default | Requirements | Effect |
| --- | --- | --- | --- |
| `identity.rest.api.enabled` | `false` | Boolean | Exposes the API-key-protected `/api/**` REST surface when `true`. |
| `identity.rest.api.audit.flush-interval` | `PT10M` | Spring `Duration` | Frequency for flushing aggregated successful API-authentication counters. |
| `identity.rest.api.audit.max-counter-keys` | `10000` | Integer | In-memory cap for distinct successful-authentication counter keys before excess keys use the overflow bucket. |

## Trusted embedded application mode

| Property | Default | Requirements | Effect |
| --- | --- | --- | --- |
| `vg.identity.embedded.application-unique-id` | empty | Optional positive Identity application identifier | Supplies the trusted embedded application when no authenticated principal exists. It never overrides an existing non-application principal. |

## REST client library

These properties configure the `identity-rest-client` library in a consuming application, rather than the Identity
server.

| Property | Default | Requirements | Effect |
| --- | --- | --- | --- |
| `vg.identity.rest-client.base-url` | `http://localhost:8080` | Absolute HTTP(S) URL without credentials, query, or fragment | Identity service URL used by the typed REST client. |
| `vg.identity.rest-client.api-key` | none | Required nonblank secret | API key sent by the typed REST client on every request. |
| `vg.identity.rest-client.connect-timeout` | `5s` | Positive Spring `Duration` | HTTP connection timeout. |
| `vg.identity.rest-client.read-timeout` | `5s` | Positive Spring `Duration` | Per-request response-read timeout. |

## Related contracts

- [Engineering notes](engineering-notes.md) — runtime entry points, Telegram webhook behavior, rotation procedure,
  and verification.
- [Authentication and recovery](../logic/authentication-and-recovery.md) — action-token, Telegram authentication,
  and recovery behavior affected by these properties.
- [Application API](../api/identity-application-api.md) — REST API enabled by `identity.rest.api.enabled`.
