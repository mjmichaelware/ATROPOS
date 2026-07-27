# UI Parity Blockers

Generated: 2026-07-27T01:25:34.640399+00:00 · Batch A

A blocker is any condition preventing a row from reaching
`INDEPENDENTLY_VERIFIED`. Blockers are never silently closed.

## B-001 — No ATROPOS target surface for non-terminal clients (HARD)

OpenCode ships desktop, web app, console, session-ui and an HTTP server
(~1,000 UI source files at the pinned commit). ATROPOS has **none** of these —
no package.json, no TSX, no HTML, no Android manifest, no server.

Affected rows: 6 (`BLOCKED_NO_TARGET_SURFACE`).

Closing this is not UI work; it is building 3 client applications and a
server/API layer from zero. **Requires an explicit product decision** on whether
ATROPOS is to become multi-client at all. Until that decision, "every applicable
surface" correctly resolves to the terminal client only.

## B-002 — Referenced governance documents do not exist (HARD)

The mission designates these as authority inputs to inspect before editing. None
are present anywhere in the repository at `b4c0f5b31b37735eccd9b6418ec95689b1b23a68`:

- `ATROPOS_COMPLETION_BLUEPRINT_DAG`
- Source Docs 1–2 / Source Document Map
- `AGENTS` / Agent Playbook
- handoffs, ledgers, atoms export
- context-sovereignty / workbench / live-preview requirement docs

Consequence: ATROPOS-only surfaces (RUN, GOALS, DAG, SOURCE AUTHORITY,
HIERARCHY/TERRITORY, QUEUE/DAEMON, CONTEXT SOVEREIGNTY, MEMORY, EVALUATION,
APP FACTORY, PREVIEW/INSPECTOR, ARTIFACTS) have **no specification to build
against**. Designing them from inference would fabricate authority state — which
the mission explicitly forbids. **Requires the documents, or an explicit
instruction to author specs first.**

## B-003 — Export target unavailable in this environment (SOFT)

The mission requires export to `~/storage/downloads/ATROPOS_OPENCODE_FULL_UI_PARITY_<TS>/`
followed by `termux-media-scan -r`. This session runs in a remote Linux
container, not Android/Termux; `~/storage` and `termux-media-scan` do not exist.

Mitigation applied: all artifacts are committed **into the repository** at
`docs/ui-parity/`, so they reach the phone via `git pull` and can be exported
there with a one-line copy. No evidence is lost.

## B-004 — Undiscoverable and dead routes in ATROPOS today (REAL DEFECT, open)

`CommandRouter` handles verbs absent from `CommandRegistry`, so they never
appear in `/help` or autocomplete:

`/assets`, `/ci`, `/ops`, `/security`, `/swarm`, `/tests`

Additionally `/swarm` is a **dead route** — it is routed but only ever replies
`swarm endpoint is not bound`.

This violates the mission's own "no dead routes / no missing commands" quality
bar. Recorded here rather than fixed in Batch A, because Batch A is
inventory-only and the fix touches `CommandRegistry`/`CommandRouter`, which are
outside a reference-lock batch.

## B-005 — Single-session completion is not achievable (HARD, structural)

Batches A–J against 161 applicable rows, each requiring 13 lifecycle gates
including independent adversarial verification, is on the order of **2,000+
verification events**. This cannot complete in one session at any context
length. Batch A is delivered complete; B–J are not started. Progress is
checkpointed in `ui-parity-checkpoint.json`.
