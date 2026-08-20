# identity-logic

The domain and business-logic layer of **identity-service**. It owns the identity model
(users, applications, workspaces, roles and permissions), the authorization engine, PII
encryption, and the integrations that back authentication flows (email confirmation and
Telegram Mini App login).

The module is packaged as a Spring Boot **auto-configuration library** (`java-library` +
`maven-publish`): it contributes no controllers or UI. Frontends and transport layers
(`identity-rest-server`, `identity-frontend-vaadin`) depend on it and drive its services.

---

## Position in the project

```
identity-api            → shared contracts (IdentityPrincipal, CurrentUserService)
identity-logic          → THIS MODULE — domain model, services, persistence, security
identity-rest-server    → REST controllers/OpenAPI over the logic
identity-rest-app       → runnable Spring Boot app bundling the REST server
identity-rest-client    → generated/typed client for the REST service
identity-integration-tests → cross-module end-to-end tests
identity-frontend-vaadin→ Vaadin admin & auth UI over the logic
```

`identity-logic` depends only on `identity-api` and infrastructure libraries; it has no
dependency on any transport or UI module.

## Tech stack

| Concern            | Choice                                                    |
| ------------------ | --------------------------------------------------------- |
| Language / runtime | Java 21                                                   |
| Framework          | Spring Boot 4.1                                            |
| Persistence        | Spring Data JPA + Hibernate, MySQL, Liquibase migrations  |
| Security           | Spring Security (method security, Argon2 password hashing)|
| Crypto             | BouncyCastle — AES-256/GCM field encryption, HMAC-SHA256  |
| Mapping            | MapStruct (entity ↔ domain model)                         |
| Boilerplate        | Lombok                                                     |
| JSON               | Jackson (`tools.jackson`)                                  |
| IDs                | `vg.unique-id` (`unique-id-jpa-lib`)                       |

---

## Package layout

```
vg.identity
├─ IdentityLogicConfig        // @Configuration: component scan, JPA, method security, scheduling, beans
├─ IdentityLogicAutoConfig    // @AutoConfiguration entry point (registered via META-INF/spring/…AutoConfiguration.imports)
├─ EncryptionProperties       // identity.encryption.*
├─ EmailProperties            // identity.email.*
├─ IdentityActionTokenProperties // identity.action-token.*
│
├─ model            // domain objects & enums (the API surface services return)
│  ├─ access        //   Permission — canonical permission-name constants
│  ├─ application   //   TelegramBot config value objects
│  └─ user.channel  //   channel value objects (email, …)
├─ entity           // JPA @Entity classes (persistence model)
├─ repository       // Spring Data repositories
├─ mapper           // MapStruct mappers (entity ↔ model)
└─ service          // business services (the module's public behavior)
```

---

## Core domain concepts

### Principals: users and applications

Every actor is an `IdentityPrincipal` backed by a shared `identity_principal` row (shared
primary key). Two principal types exist:

- **User** (`IdentityUser`) — a human account with a username, Argon2-hashed password,
  authorities, and contact channels. Usernames are canonicalized (trimmed, lower-cased)
  and may not contain `:`.
- **Application** (`IdentityApplication`) — a machine principal, currently specialized for
  Telegram bots. Registered under an absolute URI name (`https://t.me/<botname>`).

The `:`-free rule for usernames keeps them disjoint from URI-named applications in the
shared principal-name index, so the two namespaces can never collide.

### Workspaces and applications

`IdentityWorkspace` is the top-level access scope below the global level. Applications live
inside a workspace. Resources form a hierarchy that authorization walks upward to the owning
workspace when resolving permissions.

### Roles, permissions and role templates

- **Permission** — a fine-grained capability string, e.g. `user.create`, `workspace.read`,
  `app.update`, `role.delete`. The canonical set is declared in
  [`model/access/Permission.java`](src/main/java/vg/identity/model/access/Permission.java).
- **Role** (`IdentityRole`) — a workspace-scoped named bundle of permissions.
- **Role template** (`IdentityRoleTemplate`) — a reusable, workspace-independent blueprint.
  Creating a workspace seeds roles from the available templates.
- **System roles** (`IdentityUserSystemRole`) — global roles: `OWNER` (implicitly holds
  every permission) and `VERIFIED_USER`.

### Channels

