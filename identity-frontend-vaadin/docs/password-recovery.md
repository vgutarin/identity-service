# Password recovery and initial setup

Current contracts:

- [Authentication UI](../../specs/current/ui/authentication.md) — routes and user outcomes.
- [Authentication and recovery](../../specs/current/logic/authentication-and-recovery.md) — token,
  validation, consent, and throttling rules.
- [Configuration](../../specs/current/engineering/configuration.md#action-tokens) — property defaults.
- [Runtime limitations](../../specs/current/engineering/engineering-notes.md#authentication-runtime)
  — per-instance rate limiting and browser-session invalidation.

Invitations use email-confirmation links for initial setup; password recovery uses reset links.
Both are browser flows. Historical design: [feature 002](../../specs/002-password-recovery-setup/spec.md).
