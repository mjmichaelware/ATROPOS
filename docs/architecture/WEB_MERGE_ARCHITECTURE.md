# ATROPOS Web Merge Architecture

Status: canonical architecture record for `STRICT-44-WebMergeArchitecture`.

## Runtime ownership

`apps/web` is the sole ATROPOS web runtime root. The web client is a projection
surface over the JVM engine bridge; it does not own command execution, durable
project state, policy, verification, territory, or evidence decisions.

The former nested path `apps/specgraph-foundry/apps/web` is not a runtime root.
SpecGraph is a developer tool mounted below `/developer/specgraph` inside the
same web application. Its graph transform, planning, execution receipts, and
redaction modules remain reusable implementation modules, while the visible
developer-tool views are composed under the canonical shell.

## Engine boundary

`src/main/kotlin/atropos/bridge/BridgeRoutes.kt` is the HTTP route owner. It
exposes typed routes for health, answers, projects, commands, vocabulary,
approvals, messages, sessions, queue activity, evidence, files, thinking,
authority, storage, governance, checkpoint, and SSE events. Route handlers
delegate to projections and existing stores. No bridge route accepts shell
text, an argv, or a client-selected filesystem command.

The bridge therefore consumes engine state and typed requests. It does not
reimplement the CLI router or create a second event, session, queue, evidence,
or policy system.

## Command and menu vocabulary

`src/main/kotlin/atropos/bridge/menu/HelpRegistry.kt` is the bridge's menu/help
projection owner. It consumes the canonical `CommandRegistry` for command
entries, groups, and help, and declares the smaller set of safe route-backed
actions available to a remote surface. `/shell`, `!command`, and `/cd` remain
CLI-only and must not be exposed through the bridge menu or route table.

## Surface parity

`src/main/kotlin/atropos/core/parity/SurfaceParityProbe.kt` compares shared
capability vocabulary and route-backed menu integrity across CLI and bridge.
Parity is intentionally intersection-based: the bridge may expose less than
the local CLI, but shared capabilities must retain the same identity and
meaning. `forbiddenOnPort()` is the release-blocking check for operating-system
command surfaces.

## Developer tools and view transitions

The canonical web application owns the shell, projects, work, conversations,
files, agents, models, automation, history, settings, and hidden developer
tools container. SpecGraph views are reachable only under the developer-tools
prefix. Evidence, approval, activity, and session state are read through the
bridge contracts so a view transition preserves the same project identity,
status vocabulary, and evidence references.

## Acceptance predicates

The architecture is compliant when:

1. `apps/web` is the only active web runtime root.
2. SpecGraph is reachable only as a developer-tool route under
   `/developer/specgraph`.
3. bridge routes delegate to engine owners and expose no shell or raw command
   surface.
4. bridge menus are derived from the canonical help/command registry.
5. `SurfaceParityProbe.danglingActions()` and `forbiddenOnPort()` are empty.
6. shared surface capabilities retain the same identity and gate/evidence
   outcomes.

This document describes the implementation already present in the tree; it is
not a second web product specification.
