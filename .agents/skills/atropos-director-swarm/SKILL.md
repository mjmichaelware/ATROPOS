---
name: atropos-director-swarm
description: Run the ATROPOS Director swarm contract across sessions with stable specialties, ephemeral territories, checkpoint-first writers, read-only inspectors, HR interrupts, and append-only evidence.
metadata:
  short-description: Durable ATROPOS Director swarm
---

# ATROPOS Director Swarm

This skill is subordinate to the repo-root `AGENTS.md`. Read that file first on every session.

## Durable operating state

Read `ATROPOS_DIRECTOR_SWARM_STATE.md` at the repo root after `AGENTS.md`. Treat it as a restart handoff, not as source authority. Refresh only at batch boundaries.

## Every batch

1. Determine the next unfinished checkpoint from `AGENTS.md`, currently Phase 11 unless the ledger proves otherwise.
2. Assign ephemeral, disjoint territories in the Director's working context. Never make them permanent law in `AGENTS.md`.
3. Run five writers and five readers in parallel. W1 and W2 remain on the active checkpoint. W3 must disclose out-of-checkpoint work. W4 only decouples. W5 tests only newly created files and owns HR interrupts. Readers never mutate product code.
4. Review overlap before accepting any shared authority-file changes.
5. Append one ledger row and refresh the durable state only after the coherent batch closes.
6. Continue to the next batch without permission-seeking unless the human-mandatory stop list applies.

## Safety

No fake VERIFIED, no self-approval, no second DAG/verifier/policy/territory/memory root, no permanent live territory in the contract, and no runtime claim without executed evidence. Human-mandatory stops remain secrets, paid unlocks, operator-required build/install, destructive protected-branch operations, and weakening immutable invariants.
