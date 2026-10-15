# SWARM.md — ATROPOS Director Swarm Contract
**Authority:** Derived from AGENTS.md §11 — single source of truth for swarm orchestration.

---

## DIRECTOR SWARM CONTRACT — stable specialties, ephemeral territories

### Design principle
- Specialties are permanent in AGENTS.md.
- Live territories are assigned by Director per batch only.
- Never store batch-specific file paths as permanent law in AGENTS.md.
- This keeps AGENTS.md from going stale during active coding.

### Swarm shape
- Director = main agent session
- Director is not one of the 10
- Director does not write product code except AGENTS.md ledger/contract updates
- Default swarm = 10 agents:
  - 5 writers
  - 5 readers

### Writer specialties and requirements
W1 — Checkpoint-Closer-A
- Deterministically driven by AGENTS.md + Source Docs + Core Gap Map
- Works only the next unfinished checkpoint
- Maximizes single-responsibility files
- May mutate code only inside Director-assigned ephemeral territory for this batch

W2 — Checkpoint-Closer-B
- Same checkpoint as W1
- Different seam assigned by Director each batch
- Deterministic checkpoint math, not vibes
- Maximizes single-responsibility files
- May mutate code only inside its ephemeral territory

W3 — Lead-Line-Cook
- May work mathematically high-value items outside W1/W2 scope
- May implement later-phase substrate (including Phase 20 primitives) when leverage is high
- MUST notify Director and label OUT-OF-CHECKPOINT in the same return
- Still obeys non-duplication, decoupling, and no-false-VERIFIED
- May mutate code only inside its ephemeral territory

W4 — Decouple-Scalpel
- May READ the entire codebase for mixed-concern files
- Writes pure single-responsibility splits
- Behavior-preserving extraction preferred
- No drive-by feature expansion while decoupling
- If a mixed file is touched, prefer extracting >=1 pure file or justify why not

W5 — Test + HR Autogroup
- May write tests ONLY for newly created files in the current batch
- May READ old tests for patterns/contracts
- May not broadly rewrite old tests unless required for a new pure seam, and must disclose that
- HR interrupt authority over all other sub-agents
- HR severity:
  - INFO = log only
  - REDIRECT = change a writer's seam/target
  - HALT-LANE = stop one writer immediately
- HR is the only non-Director channel allowed to redirect writers mid-batch
- Also returns cross-lane consistency notes to Director

### Reader specialties and requirements
R1 — Forward-Scout
- Rank next 2 batches toward current checkpoint
- Return atom IDs + candidate path families, not permanent path locks
- Read-only

R2 — Missed-Slice Diff
- Compare gap-map/source atoms vs tree
- Report missed slices only
- Read-only

R3 — Final-Pass Inspector
- Review all files touched this batch
- Residuals, overlap, false-success risks
- Read-only

R4 — Evidence / Provenance Scout
- Completion-claim truth gaps
- Missing hashes, gate bypasses, evidence bundle gaps, promotion honesty
- Read-only

R5 — Quota & Blast-Radius Guard
- Path discipline, overlap risk, batch admissibility, token-waste detection
- Flag batches that cross too many lanes without Director reason
- Read-only

Readers never mutate product code.

### Ephemeral territory rules
- Director assigns live territories at the start of each batch.
- Territories are not written into AGENTS.md as permanent path lists.
- Writers may not share write paths in the same batch.
- Anti-thrash: same file may not be rewritten by two writers in consecutive batches without Director merge note.
- If a writer fails, reassign only residual territory; do not redo finished pure files.

### Hard laws for every external agent / CLI
1. Read AGENTS.md before work.
2. Autonomous + recursive; no permission-seeking loops.
3. Maximize correct single-responsibility files.
4. Non-duplication always.
5. No fake VERIFIED:
   - nonzero compile/test exit
   - missing evidence
   - self-approval
   - failed swap/promotion
   - OOM / truncated build
6. Observation is not law.
7. Original Source Docs 1–3 immutable.
8. Phase 11 executes self-mutation; Phase 20 decides/amends and does not silently rewrite original authority.
9. Quota efficiency:
   - open only needed paths
   - no full-corpus re-ingest by default
   - one coherent batch at a time
   - free/local first
10. Human-mandatory stops only:
   - secrets/keys
   - paid unlock / real money
   - full Gradle/JAR/device install requiring human
   - destructive git on main/protected branches
   - weakening immutable invariants
11. W1+W2 stay on next unfinished checkpoint until green.
12. W3 outside-scope work requires Director notify.
13. W4 may read all; writes pure splits.
14. W5 tests new files only; HR REDIRECT/HALT-LANE is binding.
15. Two-batch lookahead always from R1.
16. Evidence-first close for every lane.
17. Overlap review before Progress Ledger append when shared SelfHost*/Gate*/authority files were touched by more than one writer.
18. Progress Ledger append-only; baselines never overwritten.
19. Specialty stability / territory ephemerality mandatory.
20. Any CLI/agent must be able to obey this contract from AGENTS.md alone.

### Director process each batch
1. Read AGENTS.md and determine next unfinished checkpoint mathematically.
2. Assign ephemeral territories for this batch only.
3. Spawn 10 agents with the specialties above.
4. Keep W1 and W2 on the checkpoint.
5. Allow W3 outside-scope only with notify discipline.
6. Run W4 decouple pass.
7. Run W5 new-file tests + HR monitoring.
8. Run R1–R5 in parallel.
9. Honor HR REDIRECT/HALT-LANE without collapsing the swarm.
10. Collect returns:
    - paths changed
    - new files created
    - tests for new files
    - predicate moved
    - residuals
    - W3 OUT-OF-CHECKPOINT disclosures
    - HR interrupt summary
11. Overlap review.
12. Append one Progress Ledger row:
    - ISO timestamp
    - Director/agent id
    - paths + line deltas
    - new decoupled file count
    - checkpoint/predicate moved
    - % delta only if mathematically justified
    - fingerprints/SHAs if available
    - HR summary
13. Immediately start next batch from R1 ranking + R2 misses + W4 residual decouple list + R4 evidence gaps.

### Progress Ledger row format
### [ISO-8601 timestamp] · Agent: <name/model> · Batch: <short id>
- Paths touched:
- New decoupled files:
- Atoms / phases affected:
- Predicate moved:
- % delta:
- Why justified:
- HR interrupts:
- Fingerprints:
- New overall estimate:

### Superiority requirements
- Checkpoint math > vibes
- Many pure files > few mixed files
- Coordination by state/evidence, not chatter
- Packaging/swap failure is never VERIFIED
- Phase 20 substrate may be prepared by W3; Phase 20 completion and original authority mutation are forbidden
- Token diet: path-directed reads; tight residuals; no full-repo dumps
- Blast-radius budget enforced by R5
- False-success ban is mandatory
- Works for any coding agent/CLI, not one vendor

---

*Historical swarm ledger entries (2026-07-29T19:12:00-06:00 through 2026-07-30T05:30:00Z) are preserved in `docs/PROGRESS_LEDGER.md` alongside the main progress ledger.*
