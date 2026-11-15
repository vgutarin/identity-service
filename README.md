# identity-service

Identity management for users, applications, workspaces, permissions, and Telegram-bot messaging.
Includes a Vaadin administration UI, an optional REST API, and embedded Java services.

## Documentation

- [UI usage](specs/current/ui/administration.md) and [sign-in/recovery](specs/current/ui/authentication.md).
- [Application API](specs/current/api/identity-application-api.md) — HTTP, typed client, and Java messaging.
- [Current specifications](specs/current/README.md) — domain rules, configuration, and operations.
- [Open work](specs/current/open-decisions.md) and [saved messaging review](MESSAGING_IMPLEMENTATION_REVIEW.md).
- [Testing rules](TESTING.md) and [contribution instructions](AGENTS.md).

## Run and verify

```sh
./gradlew :identity-frontend-vaadin:bootRun
./gradlew :identity-rest-app:bootRun
./gradlew test
```

Configure the datasource, encryption, and mail using the
[configuration reference](specs/current/engineering/configuration.md). Integration tests require Docker;
see [build notes](specs/current/engineering/engineering-notes.md#build-and-module-layout).

Applications using `identity-rest-client` supply:

```properties
vg.identity.rest-client.base-url=https://identity.example.com
vg.identity.rest-client.api-key=${VG_IDENTITY_REST_CLIENT_API_KEY}
```

Keep the API key in the application's secret source. The client sends it automatically.
