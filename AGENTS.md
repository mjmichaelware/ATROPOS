# AGENTS.md — ATROPOS External Agent Control Document
**Authority:** Source Docs 1–4 · 100% Completion Blueprint · Core/HOE/Phase20 Gap Maps · Hierarchy Research · 2026-07-29 export (1475 files)

**Purpose:** Single file every external coding agent / CLI / swarm must read before any work and must update after every coherent batch.

**Location:** Repo root (`AGENTS.md`). Optional mirror: `docs/AGENTS.md`.

**Rule:** This file is living. Original baselines never change. Every agent appends timestamped progress to `docs/PROGRESS_LEDGER.md`. Fake progress is forbidden.

> Appended pointer (not part of the original baseline text): the previous repo-root operating rules — Source Authority Precedence, canonical phase table, formal invariants, and the operating laws (free-first route, compile cadence, territory, redaction, exact-path staging, no force push) — are preserved verbatim at [`docs/ATROPOS_OPERATING_RULES.md`](docs/ATROPOS_OPERATING_RULES.md). They remain in force as the mechanical detail behind §0, §7, and §8 of this document. Where the two ever conflict, this control document wins.

---

## 0. HARD RULES (apply to every agent — Claude, Codex, Antigravity, Cursor, Aider, OpenCode, any swarm)

1. **Read this file first.** Before writing a single line, load current percentages, open atoms, and the non-duplication law.
2. **Work like ATROPOS.** Territory, evidence, independent verification, atomic decoupling, no self-approval, no silent authority mutation.
3. **Autonomous + recursive.** Do not stop for permission, checkpoints, or "should I continue?". Stop only for the human-mandatory list (§6).
4. **Write code. Maximize decoupled files.** Primary job = write the remaining lines of code in the largest number of single-responsibility files. Do not optimize for few large files.
5. **Quota efficiency (token discipline).**
   - Open only the exact paths named in the current atom.
   - Never re-ingest the full corpus or full export.
   - Prefer existing owners; create a new file only when no existing owner can hold the responsibility.
   - One coherent batch at a time (3–8 tightly related files max unless the atom requires more).
   - After each batch: update `docs/PROGRESS_LEDGER.md`, then immediately start the next open atom.
6. **No fake VERIFIED.** Nonzero compile/test exit, missing evidence, or self-approval may never be reported as complete.
7. **Non-duplication law.** Extend or compose existing semantic owners. Never create a second DAG, verifier, territory system, memory root, provider registry, or lakehouse.
8. **Original Source Docs 1–3 are immutable.** New capability = proposal → accepted amendment path (Phase 20). Do not silently edit original authority.
9. **Evidence over narrative.** Every claim of progress must name paths, line deltas, and which acceptance predicate moved.
10. **Update `docs/PROGRESS_LEDGER.md` after every coherent batch.** See §4.
11. **The Chaos Gate (Adversarial Reliability).** Any generated state machine, WAL, or consensus module MUST include adversarial unit tests. ATROPOS must test recovery from truncated files, corrupted byte frames, and simulated network partitions.
12. **The Persistence Gate (Crash Resilience).** Any system involving file I/O or databases MUST generate a "write -> kill process -> restart -> read" integration test. In-memory-only mock tests are strictly forbidden for storage logic.
13. **Strict Serialization.** Enforce explicit raw string literals and strict byte-level encoding (standardizing `\n` vs `\\n` escaping) to prevent disk serialization mismatches during file generation.

---

## 1. ORIGINAL BASELINE PERCENTAGES (locked 2026-07-29 — do not alter)

Derived by juxtaposing the full 1475-file export against Source Docs 1–4, Blueprint phases, Hierarchy Research, and the three gap maps.

### Critical stubs

| Component | Baseline % | Evidence |
| --- | --- | --- |
| ConstraintSolverEvaluator | 85% | 36 L real filter (no longer constant-true) |
| TokenIsolationVault | 90% | 149 L + tests |
| TreeSitterGrammarBridge | 55% | 83 L; AST depth still limited |
| ScaffoldAdapters | 95% | 3 L (split/emptied) |
| ArchitectureComplianceChecker | 70% | 161 L; enforcement mode incomplete |
| **Critical-stub aggregate** | **~79%** | |

### Phases 0–11 (Checkpoint 1 — foundation + self-build)

| Phase | Canonical name | Baseline % |
| --- | --- | --- |
| 0 | Baseline Lock | 100% |
| 1 | Provider Activation Doctor | 100% |
| 2 | Provider Transport | 100% |
| 3 | Quota / Route Truth | 100% |
| 4 | Secret / Security | 100% |
| 5 | Provider Fixture Matrix | 100% |
| 6 | DLOI Source Router | 100% |
| 7 | AST Symbol Graph | 50% |
| 8 | Deterministic Verifier | 100% |
| 9 | Persistent Memory | 100% |
| 10 | Execution Policy | 100% |
| 11 | Self-Build Loop | 100% |
| **Phases 0–11 aggregate** | | **100%** |

