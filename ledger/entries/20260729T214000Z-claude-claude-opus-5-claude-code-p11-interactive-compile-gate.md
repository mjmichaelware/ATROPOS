---
timestamp: 2026-07-29T21:40:00Z
agent: Claude (claude-opus-5, Claude Code)
batch: p11-interactive-compile-gate
atoms: §3 priority 1 (Phase 11 interactive self-build path). Phase 10 touched only by removing one ungoverned tool call.
phase_deltas: 11:65:78
---
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
