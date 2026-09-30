# Agent Instructions

## Definition of done (mandatory)

- **Keep `specs/current/` actual at all times.** Every change that alters behaviour, contracts,
  configuration, or data flow MUST update the corresponding UI, logic, API, or engineering documents
  within the same change — never as a follow-up. If no document covers the affected area yet, create
  one grouped by capability or logical domain, not by feature or ticket. Code and `specs/current/` must never drift
  apart: a change is not complete until the spec reflects it. Do not mark work done, hand off, or open a
  PR while the current specification still describes the old behaviour. Start UI and logic documents
  from `specs/current/templates/ui-contract.md` and `specs/current/templates/logic-domain.md`
  respectively, following [Documentation authoring and maintenance](specs/README.md#documentation-authoring-and-maintenance).
- **Cover every change in logic with tests.** New or changed behaviour MUST be accompanied by unit tests
  for the logic in isolation and integration tests for its wiring and interaction with collaborators (persistence,
  messaging, HTTP, Spring context), added in the same change. A change is not complete until
  both levels of tests exist and pass; do not mark work done or open a PR with untested logic.

## Scope & specs

- Work only in this repository for project tasks; never switch to or use the `knowledge-storage` repository.
- **Read and follow [Documentation authoring and maintenance](specs/README.md#documentation-authoring-and-maintenance)**
  before creating, editing, moving, or reviewing project documentation, and before planning or
  implementing changes to behaviour, contracts, configuration, or data flow that require spec updates.
  Apply its verification checklist before marking that work complete.
- Current-state behaviour lives in `specs/current/ui/`, `specs/current/logic/`, and `specs/current/api/`;
  `specs/current/engineering/` documents operational and technical mechanisms. Read the relevant contracts
  before changing behaviour in that area. A feature directory under `specs/NNN-*/`
  is primarily a decision record capturing the system as of that feature; it may be updated when genuinely needed, but
  there is no obligation to review the `specs/NNN-*/` directories, or reconcile them with later changes, just to apply a
  change.
- Before your first build or test run in a session, read `specs/current/engineering/engineering-notes.md`: build,
  test and deployment facts that are not derivable from the code and have each cost debugging time.
  `specs/current/open-decisions.md` lists work that is neither finished nor abandoned.

## Tooling

- Never use `tango-cli` for this project.
- Never use tango skills for this project.
- When suitable, use the IntelliJ MCP tools for project-aware Java and Gradle work, including searching code, opening files, running configurations, and executing tests.
- Prefer IntelliJ MCP refactoring tools for symbol/package renames when they are available and appropriate.
- When working in the `identity-frontend-vaadin` module, use the Vaadin MCP tools when available for Vaadin-specific code, UI,
  routing, and frontend verification work.

## Java conventions

- Use Lombok for boilerplate rather than writing it by hand: `@Data`/`@Getter`/`@Setter` for accessors,
  `@RequiredArgsConstructor` for dependency injection, `@Builder` with
  `@NoArgsConstructor`/`@AllArgsConstructor` on models and entities, `@Slf4j` for loggers. Two limits.
  Keep the generated member's visibility the same as the code it replaces — `@RequiredArgsConstructor`
  defaults to `public`, so a package-private constructor needs `(access = AccessLevel.PACKAGE)`. And
  leave a constructor hand-written when it does real work: validating configuration, copying an injected
  collection defensively, or enforcing a value-object invariant. Null-checking an injected Spring
  collaborator is not real work — a single-constructor `@Component` fails context startup rather than
  ever being handed a null.
- Construct instances through their builder in both production code and tests
  (`IdentityInternalUser.builder()...build()`), not through positional constructors: a positional
  call reads as a row of unlabelled values and silently reorders when a field is added.
- Prefer `UniqueId` over `long` for the identifier of any `Identifiable` instance whenever a `UniqueId` is already
  available or can be carried across the boundary. Convert to `long` only at persistence, repository, or third-party
  boundaries that require the primitive value.
- Keep package declarations, imports, filesystem paths, and Spring metadata aligned after Java package changes.

## Frontend (`identity-frontend-vaadin`)

- The `identity-frontend-vaadin` module uses Vaadin `25.2.2`; use Vaadin MCP/docs for version `25.2` when checking Vaadin
  guidance.

## Testing & verification

- Verify code changes with the narrowest relevant Gradle task first, then broaden to `./gradlew test` when the change affects multiple modules.
- Read `TESTING.md` before adding or changing tests.

## Session Tooling

At the start of a session, check which MCP/tool integrations are available through tool discovery. Use them deliberately when they are a better fit than shell/file edits.

## Refactoring

For Java/Kotlin symbol renames, prefer IntelliJ/IDEA MCP rename refactoring over manual text edits when renaming classes, methods, fields, variables, or other programmatic symbols.

## Verification

Prefer targeted Gradle test runs while iterating, for example:

```sh
./gradlew :identity-logic:test --tests vg.identity.service.SomeServiceTest
```

Run the broader module test suite when changes touch shared persistence, Spring configuration, Liquibase, or security behavior:

```sh
./gradlew :identity-logic:test
```
