# Batch 8 — territory at dispatch: HARD STOP

**Priority attempted:** #1 — preventive territory at dispatch.
**Status:** blocked on one architectural decision. No code written.

Batch 7 delivered what Batch 8 was waiting on: every `ActionProposal` now
carries an `ActionActor`, and `targetPaths` were already present. The Director
pass then found a second, different gap.

## What exists

- `TerritoryAssignment(ownerId, ownerRole, allowedPrefix, allowedFilePatterns, deniedPatterns, expiresAt, readOnly)`
- `TerritoryAssignment.allows(path)` — real prefix + denied-pattern + expiry logic
- `TerritoryService.getForOwner(ownerId)`, `allows(id, path)`, `checkViolation(...)`
- `TerritoryStore` persists to `.atropos/territory/assignments.jsonl`

The matching logic is real and usable. Nothing needs to be built there.

## The blocker: nothing grants territory, and grants cannot address actors

Territory is created in exactly one place:

```
HierarchyCommand.kt:132 →  /hierarchy territory assign <ownerId> <role> <prefix>
```

An operator, by hand. No DAG run, agent run, patch, or verification ever
requests or receives an assignment.

Worse, the two identity models do not meet:

| | shape | lifetime |
|---|---|---|
| `ActionActor.HierarchyNode.identity` | `dag-executor:node-7`, `patch:20260727-...`, `verify:<patchId>` | **ephemeral, per work item** |
| `TerritoryAssignment.ownerId` | operator-chosen free string | **durable, granted ahead of time** |

An operator cannot pre-assign territory to a patch id that does not exist yet.
So "resolve territory by actor" has nothing to resolve against.

## Why each way forward is barred

1. **Enforce as specified** — every `HierarchyNode` action refused until an
   assignment exists. Correct posture, but since nothing grants assignments,
   this bricks every DAG node, patch apply, repair and verification on the
   default path. That is a functional break of the running CLI, not a bounded
   batch.

2. **Match on `ownerRole` instead of `ownerId`** — plausible, and the field
   already exists. But no roles are assigned either, so the outcome is
   identical to option 1 until someone grants them. It also silently changes
   what `TerritoryAssignment` means.

3. **Let the node's own declared `territory` be its grant** — `DagNode.territory`
   is self-declared, so a node would authorise itself. That is allow-everything
   wearing a checker's uniform, and is explicitly forbidden.

4. **Seed a default root assignment** — a literal allow-everything default.
   Explicitly forbidden.

Options 3 and 4 are the stub/theater paths the no-stub law bars. Options 1 and 2
require a decision that is not mine: **who grants territory, keyed on what, and
at what point in a run.**

## The decision needed

> When a hierarchy node is dispatched, where does its territory come from?

Concretely, one of:

- **(a) Role-scoped standing grants.** Territory is assigned to roles
  (`dag-executor`, `patch`, `verify`, `repair`) once, by the operator; a node
  inherits its role's territory. Requires: matching on `ownerRole`, and a
  documented bootstrap set. Nodes are refused until the operator grants.
- **(b) Grant-on-dispatch from a parent.** The dispatcher (DAG run, agent run)
  holds a territory and issues a narrowed child assignment per node, using the
  existing `parentTerritoryId` field. Requires: deciding who holds the root
  grant and how narrowing is validated — this is the model `parentTerritoryId`
  was clearly designed for.

(b) matches the existing schema better and keeps grants non-self-issued, but it
needs a root holder defined.

## Scope, once decided

Small. `BoundedAgencyGate` consults `TerritoryService` for `HierarchyNode`
actors, refusing any proposal whose `targetPaths` are not all covered, and
recording a `TerritoryViolation` — which also feeds `DirectorService.observe`,
delivering the "Director-level drift detection" half of priority #1 for free.

## Also blocked by the same decision

`DagExecutionService.executeCheck` is constant-true — `POLICY_CHECK`,
`SECRET_CHECK` and `TERRITORY_CHECK` all report `"check passed"` without
checking. `POLICY_CHECK` and `SECRET_CHECK` are implementable now
(gate evaluation and `RedactionFilter` respectively); `TERRITORY_CHECK` is
blocked on the decision above. Recorded rather than half-fixed.