Self-build acceptance (NL inside JAR → verified patch → compile gate → real mutation → `git status`) is **not 100%**. Sandbox proof (261 L) exists; live interactive path remains PARTIAL.

### Phases 12–16 (Hierarchy)

| Phase | Canonical name | Baseline % |
| --- | --- | --- |
| 12 | Director Advisory | 40% |
| 13 | Territory Enforcement | 70% |
| 14 | HR Router | 35% |
| 15 | Auditor / Custodian | 40% |
| 16 | Manager/Specialist/Worker | 30% |
| **12–16 aggregate** | | **~43%** |

### Phases 17–18

| Phase | Canonical name | Baseline % |
| --- | --- | --- |
| 17 | Multimodal Runtime | 25% |
| 18 | Multiplatform | 20% |
| **17–18 aggregate** | | **~22%** |

### Phase 19 — App Factory

Baseline: **~20%**

### Phase 20 — Long-horizon autonomy (implementation)

Baseline: **~12%** (architecture/gap map itself is 100% specified)

### HOE / Source Doc 4 (Presentation)

| Surface | Baseline % |
| --- | --- |
| CLI/TUI foundation | 70% |
| Web (ATROPOS-owned) | 15% |
| Android APK | 8% |
| Six continuous answers | 40% |
| Progressive disclosure | 25% |
| Evidence / trust indicators | 35% |
| Competitive targets | 25% |
| **HOE aggregate** | **~32%** |

### Weighted overall vision

| Horizon | Weight | Baseline % | Weighted |
| --- | --- | --- | --- |
| I Foundation 0–10 | 25% | 72% | 18.0 |
| II Self-build + Hierarchy 11–16 | 25% | 55% | 13.8 |
| III Multimodal + Multiplatform 17–18 | 10% | 22% | 2.2 |
| IV App Factory 19 | 20% | 20% | 4.0 |
| V Phase 20 Autonomy | 10% | 12% | 1.2 |
| HOE Presentation | 10% | 32% | 3.2 |
| **OVERALL BASELINE** | | | **≈ 42%** |

These numbers are the permanent original baseline. Later agents only append; they never overwrite this section.

---

## 2. PROGRESS LEDGER PROTOCOL

**Canonical location:** `docs/PROGRESS_LEDGER.md`

Every agent, after a coherent batch that moves a measurable acceptance predicate, MUST append their timestamped entry directly to `docs/PROGRESS_LEDGER.md`. Never append run logs or batch entries to `AGENTS.md`.

### Row format
```
### [ISO-8601 timestamp] · Agent: <name/model> · Batch: <short id>
- Paths touched: <exact paths + line deltas>
- Atoms / phases affected: <IDs from gap maps>
- Predicate moved: <what became true that was false>
- % delta: <e.g. Phase 11 65% → 72% (+7)>
- Why the delta is justified: <one tight paragraph naming evidence>
- New overall estimate: <recalculated weighted overall, or "unchanged">
- Fingerprints: <content hashes or git short-SHAs of changed files if available>
```

Oldest entries stay. Newest entries go at the bottom. Never delete or rewrite prior ledger rows.

### Disagreement markup
See `docs/PROGRESS_LEDGER.md` for the LEDGER DISAGREEMENT MARKUP PROTOCOL. When an agent disagrees with another ledger response, add a `DIRECTOR MARKUP` beneath the disputed row — never delete or rewrite.

---

## 3. CURRENT OPEN PRIORITIES (ordered)

Work the highest open atom that is not blocked. Prefer:

1. Close remaining self-build interactive path (Phase 11) so NL → mutate → compile gate → `git status` is true inside the JAR.
2. Finish critical stubs to 100% (ConstraintSolver depth, TreeSitter/AST, ArchitectureCompliance enforcement).
3. Territory + Auditor wiring (Phases 13, 15).
4. HOE CLI Antigravity-class gaps (sticky chrome, progressive disclosure, six answers always visible).
5. Phase 20 ledger substrate (evidence/memory/proposal/amendment on lakehouse CAS) only after Phase 11 is green.
6. App Factory and multiplatform only after the above.

Exact atom IDs live in:
- `docs/` or Drive: Core Engine Gap Map v2
- HOE UI/UX Gap Map v2
- Phase 20+ Architecture + Gap Map v2

Open only the paths named by the atom you are executing.

