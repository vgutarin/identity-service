# Authentication and recovery

## Responsibility and language

This capability authenticates local users, validates Telegram Mini App data, and completes one-time email
actions. An **action token** is a single-use, time-limited opaque key for email confirmation, Telegram
binding, or password reset. **Consent** is the recorded permission to retain personal data for a user.

## Commands and queries

- Email confirmation inspects a confirmation action, requires consent when it has not already been recorded,
  optionally provisions a new user from submitted credentials, verifies the email channel, and consumes the
  action. It may offer a Telegram-binding link when a configured bot is available.
- Password recovery accepts an email address but produces a neutral outcome. It issues a reset action only
  for a verified email channel attached to a user and applies both a client-key rate limit and a per-channel
  request cooldown. Resetting validates the token under a lock, enforces the password policy, changes the
  password, and consumes the token. A weak password leaves the token usable.
- Telegram login verifies the raw WebApp `initData` against the configured bot and its authentication-date
  lifetime. A known bound Telegram user is authenticated; an unknown user receives only a greeting. Action
  links can confirm an email or bind Telegram after consent, and may require credentials to provision a
  previously unknown identity.
- The application API has a separate Telegram flow: without explicit consent it returns a provisional
  principal and does not persist a user; with consent it resolves or creates an established user. Its exact
  request and response contract is authoritative in [Application API](../api/identity-application-api.md).
- A Telegram bot may receive a self-shared phone contact. Identity accepts it only for a private-chat update
  whose sender Telegram id equals the contact's Telegram user id and which resolves to an established,
  consented user. It normalizes the number, updates the encrypted Telegram-channel payload, and sets only
  that bot application's Telegram-phone flag (ordinal `0`) in its compact `f` application-user metadata set.
  This contact is not independently phone-verified.

## Invariants and consistency

- Raw action secrets and Telegram init data are untrusted inputs. Action-token lookup validates the opaque
  secret, action type, and expiry; state-changing processing locks the token before consumption.
- Invalid, expired, tampered, used, or wrong-type actions produce a neutral failure rather than exposing
  account or token state.
- Authentication flows record consent only when a flow successfully establishes or confirms the relevant
  identity. They must not create a user when the application API request lacks consent.
- A Telegram contact update for an unknown, provisional, malformed, third-party, or unconsented identity has
  no state change. Webhook authentication uses the application's currently stored Telegram bot ID; changing
  bots invalidates the previous bot's secret, while token rotation on the same bot retains it. Configurations
  without a stored bot ID cannot authenticate webhook updates. Repeated valid deliveries are safe because
  they replace the same global phone value and retain the same application-user metadata flag.
  Do not persist Telegram update IDs solely for this flow.
  Introduce persistent delivery deduplication before adding any non-idempotent webhook side effect, such as a
  notification, distinct audit event, counter, or workflow transition.
- Webhook processing resolves bot configuration through the shared `TelegramBotLookupService`. Missing
  applications and malformed bot payloads are ignored without marking the caller's transaction rollback-only.
  The nullable bot lookup returns null only for an absent application; the strict getter throws for absence,
  and both lookup methods reject malformed payloads.
- Issuing confirmation and recovery actions enqueues an identity command for mail delivery; delivery is
  processed asynchronously by the command worker.

## Interactions

Public browser interactions are in [Authentication UI](../ui/authentication.md). The Vaadin security
configuration establishes browser sessions after local, reset, or Telegram authentication. The REST security
chain protects the application API with an application API key, separately from browser session security.

## Evidence

- `identity-logic/src/main/java/vg/identity/service/IdentityActionTokenService.java`
- `identity-logic/src/main/java/vg/identity/service/IdentityActionTokenProcessorService.java`
- `identity-logic/src/main/java/vg/identity/service/TelegramLoginService.java`
- `identity-logic/src/main/java/vg/identity/service/TelegramAuthenticationService.java`
- `identity-logic/src/main/java/vg/identity/service/TelegramBotLookupService.java`
- `identity-logic/src/test/java/vg/identity/service/IdentityActionTokenProcessorServiceIntegrationTest.java`
- `identity-logic/src/test/java/vg/identity/service/TelegramLoginServiceTest.java`
- `identity-logic/src/test/java/vg/identity/service/TelegramBotLookupIntegrationTest.java`
- `identity-logic/src/test/java/vg/identity/service/TelegramBotLookupServiceTest.java`
- `identity-frontend-vaadin/src/test/java/vg/identity/frontend/vaadin/auth/PasswordResetViewTest.java`