`IdentityUserChannel` links a user to an identity on an external channel
(`EMAIL`, `TELEGRAM_USER`). Channel identifiers are stored encrypted plus a blind-index hash
for lookup, and carry a verified state (`verifiedAt` / `isVerified()`). Personal-data consent is
a user-level attribute (`identity_user.consent_to_keep_personal_data_at`), not a channel one.

### Application-scoped user claims

Identity-service also acts as an OIDC-like claims provider for registered applications. When an
application authenticates one of its end-users (today via a Telegram Mini App `initData` proof at
`POST /api/v1/applications/me/authentications/telegram`), it receives an
`IdentityApplicationUserPrincipal(applicationUniqueId, identityUserUniqueId /* OIDC sub */,
claimsByScope)` — a stable subject id plus that user's **application-scoped claims grouped by
scope** (`Map<scope, Set<claim>>`).

These claims are **application-local and deliberately separate from the platform authorization
model above**: they are plain strings the application interprets in its own domain, are never
loaded into Spring `GrantedAuthority`, and are never consulted by `AuthorityChecker`. A claim
value that happens to equal a platform permission string (e.g. `app.update`) confers no platform
authority. Scopes are free-form; the reserved `permissions` scope
(`IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE`) is the conventional default for
application-permission strings.

- **Grants** live in `identity_application_user_claim`, keyed by
  `(application, user, scope, claim)`. Scope and claim strings are **interned per workspace** in
  `IdentityWorkspaceScopeClaimDictionaryEntity` (encrypted at rest with a blind index) and
  referenced by id, so a string is stored once regardless of how many users or applications share
  it.
- **Membership** (`identity_application_user`) records which users have authenticated for an
  application (with `lastAuthenticatedAt`), letting workspace administrators enumerate an
  application's users.
- **Management** is done by workspace administrators — never by an authenticated application —
  through `IdentityApplicationClaimService` (`grantClaim`/`revokeClaim`/`getUserClaims`), gated by
  the `app.claim.create` / `app.claim.delete` / `app.read` permissions, and surfaced in the Vaadin
  admin UI (`admin/application-claims`). Claim values are normalized (trimmed + lower-cased).

### Action tokens

An `IdentityActionToken` is a short-lived, single-use, UUID-keyed link that lets an
**unauthenticated** user prove control of an identity out of band. Three action types exist:
`CONFIRM_EMAIL`, `BIND_TELEGRAM`, and `RESET_PASSWORD`. Tokens honor a configurable expiry and
request cooldown, and confirming one can chain a follow-up action (e.g. email confirmation that
also binds a Telegram channel).

`IdentityActionTokenProcessorService` consumes a token atomically under a pessimistic lock — so a
single-use token cannot be redeemed twice by concurrent requests.

### Password recovery & reset

Password recovery is an action-token flow that authorizes a password change out of band rather
than through the caller's own authority:

- `IdentityActionTokenService.requestPasswordReset(email, clientKey)` issues a single-use
  `RESET_PASSWORD` token and enqueues a recovery email — **only** when the email is a *verified*
  channel attached to an existing user. The response is deliberately uniform whether or not the
  address is known, so the endpoint cannot be used to probe for accounts.
- The request surface is unauthenticated and public, so it is guarded twice: a DB-backed per-email
  `request-cooldown`, and an in-memory per-client `RequestRateLimiter` (see
  [`RequestRateLimiter.java`](src/main/java/vg/identity/service/RequestRateLimiter.java)) that
  bounds requests per client key (typically IP) within a window.
- `IdentityActionTokenProcessorService.resetPassword(actionKey, rawPassword)` validates and
  consumes the token, then sets the new password. Password strength is enforced by
  [`PasswordPolicy`](src/main/java/vg/identity/model/PasswordPolicy.java), the single source of
  truth shared by the frontend Binder validator and the server-side safety net (min length + at
  least one lowercase, uppercase, and digit). The same flow also backs **initial password setup**.

### API keys

An `IdentityApiKey` is an opaque, long-lived credential issued to an **application** principal so
non-interactive clients can authenticate without a Mini App proof. `IdentityApiKeyService`:

- **Issues** keys (`issueForApplication`, gated by `app.update`) as `<uuid>.<secret>`, returning the
  raw value exactly once in an `IssuedIdentityApiKey`. Only a SHA-256 hash of the secret is
  persisted (see [`OpaqueKey`](src/main/java/vg/identity/service/OpaqueKey.java)) — raw key material
  is never stored.
