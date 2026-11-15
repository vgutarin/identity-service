# Authentication and recovery

## Responsibility and language

Authenticate local/Telegram users and complete one-time email actions. An **action token** is a
single-use, expiring opaque key for email confirmation, Telegram binding, or password reset.
**Consent** records a user's permission to retain personal data.

## Commands and queries

- Email confirmation requires missing consent, optionally provisions credentials for a new user,
  verifies the email channel, and consumes its action. A configured bot can supply a Telegram-binding link.
  Invitations use this flow for initial password setup.
- Password recovery returns a neutral outcome. It issues reset mail only for a verified email attached
  to a user, subject to per-client rate limiting and per-channel cooldown. Reset locks/validates the token,
  enforces `PasswordPolicy` (minimum length 10, lowercase, uppercase, and digit), changes the password,
  and consumes the token. Weak passwords leave it usable.
- Telegram login verifies WebApp `initData` HMAC-SHA256 signature in constant time and `auth_date`
  lifetime against the configured bot.
  Bound users authenticate; unknown users receive a greeting. Email/binding actions can require consent
  and provisioning credentials.
- Application Telegram authentication is separately consent-gated; see the
  [API contract](../api/identity-application-api.md#consent-gate-data-safety).
- Phone sharing accepts only private-chat self-contacts whose sender ID matches the contact user ID,
  resolving to an established consented Telegram user. It normalizes to E.164, updates the encrypted
  global channel phone, and enables disclosure only for that bot's application membership.
  The value is contact data, not independent phone verification.

## Invariants and consistency

- Validate action secret, type, and expiry; lock before state-changing consumption. Invalid, expired,
  tampered, used, or wrong-type actions produce neutral failures without exposing account/token state.
  Opaque secrets are hashed and verified in constant time.
- Record consent only when a flow successfully establishes/confirms identity. Application authentication
  without consent creates no user. Email actions enqueue delivery in the existing command queue.
- Invalid/unknown/provisional/unconsented or third-party contacts change nothing. Webhook authentication
  binds the application and current stored bot ID. Bot replacement invalidates the old secret;
  same-bot token rotation retains it. Bots without stored IDs cannot authenticate updates.
- Repeated valid contacts replace the same phone/disclosure flag; update IDs are not persisted.
  **Limitation:** replaying an old valid contact after revocation can re-enable disclosure. Track the
  [unresolved revocation boundary](../open-decisions.md#telegram-phone-disclosure-replay) before relying
  on revocation to reject delayed/replayed updates.
- Bot lookup and webhook failure handling follow
  [engineering notes](../engineering/engineering-notes.md#telegram-bot-webhooks).

## Interactions and evidence

[Runtime notes](../engineering/engineering-notes.md#authentication-runtime) cover per-instance throttling
and session invalidation. REST API-key security is separate from browser sessions.

- [Action issuance](../../../identity-logic/src/main/java/vg/identity/service/IdentityActionTokenService.java)
  and [processing](../../../identity-logic/src/main/java/vg/identity/service/IdentityActionTokenProcessorService.java).
- [Telegram login](../../../identity-logic/src/main/java/vg/identity/service/TelegramLoginService.java),
  [proof validation](../../../identity-logic/src/main/java/vg/identity/service/TelegramAuthenticationService.java),
  [phone acceptance](../../../identity-logic/src/main/java/vg/identity/service/TelegramPhoneWebhookService.java).
- [Action integration tests](../../../identity-logic/src/test/java/vg/identity/service/IdentityActionTokenProcessorServiceIntegrationTest.java),
  [login tests](../../../identity-logic/src/test/java/vg/identity/service/TelegramLoginServiceTest.java),
  [bot lookup integration](../../../identity-logic/src/test/java/vg/identity/service/TelegramBotLookupIntegrationTest.java).