For context-efficient file discovery, read `ATROPOS_TREE_PORT_EXPORT_PATHS.md`. It is tree data, not a batch ledger, and is refreshed only after whole phase completion or explicit Human Owner request.

---

## 4. MANDATORY UPDATE PROTOCOL

After every coherent batch:

1. List exact files written or modified and approximate line deltas.
2. State which acceptance predicate(s) moved from false/PARTIAL to true or higher %.
3. Append a Progress Ledger row to `docs/PROGRESS_LEDGER.md` with timestamp.
4. Recalculate only the affected phase/aggregate percentages; leave unrelated baselines untouched.
5. If overall weighted % changes, record the new overall and the arithmetic.
6. Do not claim a phase is 100% unless its Blueprint acceptance gate is fully met and evidence exists inside the repo.

If a batch produces no measurable predicate movement, still log the paths and state "% unchanged".

---

## 5. QUOTA / TOKEN EFFICIENCY RULES

- Never dump the full export or full Source Docs into context.
- Never re-derive architecture already locked in the gap maps.
- One atom (or tightly coupled pair) per batch.
- Prefer extend-in-place over new files; new file only when the non-duplication law requires it.
- Prefer small pure functions and single-responsibility files over large mixed-concern files.
- Do not run full-project Gradle or full test suites unless the atom's acceptance predicate requires it or the human has authorized it.
- Free/local tools first. Paid providers only when explicitly unlocked.
- After the batch is written and `docs/PROGRESS_LEDGER.md` is updated, immediately continue to the next open atom. No "awaiting confirmation".

---

## 6. HUMAN-MANDATORY STOP LIST

Stop and surface a clear request to the human only for:

- Entering or rotating secrets / API keys
- Enabling paid providers or spending money
- Full Gradle build / JAR install / device-side install that the human must run
- Destructive git operations on main / protected branches
- Any action that permanently weakens an immutable invariant (authority, territory, verification, secret policy)

Everything else continues autonomously.

---

## 7. CODE STYLE FORCED ON EVERY AGENT

- Extreme per-file atomic decoupling: one file = one responsibility.
- Composition over inheritance and over monoliths.
- No file mixes presentation + decision + transport + verification.
- New code must leave ArchitectureComplianceChecker equal or better.
- All new self-build or mutation paths must go through VerifiedCompletionGate; nonzero exit forbids VERIFIED.
- Territory recorded at claim/dispatch; out-of-territory writes refused before mutation.
- Evidence (hashes, paths, gate results) produced for every completion claim.
- **The Chaos Gate:** Any generated state machine, WAL, or consensus module MUST include adversarial unit tests. ATROPOS must test recovery from truncated files, corrupted byte frames, and simulated network partitions.
- **The Persistence Gate:** Any system involving file I/O or databases MUST generate a "write -> kill process -> restart -> read" integration test. In-memory-only mock tests are strictly forbidden for storage logic.
- **Strict Serialization:** Enforce explicit raw string literals and strict byte-level encoding (standardizing `\n` vs `\\n` escaping) to prevent disk serialization mismatches during file generation.

---

## 8. RESEARCH PLANES (never collapse)

1. Ordinary NL user prompt → automatic lakehouse / DLOI retrieval only.
2. Application research document (when knowledge is insufficient) → SpecGraph or fallback ATROPOS DAG.
3. Phase 20 self-improvement → evidence → proposal → auditor → versioned amendment → Phase 11 execution.

User-app research never becomes ATROPOS law. Phase 20 never silently rewrites Source Docs 1–3.

---

## 9. WHAT SUCCESS LOOKS LIKE FOR AN EXTERNAL AGENT

- Maximum correct lines of code written in maximum properly decoupled files.
- Measurable movement of an acceptance predicate recorded in the Progress Ledger.
- This file updated with truthful deltas and fingerprints.
- No fake completion, no silent authority change, no quota waste, no unnecessary full builds.
- Next open atom started immediately.

When Phase 11 self-build is fully green, ATROPOS can begin to perform this loop on itself. Until then, external agents are the hands; this file is the contract.

---

## 10. DOCUMENT FINGERPRINT & POINTERS

- Created: 2026-07-29
- Baseline source: full export juxtaposition + gap maps
- Overall original baseline: ≈ 42%
- **Swarm contract:** `SWARM.md` (stable specialties, ephemeral territories)
- **Progress ledger:** `docs/PROGRESS_LEDGER.md` (append-only, all historical entries)
- **Tree export:** `ATROPOS_TREE_PORT_EXPORT_PATHS.md` (context-efficient file discovery)
- Next agent: read §0–§3, execute highest open atom, append to `docs/PROGRESS_LEDGER.md`, continue.

End of AGENTS.md
