# Authentication UI

## Purpose and actors

Anonymous users can sign in, request password recovery, follow email-verification or password-reset links,
and complete Telegram Mini App authentication. Authenticated browser users are redirected to the main
application shell.

## Routes and entry conditions

| Route | Entry condition | Outcome |
| --- | --- | --- |
| `/login` | Anonymous | Username/password sign-in and a link to password recovery. |
| `/recover/password` | Anonymous | Accepts an email address and always presents a neutral completion outcome. |
| `/reset/password/:id?` | Anonymous; action key required | Displays the reset form only for a valid reset action. |
| `/verify/email/:id?` | Anonymous; action key required | Guides consent, optional credentials, and email confirmation. |
| `/login/telegram` | Anonymous; opened as a Telegram Mini App | Reads WebApp init data and runs the Telegram login or action flow. |

The route parameter is optional only so a bare route can display a missing-link message instead of a 404.

## Interactions and outcomes

- The recovery screen submits the email with a caller key used for rate limiting. It does not reveal whether
  an account or a reset email exists.
- A valid reset link displays the password form. Successful reset invalidates the user's other registered
  browser sessions, attempts to sign the current browser in, and returns it to `/`; an invalid or expired
  link offers a new recovery request.
- Email verification first asks for personal-data consent when required. A pending user then supplies a
  display name and password; an existing user is confirmed directly. A successful result may include a
  Telegram-binding link.
- Telegram authentication retries client-side init-data collection. Depending on the validated result it
  signs in, greets an unbound user, asks for consent, asks for provisioning credentials, or shows one neutral
  failure message.

## States and platform constraints

All public authentication views display a locale picker and use the configured Vaadin localization provider.
The Telegram route loads Telegram's WebApp JavaScript and depends on a Telegram Mini App environment for
init data; a missing or invalid value is an explicit retryable state. Password rules are enforced in the UI
and again by the domain service.

## Related contracts and evidence

The governing rules are in [Authentication and recovery](../logic/authentication-and-recovery.md).

- `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/auth/LoginView.java`
- `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/auth/PasswordRecoveryRequestView.java`
- `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/auth/PasswordResetView.java`
- `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/auth/IdentityUserEmailVerificationView.java`
- `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/auth/TelegramAuthView.java`
