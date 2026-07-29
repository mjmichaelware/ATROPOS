# ATROPOS Unified Web Architecture
## Status
Phase 0 architectural boundary contract.
## Primary Decision
ATROPOS and SpecGraph shall eventually operate through one local-first web host while remaining separately owned product surfaces.
## Product Identity
ATROPOS is the human operating environment.
SpecGraph is an embedded compiler, authority, research, planning, proof, and graph subsystem.
The unified host shall never make SpecGraph the primary ATROPOS information architecture.
## Target Route Ownership
| Route | Owner | Purpose |
| --- | --- | --- |
| `/` | ATROPOS | Home and active-work orientation |
| `/projects` | ATROPOS | Durable project directory |
| `/projects/:projectId` | ATROPOS | Human objective and project workspace |
| `/projects/:projectId/work` | ATROPOS | Tasks, workflows, approvals, blockers |
| `/projects/:projectId/conversations` | ATROPOS | Project conversations |
| `/projects/:projectId/files` | ATROPOS | Files and artifacts |
| `/projects/:projectId/agents` | ATROPOS | Agent responsibility and workload |
| `/projects/:projectId/models` | ATROPOS | Provider and route presentation |
| `/projects/:projectId/automation` | ATROPOS | Schedules and autonomous workflows |
| `/projects/:projectId/history` | ATROPOS | Durable project history |
| `/projects/:projectId/evidence` | ATROPOS | Human-facing evidence and verification |
| `/developer` | ATROPOS | Developer Tools entry |
| `/developer/specgraph` | SpecGraph | Embedded SpecGraph workspace |
| `/developer/specgraph/projects/:projectId/sources` | SpecGraph | Source authority workspace |
| `/developer/specgraph/projects/:projectId/research` | SpecGraph | Research workspace |
| `/developer/specgraph/projects/:projectId/graph` | SpecGraph | Authority and execution graph workspace |
| `/developer/specgraph/projects/:projectId/handoff` | SpecGraph | Export and handoff workspace |
| `/developer/specgraph/projects/:projectId/routing` | SpecGraph | SpecGraph routing workspace |
## Deployment Shape
The initial implementation may use two development processes behind one local gateway.
The final implementation may use one host process with separately compiled route modules.
The deployment choice shall not erase product ownership.
## Shared Infrastructure
The two surfaces may share:
- authentication session
- project identity
- design tokens
- typography
- theme state
- accessibility primitives
- notification transport
- command-palette protocol
- project event stream
- safe API client foundations
- route-level error and loading contracts
- redaction and secret-display rules
## Prohibited Shared Ownership
The two surfaces shall not share:
- primary navigation definitions
- domain-specific view models
- compiler authority state
- ATROPOS runtime policy state
- SpecGraph semantic graph ownership
- completion criteria
- product-specific route decisions
- mutation authority
## Integration Boundary
ATROPOS may consume bounded SpecGraph projections:
- source authority health
- extraction progress
- research readiness
- plan readiness
- graph verification state
- export availability
- execution handoff status
ATROPOS shall not duplicate SpecGraph compiler logic in its own frontend.
SpecGraph shall not redefine ATROPOS project, conversation, agent, automation, or human-control models.
## Shared Server Evolution
Stage 0: preserve current independent implementation.
Stage 1: establish shared contracts and route ownership.
Stage 2: create ATROPOS web shell.
Stage 3: mount or proxy SpecGraph beneath Developer Tools.
Stage 4: share authentication, themes, project identity, and event transport.
Stage 5: converge deployment into one local-first host while retaining module isolation.
## Acceptance
The architecture is accepted only when:
- ATROPOS primary navigation remains objective-centered.
- SpecGraph routes are reachable through Developer Tools.
- no compiler implementation is copied into ATROPOS UI.
- no ATROPOS operating model is copied into SpecGraph.
- both surfaces can evolve and test independently.
- one local launch command can eventually expose both surfaces.
