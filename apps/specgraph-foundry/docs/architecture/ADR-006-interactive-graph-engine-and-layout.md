# ADR-006 Interactive Graph Engine and Layout

Status: Accepted for implementation.

## Context

The backend already distinguishes authority-graph cycles from forbidden execution cycles, but the repository has no frontend graph engine, no deterministic client layout worker, and no accessible graph alternative. Later graph groups need a fixed interaction and layout stack that does not weaken server authority.

## Decision

- Use React Flow for interaction.
- Use ELK.js in a Web Worker for deterministic layout.
- Authority graph cycles remain legal.
- Execution graph cycles remain forbidden.
- Semantic graph truth remains server authority.
- Visual layout persistence is separate from semantic graph data.
- Client cycle checks provide feedback, but server planning and execution verification remain authoritative.
- An accessible list or table alternative is required.
- Large-graph performance fixtures are required.

## Detailed Topology or Contract

- React Flow renders node and edge interaction, selection, zoom, drag, and inspection.
- ELK.js runs in a dedicated Web Worker so layout does not block the main thread.
- Server responses supply semantic nodes, edges, statuses, and provenance references.
- Client-persisted layout stores coordinates, viewport, expansion state, and user presentation preferences separately from semantic relation records.
- Authority graph views may render cycles without treating them as validation failures.
- Execution graph views may surface client-side cycle diagnostics, but final acceptance still comes from server-side verification.
- Every graph view also exposes a list or table representation with the same node and edge information.

## Security Consequences

- Browser graph state must not mutate semantic authority without authenticated server APIs.
- Large graph payload handling requires size and parsing bounds to avoid client denial-of-service.
- Graph rendering must not expose private data through uncontrolled exports or cached payloads.

## Data/Migration Consequences

- Optional persisted layout records are separate from authority relations, plan graphs, and execution records.
- Graph fixture datasets are required to prove large-project behavior before release.

## Testing Consequences

- Tests must cover deterministic layout inputs and outputs, cycle feedback, accessibility fallback rendering, and large-graph performance fixtures.
- Server tests remain responsible for semantic cycle rejection and verification correctness.

## Operational Consequences

- Layout worker performance and memory use become observable frontend concerns.
- Large graph payloads may require incremental loading or bounded rendering in later groups.
- Accessibility support must remain feature-complete when graphical rendering degrades.

## Rejected Alternatives

- Doing all layout on the main thread: rejected because large graphs will degrade interaction.
- Treating client graph state as semantic authority: rejected because backend verification must remain authoritative.
- Requiring graphical rendering with no accessible fallback: rejected because it fails accessibility and low-power use cases.

## Dependencies on Later Groups

- Group 14 for graph foundation
- Group 15 for authority, planning, and execution graph UX
- Group 18 for accessibility completion
