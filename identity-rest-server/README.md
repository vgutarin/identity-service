# identity-rest-server

Spring Boot library providing REST controllers, API-key authentication, and shared error handling.
`identity-rest-app` hosts it standalone; the Vaadin application can also enable it.

## Enable

Set `identity.rest.api.enabled=true`. The library default and Vaadin default are `false`;
`identity-rest-app` sets `true`. Invalid property values fail startup; the effective mode is logged.

```sh
./gradlew :identity-frontend-vaadin:bootRun --args='--identity.rest.api.enabled=true'
```

The enabled `/api/**` security chain runs before the Vaadin chain. Its API-key filter is not a global
servlet filter. The Telegram webhook uses its own secret-header validation instead of an application key.

## Call

Issue an application API key through application administration and store its one-time secret securely.
Send exactly one `X-VG-Identity-API-Key` header over HTTPS. Keys are checked on every request;
invalid, expired, or revoked keys receive a neutral `401`.

```sh
curl --fail-with-body \
  -H "X-VG-Identity-API-Key: ${IDENTITY_API_KEY}" \
  https://identity.example.com/api/v1/applications/me
```

[Application API](../specs/current/api/identity-application-api.md) defines metadata, consent-gated
Telegram authentication, phone disclosure, messaging, and errors. Identifiers are `applicationUniqueId`
and `userUniqueId`; responses use Identity's domain model.

[Configuration](../specs/current/engineering/configuration.md#rest-api-server) and
[audit operation](../specs/current/engineering/engineering-notes.md#scheduled-work-and-audit-routing)
are documented centrally.

## Verify

```sh
./gradlew :identity-rest-server:test
./gradlew :identity-integration-tests:test
```

The integration suite requires Docker/MySQL Testcontainers.
