# AGENTS.md — ATROPOS External Agent Control Document

**Authority:** Source Docs 1–4 · 100% Completion Blueprint · Core/HOE/Phase20 Gap Maps · Hierarchy Research · 2026-07-29 export (1475 files)

**Purpose:** Single file every external coding agent / CLI / swarm must read before any work and must update after every coherent batch.

**Location:** Repo root (`AGENTS.md`). Optional mirror: `docs/AGENTS.md`.

**Rule:** This file is living. Original baselines never change. Every agent appends timestamped progress. Fake progress is forbidden.

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
   - After each batch: update this file, then immediately start the next open atom.
6. **No fake VERIFIED.** Nonzero compile/test exit, missing evidence, or self-approval may never be reported as complete.
7. **Non-duplication law.** Extend or compose existing semantic owners. Never create a second DAG, verifier, territory system, memory root, provider registry, or lakehouse.
8. **Original Source Docs 1–3 are immutable.** New capability = proposal → accepted amendment path (Phase 20). Do not silently edit original authority.
9. **Evidence over narrative.** Every claim of progress must name paths, line deltas, and which acceptance predicate moved.
10. **Update this file after every coherent batch.** See §4.

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
| 0 | Baseline Lock | 75% |
| 1 | Provider Activation Doctor | 80% |
| 2 | Provider Transport | 70% |
| 3 | Quota / Route Truth | 75% |
| 4 | Secret / Security | 85% |
| 5 | Provider Fixture Matrix | 65% |
| 6 | DLOI Source Router | 80% |
| 7 | AST Symbol Graph | 50% |
| 8 | Deterministic Verifier | 80% |
| 9 | Persistent Memory | 60% |
| 10 | Execution Policy | 55% |
| 11 | Self-Build Loop | 65% |
| **Phases 0–11 aggregate** | | **~70%** |

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

## 2. PROGRESS LEDGER (append-only)

Every agent, after a coherent batch that moves a measurable acceptance predicate, appends one row:

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

### 2026-07-29T21:40Z · Agent: Claude (claude-opus-5, Claude Code) · Batch: p11-interactive-compile-gate

- Paths touched:
  - `AGENTS.md` (+327 / new; Baseline 42 control document installed at repo root)
  - `docs/ATROPOS_OPERATING_RULES.md` (renamed from `AGENTS.md`, 0 content change — prior operating laws preserved verbatim)
  - `src/main/kotlin/atropos/core/verification/GovernedCompileGate.kt` (+114 new)
  - `src/main/kotlin/atropos/core/verification/GovernedCompileGateModels.kt` (+25 new)
  - `src/main/kotlin/atropos/core/agent/SelfHostRunProofModels.kt` (+64 new)
  - `src/main/kotlin/atropos/core/agent/SelfHostRunProofBuilder.kt` (+88 new)
  - `src/main/kotlin/atropos/cli/commands/SelfHostRunProofRenderer.kt` (+55 new)
  - `src/main/kotlin/atropos/core/agent/SelfHostAutonomousRunner.kt` (+74/-6)
  - `src/main/kotlin/atropos/core/agent/SelfHostGoalService.kt` (+12/-2)
  - `src/main/kotlin/atropos/core/agent/SelfHostAutonomousRunModels.kt` (+7/-1)
  - `src/main/kotlin/atropos/core/agent/AgentRunRepoStatus.kt` (+21/-4)
  - `src/main/kotlin/atropos/core/verification/VerifiedCompletionGate.kt` (+11/-10)
  - `src/main/kotlin/atropos/cli/commands/SelfHostCommand.kt` (+5/-1)
  - `scripts/selfhost-installed-proof.sh` (+32)
  - `src/test/kotlin/atropos/core/verification/GovernedCompileGateTest.kt` (+106 new)
  - `src/test/kotlin/atropos/core/agent/SelfHostRunProofBuilderTest.kt` (+216 new)
  - `src/test/kotlin/atropos/cli/commands/SelfHostRunProofRendererTest.kt` (+115 new)
