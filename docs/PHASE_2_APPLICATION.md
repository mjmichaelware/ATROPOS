# SpecGraph Foundry Phase 2

## Goal

Turn the verified backend into an authenticated,
mobile-first hosted application without changing the
authority, research, planning, export, routing, or execution
semantics proven by backend v1.0.1.

## Workstream 1 — Application API

- Authenticate Supabase users.
- Resolve the authenticated owner ID.
- Reject anonymous requests.
- Enforce project ownership through the existing RLS model.
- Add stable JSON request and response contracts.
- Add health and version endpoints.
- Add complete API integration tests.

## Workstream 2 — Project Workspace

- Create and list projects.
- Select an active project.
- Display project readiness.
- Display source, atom, research, graph, plan, export, and
  execution counts.

## Workstream 3 — Source Workspace

- Upload text and supported source documents.
- Preserve byte-complete source authority.
- Display hashes, byte ranges, line ranges, sections, and
  provenance.
- Run ingestion and atom extraction.

## Workstream 4 — Research Workspace

- Display atoms and required research dimensions.
- Display open, resolved, and not-applicable dimensions.
- Claim and complete research tasks.
- Display supporting evidence and conclusions.

## Workstream 5 — Planning Workspace

- Display the authority graph.
- Display the acyclic execution graph.
- Show blocked and ready nodes.
- Synthesize, verify, and inspect plan versions.

## Workstream 6 — Handoff Workspace

- Configure ATROPOS integration bindings.
- Generate deterministic export bundles.
- Verify export artifacts and checksums.
- Start execution runs and ingest runtime receipts.
- Display rejection, tamper detection, and final verification.

## Workstream 7 — Mobile Interface

- Mobile-first navigation.
- Accessible project dashboard.
- Source and provenance viewer.
- Atom and research panels.
- Graph visualization.
- Readiness and execution status.
- Export and handoff controls.

## Release Target

Phase 2 is complete when an authenticated user can move from
source upload through verified ATROPOS handoff using the hosted
application without direct database or terminal access.
