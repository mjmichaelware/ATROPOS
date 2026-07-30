# ATROPOS Director Swarm State

This is a restart handoff for the Director swarm. `AGENTS.md`, Source Docs, and the completion blueprint remain authoritative. This file records durable checkpoint state only; live file territories are assigned per batch and are never persisted here.

## Current checkpoint

- Checkpoint: Phase 11 / Checkpoint 1 self-build acceptance
- Status: active, runtime proof pending
- Goal: durable Phase 11 natural-language self-host loop
- Goal thread: `019fa720-b8b1-77f3-9724-b7faa43816bc`
- Required chain: intent -> context pack and attestation -> bounded agency -> isolated territory mutation -> independent verification -> evidence/git proof -> optional safe promotion -> restart recovery -> automatic continuation

## Restart protocol

1. Read root `AGENTS.md`.
2. Read this file.
3. Read only the paths named by the current batch atom and the root tree export for discovery.
4. Reconstruct active claims, DAG node, territory, evidence pointers, and next action from durable core stores; do not infer state from chat.
5. Assign fresh ephemeral territories for the next batch.
6. Run the 5-writer/5-reader swarm and append one batch ledger row after overlap review.

## Truth boundary

- No compile, test, package, install, or runtime proof is implied by code-only progress.
- A nonzero exit, missing evidence, failed swap, truncation, self-approval, or policy/territory refusal is never VERIFIED.
- Tree export refresh is reserved for whole canonical phase completion or an explicit Human Owner request.
- This state file is not source authority and must not override immutable invariants.

## Last closed batch

- Batch: director-swarm-phase11-001
- Static result: `git diff --check` passed
- Runtime result: not executed; operator verification remains required
- Swarm result: five writer lanes and the forward scout returned; remaining reader reports were not available from the initial thread-limit response
- Follow-up result: delayed W1, W2, and R1 returned; promotion overlap was reviewed and merged without duplicate credit
- Follow-up result: delayed W3 provenance changes were reviewed; bounded pack hashing was corrected with a fixed-width canonical placeholder, and bare self-host command entry now delegates to the production runner
- Follow-up result: candidate-JAR promotion now requests the explicit `test` gate before `jar`; omission is rejected by the builder contract
- Follow-up result: installed proof harness now requires both tasks and a non-empty source `git status` mutation before recording PASS
- Follow-up result: repair verification stdout/stderr are now redacted at the provider prompt boundary
- Follow-up result: runtime startup now composes crash recovery with one automatic self-host `recoverAndContinue` attempt per process
- Follow-up result: bounded proof controls can cap advances for a kill/restart run and are forwarded by the installed proof harness
- Follow-up result: `scripts/selfhost-restart-proof.sh` now exercises kill -> restart -> recover with durable artifact checks
- Follow-up result: both installed proof harnesses now require populated provenance, redaction, and evidence-hash fields
- Follow-up result: self-host worktree mutation now binds both requested and observed paths to DAG `expectedOutputs` before merge
- Follow-up result: safe JAR promotion now verifies non-empty backup and target postconditions before reporting success
- Follow-up result: safety hard-fail variants, promotion evidence authorization, failed-swap terminal truth, provider envelope provenance, truncation refusal, restart identity, and real candidate-JAR proof harnesses were hardened in subsequent batches
- Follow-up result: installed and restart proof harnesses now require ordered safety -> Director -> completion gate -> swap evidence; direct shell swaps return typed unsupported
- Next action: reconcile remaining compile-contract risks and run operator-focused installed-runtime verification