- **Authenticates** a presented value (`authenticate`) with a constant-time hash comparison, checking
  the key is unrevoked, unexpired, and belongs to an `ACTIVE` application principal, and returns an
  `IdentityApiKeyPrincipal` (which carries **no** key material and **no** authorities).
- **Lists / revokes** keys and can `extractKeyId` from a raw value for audit logging without exposing
  the secret. Keys carry a `label`, `createdAt`, `expiresAt`, and optional `revokedAt`.

### Commands (async job queue)

`IdentityCommand` is a database-backed job. Producers `enqueue` a typed command
(currently only `SEND_EMAIL`) with a JSON payload; a scheduled worker claims and executes it
out of band, transitioning it through `QUEUED → RUNNING → COMPLETED | FAILED` with optimistic
locking. This decouples slow work (sending email) from request threads.

---

## Authorization model

Method security is enforced with `@PreAuthorize` on service methods, evaluated against the
current principal resolved from the Spring `SecurityContext`. Two complementary styles are
used:

- **Scope-aware checks** via the `authorityChecker` bean:
  `@authorityChecker.hasAuthority(#resourceId, 'workspace.update')` (permission names are
  dot-separated — see [`Permission.java`](src/main/java/vg/identity/model/access/Permission.java)).
  The checker walks the resource path up to its workspace and asks the role-assignment repository
  whether the principal holds the permission at any level. Global `OWNER`s pass everything.
- **Coarse system-role checks**: `hasRole('OWNER')`, used for role/permission/template
  administration and authority assignment.

`AuthorityChecker` intentionally talks to repositories directly (not to secured services) to
avoid circular authorization dependencies.

---

## Security & PII protection

`EncryptionService` provides field-level protection for personally identifiable data:

- **Encryption** — AES-256/GCM with a versioned keyring. Each ciphertext is stamped with the
  id of the key that produced it, so keys can be rotated: add a new key, point
  `identity.encryption.current-key-id` at it, and old data stays readable until re-encrypted.
- **Blind index** — HMAC-SHA256 hashes let encrypted columns (usernames, emails, Telegram
  ids) remain searchable for equality lookups and unique constraints without decrypting.
  The blind-index key rotates independently of the encryption keyring.
- **Passwords** — hashed with Argon2 via a `DelegatingPasswordEncoder`. Strength is enforced by
  `PasswordPolicy` (shared by the frontend validator and the server-side check).
- **API keys** — issued as opaque `<uuid>.<secret>` values; only a SHA-256 hash of the secret is
  stored, and presented secrets are verified in constant time (`OpaqueKey`).

---

## Integrations

### Telegram

Supports Telegram Mini App login and account binding:

- `TelegramApiClient` / `TelegramService` — resolve and validate a bot via the Telegram Bot
  HTTP API (`getMe`).
- `TelegramAuthenticationService` — validates Mini App `initData`: parses params, verifies
  the HMAC-SHA256 signature against the bot token (constant-time compare), enforces an
  `auth_date` TTL, and extracts the deep-link `start_param`.
- `TelegramLoginService` — orchestrates the full login flow: action-based (confirm email /
  bind Telegram, enforcing personal-data consent) vs. plain login, returning
  `AUTHENTICATED | GREETING | CONSENT_REQUIRED | FAILED`.

### Email

- `EmailService` — sends text/HTML messages through an optional `JavaMailSender`; also
  exposes email validation used to decide whether a username is an email address.
- `ConfirmEmailMailFactory` — builds the bilingual (Ukrainian/English) confirmation email
  from HTML templates under [`resources/templates/email`](src/main/resources/templates/email),
  choosing a Telegram+web or web-only variant.
- `ResetPasswordMailFactory` — builds the bilingual "reset your password" email from
  `reset-password.html.template`. The subject lives in a leading `<!-- subject: … -->` header and
  the link is a `${webUrl}` placeholder, so wording/markup can be edited without touching code.
  There is no Telegram variant — a password reset is always a web link.

---

## Configuration

All properties are bound through `@ConfigurationProperties` and enabled by the module's
auto-configuration.

