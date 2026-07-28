# ATROPOS UI Phase 0 Baseline
## Authority
Source Document 4: Human Operating Environment UI/UX Architecture Specification.
## Current Proven Surfaces
### ATROPOS CLI/TUI
The repository contains a substantial Kotlin terminal presentation foundation under `src/main/kotlin/atropos/cli/ui/`.
Known capabilities include terminal rendering, responsive viewport layout, landing presentation, command palette, composer, transcript, session overview, agents, providers, quota, security, memory, verification, status, themes, design tokens, dialogs, toasts, spinners, and ANSI-safe rendering.
### ATROPOS Web
No separately owned ATROPOS web application was proven by the initial UI export.
`apps/atropos-web/` is established as the future ATROPOS-owned web surface.
### SpecGraph Web
The existing Next.js application under `apps/specgraph-foundry/apps/web/` is a substantial SpecGraph product surface.
It shall remain SpecGraph-owned and later appear beneath ATROPOS Developer Tools or a shared local gateway.
## Phase 0 Objective
Freeze the actual presentation tree, assign ownership, map Source Document 4 requirements, identify missing surfaces, and prevent ATROPOS/SpecGraph conflation before implementation.
## Phase 0 Deliverables
- unified web architecture contract
- surface ownership registry
- HOE delta register
- deterministic tracked-path inventory
- path SHA-256 fingerprints
- current Git and authority identity
- future ATROPOS web root
- shared web-contract package boundary
## Non-Goals
Phase 0 does not:
- move SpecGraph files
- rename SpecGraph routes
- create a second compiler UI
- perform a full build
- run the full test suite
- alter provider, policy, storage, or compiler behavior
- discard or stage unrelated dirty work
## Baseline Acceptance
Phase 0 is accepted when the inventory script deterministically emits:
- ATROPOS CLI/TUI paths
- ATROPOS web paths
- SpecGraph web paths
- UI parity documentation paths
- path hashes
- ownership counts
- current branch, HEAD, and dirty paths
