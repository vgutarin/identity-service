# Agent Instructions

## Scope Restrictions

- Do not use anything from `tango-cli` (Jira, Confluence, GitLab, Allure, Slack, BigQuery, Coralogix,
  RAG, skills registry, service ownership, etc.) for this project.
- Do not use the knowledge-storage for this project.

## Project Testing Approach

Read `TESTING.md` before adding or changing tests.

## Session Tooling

At the start of a session, check which MCP/tool integrations are available through tool discovery. Use them deliberately when they are a better fit than shell/file edits.

## Refactoring

For Java/Kotlin symbol renames, prefer IntelliJ/IDEA MCP rename refactoring over manual text edits when renaming classes, methods, fields, variables, or other programmatic symbols.

## Project Stack

The current project uses Vaadin 25.2.2

## Verification

Prefer targeted Gradle test runs while iterating, for example:

```sh
./gradlew :identity-logic:test --tests vg.identity.service.SomeServiceTest
```

Run the broader module test suite when changes touch shared persistence, Spring configuration, Liquibase, or security behavior:

```sh
./gradlew :identity-logic:test
```