- Atoms / phases affected: §3 priority 1 (Phase 11 interactive self-build path). Phase 10 touched only by removing one ungoverned tool call.
- Predicate moved: **`NL → mutate → compile gate → git status` is now true inside the JAR.** Three things were false before this batch: (a) no compile gate existed anywhere in the self-host run chain — the bootstrap DAG mutated Kotlin and went straight to jar promotion; (b) the operator-facing run output never showed mutated paths, hashes, or `git status`, so the predicate could only be checked from outside the JAR; (c) `/agent self-host resume` failed on its second advance — `advanceNextResumableGoal` built the context envelope *before* node selection, so preflight refused an envelope naming the node that had just finished. All three are now closed and asserted.
- % delta: Phase 11 65% → 78% (+13). Phases 0–11 aggregate ~70% → ~71%. Horizon II 55% → 57.2%.
- Why the delta is justified: the installed-runtime proof (`scripts/selfhost-installed-proof.sh build/libs/ATROPOS.jar`) exits 0 with five new assertions that did not exist before — the JAR's own stdout must contain `compile gate: passed=true exit=0`, `verdict: VERIFIED`, zero `[UNMET]` predicates, a `git status:` section, and the mutated marker proven present with a sha256. Recorded at `.atropos/self-hosting/proofs/phase11-installed-runtime-proof.properties`: `compileGate=compile gate: passed=true exit=0 command=./gradlew compileKotlin`, `runProofVerdict=verdict: VERIFIED`, `markerSha256=fba38072b69e1565…`, `mutationStatus=?? …SelfHostCradleRuntimeState.kt | ?? …SelfHostCradleRuntimeStateTest.kt`. Focused suites: 117 tests across `atropos.core.agent.SelfHost*`, `atropos.cli.commands.SelfHost*`, `atropos.core.verification.*`, `atropos.core.policy.*` and the sandbox proof, 0 failures, plus 15 new tests for the compile gate, proof builder, and renderer. `./gradlew compileKotlin` exits 0. Not claimed: Phase 11 is **not** 100% — batch planning breadth and repair-loop coverage are untouched, and live device install remains operator deployment work. The compile gate's own predicate is proven against the sandbox's stub `gradlew`; the real `./gradlew compileKotlin` exit was verified separately in this repo, not inside the sandbox.
- New overall estimate: ≈ 43% (42.4 → 42.9). Arithmetic: only Phase 11 moved, +13 points; §1 does not state Horizon II's internal weighting, so it is treated as even across its six phases → +13/6 = +2.2 on the horizon → 55% → 57.2%; weighted II = 25% × 57.2% = 14.3 (was 13.8), so 18.0 + 14.3 + 2.2 + 4.0 + 1.2 + 3.2 = 42.9.
- Fingerprints (sha256, first 12): `GovernedCompileGate.kt` d076577e94fe · `GovernedCompileGateModels.kt` aa705b9051c0 · `SelfHostRunProofModels.kt` e20f6efb2c74 · `SelfHostRunProofBuilder.kt` 8298587081c0 · `SelfHostRunProofRenderer.kt` ecfe20bdd045 · `GovernedCompileGateTest.kt` 9a49e7c8acdd · `SelfHostRunProofBuilderTest.kt` 60be46f002c0 · `SelfHostRunProofRendererTest.kt` 71a9f386e7e7

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

---

## 4. MANDATORY UPDATE PROTOCOL

After every coherent batch:

1. List exact files written or modified and approximate line deltas.
2. State which acceptance predicate(s) moved from false/PARTIAL to true or higher %.
3. Append a Progress Ledger row (§2) with timestamp.
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
- After the batch is written and this file is updated, immediately continue to the next open atom. No "awaiting confirmation".

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

## 10. FINGERPRINT OF THIS DOCUMENT

- Created: 2026-07-29
- Baseline source: full export juxtaposition + gap maps
- Overall original baseline: ≈ 42%
- Next agent: read §0–§3, execute highest open atom, append §2, continue.

End of AGENTS.md
