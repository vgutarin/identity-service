# Specifications

[Current documentation](current/README.md) describes the implemented system. Numbered feature directories
preserve historical designs and task records.

## Documentation authoring and maintenance

| Location under `current/` | Owns |
| --- | --- |
| `ui/` | Routes, interactions, visible states, navigation, and platform constraints. |
| `logic/` | Domain language, models, authorization, invariants, lifecycle, and consistency. |
| `api/` | HTTP/Java contracts, validation, errors, serialization, and compatibility. |
| `engineering/` | Configuration, technical mechanisms, operations, and build/test facts. |
| `decisions/` | Architectural rationale and decision history. |
| `open-decisions.md` | Unresolved decisions, bugs, implementation work, and verification gaps. |
| `templates/` | Document scaffolds; maintenance instructions belong here in this README. |

1. Identify affected current contracts during planning; update them alongside behavior, interface,
   configuration, or data-flow changes.
   Documentation is part of completion, not a follow-up.
2. Describe implemented behavior in present tense. Keep proposals, TODOs, migration history, retired
   behavior, historical comparisons, roadmaps, and alternatives out of UI/logic/API contracts, including
   indexes and shared documents. Move useful rationale to decisions and pending work to open decisions.
3. Verify claims against source and tests: names, routes, fields, validation, permissions, persistence,
   and transaction boundaries. Record gaps in open decisions; do not document an intention as a guarantee.
4. Give each rule one authoritative home and link consumers to it. Keep READMEs focused on entry points
   and usage; avoid duplicating contracts, configuration tables, and implementation walkthroughs.
   UI specifications reference logic/API contracts; logic/API specifications remain independent of UI consumers.
5. Group documents by capability or domain. Use the UI/logic templates, omitting inapplicable sections
   and replacing every placeholder. Make rules independently checkable against source, tests, or behavior.
6. Use Domain-Driven Design to describe existing responsibility/context, language, entities/value objects,
   identity, commands/queries/results, lifecycle, invariants, authorization, interactions, and actual
   transaction/persistence boundaries. Do not invent aggregates, bounded contexts, or domain events to
   fill a template. Workspace ownership does not establish a single aggregate; scheduled events are not
   automatically domain events.
7. UI contracts cover actors, routes/navigation, journeys, controls, outcomes, implemented loading/empty/
   success/validation/error/access states, and accessibility/responsiveness/localization/platform constraints.
   Use shared foundations and a compact shared-interactions document when useful; split components only
   for size or independent maintenance. Link domain rules and source/tests; omit CSS and pixel inventories.
8. Use Diátaxis to keep reference contracts, how-to guidance, decision explanations, and tutorials distinct without
   creating parallel trees or empty documents. Prefer a short existing document over a new feature file.
9. Preserve historical feature paths and meaning; repair moved links when needed. Consolidate duplicate
   current guidance without leaving compatibility symlinks or competing authoritative copies.
10. Close resolved open entries, retaining significant rationale in an ADR. Record operational surprises
    in engineering notes. Describe historical changes without relying on branch-specific commit hashes.

Before completion, check coverage, source/test references, links/anchors, current-state wording, and
agreement between UI outcomes, API responses, and domain authorization. Review meaning, not just wording.
Documentation-only edits need
no application tests; logic changes follow [AGENTS.md](../AGENTS.md#definition-of-done-mandatory).

### Architecture decision records (ADRs)

Use an ADR for a significant, lasting tradeoff, such as authorization boundaries, data storage/deletion,
external integrations, or documentation organization. Routine fixes and implementation details usually
need no ADR.

- Keep one decision per `current/decisions/NNNN-short-kebab-case-title.md`, using the
  [ADR template](current/templates/adr.md). Start at `0001`, then allocate one above the highest existing
  ADR ID; never reuse or renumber published IDs. Feature-directory numbering is separate.
- Record status, creation date, decision date when resolved (`YYYY-MM-DD`), decision makers, context,
  alternatives, choice, consequences, and contract/evidence links. Explain constraints and why the choice
  wins, including costs, risks, and disadvantages. Keep alternatives in the same record and avoid
  implementation walkthroughs.
- Existing unnumbered records remain supporting history, not automatically accepted ADRs. An extracted
  ADR links to its source; do not invent original approval or decision dates.

| Status | Meaning |
| --- | --- |
| Proposed | Editable until explicitly accepted or rejected by the user/project decision maker. |
| Accepted | Approved by the user/project decision maker; record date and decision makers. |
| Rejected | Not adopted; retain reason and rejection date. |
| Superseded | Replaced by an accepted ADR; retain the original and add date and reciprocal links. |

Code or a written proposal does not establish approval; acceptance does not establish implementation.
Preserve accepted/rejected context, decision, and rationale. Minor factual/link corrections and status
metadata updates are allowed; never silently rewrite the historical choice. A changed choice requires
a new ADR. On replacement, mark the old record Superseded, link both records, and append a dated
replacement explanation without deleting the original context or decision. A proposal supersedes
nothing until accepted.

ADRs explain choices and may contain historical context, alternatives, and proposals; they stay outside
current-only UI/logic/API contracts. An ADR does not replace those contracts or make a proposal current
behavior. Update contracts alongside implementation and link relevant ADRs; engineering guidance links
to ADRs for rationale instead of copying it.

Open architectural choices link to proposed ADRs. Keep implementation and verification work open until
completed even when its ADR is accepted. Before completing ADR work, verify numbering,
status/dates/ownership, alternatives, consequences, evidence, and reciprocal supersession links.