### `identity.encryption` — [`EncryptionProperties`](src/main/java/vg/identity/EncryptionProperties.java)

| Property         | Description                                                              |
| ---------------- | ------------------------------------------------------------------------ |
| `blind-index-key`| Secret HMAC-SHA256 key backing the deterministic blind index.            |
| `current-key-id` | Id (0..255) of the key that encrypts new data; must exist in `keys`.     |
| `keys`           | Keyring: `id → Base64 32-byte AES-256 key` (`openssl rand -base64 32`).  |

### `identity.email` — [`EmailProperties`](src/main/java/vg/identity/EmailProperties.java)

| Property | Description                              |
| -------- | ---------------------------------------- |
| `from`   | Sender address (validated `@Email`).     |

### `identity.action-token` — [`IdentityActionTokenProperties`](src/main/java/vg/identity/IdentityActionTokenProperties.java)

| Property                     | Default            | Description                                      |
| ---------------------------- | ------------------ | ------------------------------------------------ |
| `verify-email-base-url`      | `/verify/email/`   | Base path for email-confirmation links.          |
| `reset-password-base-url`    | `/reset/password/` | Base path for password-reset links.              |
| `expires-in`                 | `1d`               | Action-token lifetime.                           |
| `request-cooldown`           | `5m`               | Minimum interval between token requests (per email). |
| `telegram-start-app-param`   | `startapp`         | Telegram deep-link parameter carrying the id.    |
| `reset-rate-limit.max-requests` | `10`            | Max password-recovery requests per client/window. |
| `reset-rate-limit.window`    | `10m`              | Window for the per-client recovery rate limit.   |

### Other tunables

| Property                                   | Default | Description                              |
| ------------------------------------------ | ------- | ---------------------------------------- |
| `identity.command.worker.fixed-delay-ms`   | `5000`  | Poll interval of the command worker.     |

---

## Usage

Because the module ships as Spring Boot auto-configuration, adding it to the classpath is
enough — `IdentityLogicAutoConfig` imports `IdentityLogicConfig`, which enables the
component scan, JPA repositories, entity scan, method security, and scheduling.

```groovy
dependencies {
    implementation "vg.identity:identity-logic:0.0.1-SNAPSHOT"
}
```

Provide the required datasource, mail, and `identity.*` properties, then inject the services
(e.g. `IdentityUserService`, `IdentityWorkspaceService`, `IdentityActionTokenService`).

The module provides a host-relative `IdentityActionLinkBuilder` by default; a frontend can
override it with an absolute-URL implementation (as the Vaadin module does) simply by
declaring its own bean.

---

## Persistence

Schema is managed by Liquibase at
[`resources/db/liquibase/001-identity-db-init.yaml`](src/main/resources/db/liquibase/001-identity-db-init.yaml).
Principal tables:

```
identity_principal            identity_role                 identity_action_token
identity_user                 identity_role_permission      identity_command
identity_user_channel         identity_role_template        identity_workspace
identity_user_system_role     identity_role_template_permission  identity_workspace_user_channel
identity_user_resource_permission  identity_role_assignment  identity_permission
identity_application           identity_application_user     identity_api_key
identity_workspace_scope_claim_dictionary   identity_application_user_claim
```

### Workspace membership

Workspaces are linked to user channels rather than directly to users. `IdentityWorkspaceService.addUser`
creates or reuses an EMAIL channel; an unknown email remains a pending membership until its confirmation
action succeeds, at which point the user is provisioned and attached to that channel. Existing users are
attached immediately, and an unverified email channel receives the normal confirmation action. Use
`addChannel(workspaceId, channelId)` to attach an existing verified Telegram channel (or an existing email
channel), and `getUserChannels(workspaceId)` to render pending versus verified memberships.

---

## Testing

The module has extensive unit and Spring integration tests (`*IntegrationTest`) covering
services, security enforcement, persistence, and encryption. Integration tests run against
MySQL with Liquibase applied.

```bash
# Full module suite
./gradlew :identity-logic:test

# A single test class while iterating
./gradlew :identity-logic:test --tests vg.identity.service.IdentityUserServiceTest
```

See the repository-level `TESTING.md` for the project's overall testing approach.

---

## Build

```bash
./gradlew :identity-logic:build          # compile + test + jar
./gradlew :identity-logic:publishToMavenLocal   # publish the library artifact
```
