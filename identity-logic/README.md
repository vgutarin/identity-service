# identity-logic

Spring Boot library for Identity's domain services, authorization, persistence, encryption, and queued
email/Telegram work. It depends on `identity-api` and infrastructure libraries; transport and UI modules
call its services.

## Integration

Add `vg.identity:identity-logic` to the classpath. `IdentityLogicAutoConfig` imports `IdentityLogicConfig`,
enabling component/entity scans, JPA repositories, method security, and scheduling. Configure the
datasource and [Identity properties](../specs/current/engineering/configuration.md), then inject the
required services. A host can override the default `IdentityActionLinkBuilder` bean.

`CurrentPrincipalService` resolves human/application callers; `AuthorityChecker` checks permissions.
`IdentityUserAuthorityService` manages user authorities. Public application entry points are
`IdentityApplicationApiService` and `IdentityApplicationMessageService`; the package-private
`IdentityMessageService` is their internal delivery core.

## Reference

- [Administration](../specs/current/logic/identity-administration.md) — workspaces, applications, users,
  channels, claims, roles, and API keys.
- [Authentication and recovery](../specs/current/logic/authentication-and-recovery.md).
- [Application messaging](../specs/current/logic/application-messaging.md).
- [Java and HTTP contracts](../specs/current/api/identity-application-api.md).
- [Engineering notes](../specs/current/engineering/engineering-notes.md) — module layout, schema,
  queue models, encryption mappings, audit logging, and Telegram operations.

## Build and test

Run from the repository root; integration tests require Docker/MySQL Testcontainers.

```sh
./gradlew :identity-logic:test --tests vg.identity.service.IdentityUserServiceTest
./gradlew :identity-logic:test
./gradlew :identity-logic:build
./gradlew :identity-logic:publishToMavenLocal
```

Follow [TESTING.md](../TESTING.md) when changing tests.
