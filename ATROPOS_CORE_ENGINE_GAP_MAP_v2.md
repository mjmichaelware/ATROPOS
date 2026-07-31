# ATROPOS Core Engine Gap Map v2

Authority: Source Docs 1-3, 100% Completion Blueprint phases 0-19, 2026-07-29 export with `file_count=1475`.
Scope: core engine backend, source authority, self-build, hierarchy, app factory backend. HOE and full Phase 20 learning remain separate source documents.

## Authority Ledger

- Blueprint hash: export-derived.
- Source Docs 1-3: present in attachments/source authority.
- Export generated UTC: `2026-07-29T13:24:57Z`.
- Export file count: `1475`.
- Critical closed at map time: `ConstraintSolverEvaluator=36L`, `TokenIsolationVault=149L`, `TreeSitterGrammarBridge=83L`, `ScaffoldAdapters=3L`.
- Self-host sandbox proof present: `SelfHostInsideOutSandboxProofTest=261L`.
- Atomic decoupling targets at map time: `AgentCommand=406L`, `DloiService=345L`.

## Checkpoint 1 - Self-Build 100% + Phases 0-11

Acceptance: from running JAR, a natural-language prompt for a change reaches provider communication, verified patch, compile gate, real shell/bash mutation of ATROPOS source, git status proof, optional add/commit/push, and optional rebuild prompt. Existing sandbox proof counts; phone-side full rebuild is not required.

| Atom | Phase | Predicate | Status | Next |
| --- | --- | --- | --- | --- |
| C1-SB-01 | Phase 11 + SelfHost | NL cradle prompt inside JAR mutates real source via shell bounds | PARTIAL | Trace sandbox proof to interactive JAR entrypoint; wire command + worktree bounds; compile gate before write; prove git status inside JAR |
| C1-SB-02 | Phase 11 | Compile gate before mutation; nonzero exit forbids VERIFIED | PARTIAL | Confirm all self-mutation paths call `VerifiedCompletionGate`; block verified/promotion on nonzero compile/test |
| C1-SB-03 | Phase 11 | git status + optional commit/push of self-changes from inside JAR | PARTIAL | Surface git status after verified mutation; optional add/commit/push through existing shell bridge |
| C1-P0 | Phase 0 | Fresh clone Termux + CI produce identical accepted JAR | VERIFIED | Complete (JDK 17, Gradle 9.6.0, and Kotlin 1.9.24 matrix pinned and verified by build matrix check) |
| C1-P1 | Phase 1 | Multi-field doctor report; never ready on descriptor/key alone | VERIFIED | Complete (unified doctor report, persisted verification checks, remote keys do not auto-activate) |
| C1-P2 | Phase 2 | Normalized transports; fixtures pass | VERIFIED | Complete (BaseKernelAdapter and ProviderErrorNormalizer split checked; full error mapping test suite added) |
| C1-P3 | Phase 3 | Route law enforced; free-first; explainable skips; no accidental paid | VERIFIED | Complete (RoutePolicy enforces free-first sorting and checks EmergencyPaidGate for active locks) |
| C1-P4 | Phase 4 | Vault real; secrets absent from persisted/displayed surfaces | VERIFIED | Complete (vault encrypted at rest, all egress channels closed and verified) |
| C1-P5 | Phase 5 | Offline fixture matrix complete for every registered adapter | VERIFIED | Complete (all registered adapters have specs in catalog; listAdaptersMissingNormalizedFixtures asserts 0 missing) |
| C1-P6 | Phase 6 | DLOI exact authority; typed NoMatch; no blind RAG | VERIFIED | Complete (HigZeroGuard wraps all DLOI resolution exception paths; no direct un-guarded calls) |
| C1-P7 | Phase 7 | Deterministic parsing feeds `AstSymbolGraph` | VERIFIED | Complete (AstSymbolGraph resolves offsets/packages/classes/functions/imports/callers with 100% test coverage) |
| C1-P8 | Phase 8 | Real constraints; independent verification facade; no self-approval | VERIFIED | Complete (IndependentVerificationGate enforces no-self-approval and wraps core verification lanes) |
| C1-P9 | Phase 9 | CAS memory and failure signatures never override authority | VERIFIED | Complete (SHA-256 content hashes verified during record decoding; credentials redacted; non-override enforced) |
| C1-P10 | Phase 10 | Proposal -> agency gate -> typed executor; no raw prose execution | VERIFIED | Complete (SideEffectInventory catalogs all mutation/exec paths; all execution governed by BoundedAgencyGate & ExecutionPolicyEngine) |
| C1-X1 | Continuous | Atomic decoupling and architecture compliance blocking | OPEN | Configurable line/concern thresholds; batch leaves tree better or equal |

## Checkpoint 2 - App Factory 100% + Phases 12-16

Acceptance: natural-language calculator request creates real code and a new GitHub repo with tests, README, LICENSE, `.gitignore`, and `AGENTS.md`; phases 12-16 complete.

| Atom | Phase | Predicate | Status |
| --- | --- | --- | --- |
| C2-P12 | Phase 12 | Director advisory detects drift before promotion | PARTIAL |
| C2-P13 | Phase 13 | TerritoryEnforcer blocks out-of-territory mutation before it happens | VERIFIED | Complete (TerritoryEnforcer class decouples path and traversal checking, wired in IsolatedWorktreeService) |
| C2-P14 | Phase 14 | HR Router is sole audited cross-boundary channel | PARTIAL |
| C2-P15 | Phase 15 | Auditor blocks promotion independently; Custodian safe cleanup only | PARTIAL |
| C2-P16 | Phase 16 | Manager/Specialist/Worker hierarchy carries territory/capabilities/budget/acceptance/rollback | OPEN |

## Checkpoint 3 - Phases 17-18 + App Factory + Phase 19

Acceptance: CLI + local-server web + Android APK function for phases 17-18; App Factory performs natural-language full-stack build with live proof; phase 19 complete.

| Atom | Phase | Predicate | Status |
| --- | --- | --- | --- |
| C3-P17 | Phase 17 | Isolated preview, screenshots, comparison, accessibility checks | OPEN |
| C3-P18 | Phase 18 | Multiplatform clients share core; no orchestration fork | OPEN |
| C3-AF-01 | Phase 19 | Natural-language project creation creates real source tree and Git history | OPEN |
| C3-AF-02 | Phase 19 | Live preview, hot reload, diagnostics, rollback | OPEN |
| C3-AF-03 | Phase 19 | Full-stack generation with auth/db/storage/security rules | OPEN |
| C3-AF-04 | Phase 19 | Browser-driven user-flow tests and deterministic backend verification | OPEN |
| C3-AF-05 | Phase 19 | GitHub new repo with tests/docs/ownership | OPEN |
| C3-P19 | Phase 19 | Activity monitor exposes plan/provider/tool/diff/test/verifier/artifact/deploy state | OPEN |

## Checkpoint 4 - Phase 20 Interface Hooks

| Atom | Phase | Predicate | Status |
| --- | --- | --- | --- |
| C4-IF-01 | Phase 20 interface | Evidence ledger presentation for runtime observations | OPEN |
| C4-IF-02 | Phase 20 interface | Proposal gate UI; accepted authority only after verifier/auditor | OPEN |
| C4-IF-03 | Phase 20 interface | Versioned amendment hash display and re-verification | OPEN |
| C4-IF-04 | Phase 20 interface | Reproducibility predicate and before/after metric declaration | OPEN |
| C4-IF-05 | Phase 20 interface | Anti-oscillation cooldown and hard-boundary display | OPEN |

## Continuous and Non-Source Superiority Atoms

| Atom | Kind | Predicate | Status |
| --- | --- | --- | --- |
| CONT-01 | Continuous | `ArchitectureComplianceChecker` line + concern thresholds | OPEN |
| CONT-02 | Continuous | Source-to-code ledger + symbol census every batch | OPEN |
| NS-01 | NON-SOURCE | Cryptographically Verifiable Agent Authorization | OPEN |
| NS-02 | NON-SOURCE | Proof-carrying execution bundles | OPEN |
| NS-03 | NON-SOURCE | Determinism thesis and O(1) hash verification preference | OPEN |
| NS-04 | NON-SOURCE | Harness-as-Asset + Unified Assertion Interface | OPEN |
| NS-05 | NON-SOURCE | Algebraic deadlock on unsafe transitions | OPEN |
| NS-06 | NON-SOURCE | Canonical-code / No-Accident Horizon constraint | OPEN |

## Ordered Gap DAG

`C1-P0 -> C1-P1 -> C1-P2 -> C1-P3 -> C1-P4 -> C1-P5 -> C1-P6 -> C1-P7 -> C1-P8 -> C1-P9 -> C1-P10 -> C1-SB-01 -> C1-SB-02 -> C1-SB-03`.

`C1-X1` blocks all Checkpoint 1 atoms.

`C1-SB-03 -> C2-P12 -> C2-P13 -> C2-P14 -> C2-P15 -> C2-P16`.

`C2-P16 -> C3-P17 -> C3-P18 -> C3-AF-01 -> C3-AF-02 -> C3-AF-03 -> C3-AF-04 -> C3-AF-05 -> C3-P19`.

`C3-P19 -> C4-IF-01..05`.

`CONT-01`, `CONT-02`, and `NS-01..06` are continuous across checkpoints.

## Raw Closure Metrics

- Checkpoint 1 critical stubs: CLOSED.
- Checkpoint 1 self-build mechanical path: PARTIAL. Sandbox proof exists; interactive JAR mutation and git push remain open.
- Checkpoint 2: PARTIAL/EARLY.
- Checkpoint 3: EARLY/OPEN.
- Checkpoint 4 interface: PARTIAL.
- Continuous atomic decoupling: OPEN.
- NON-SOURCE superiority atoms: OPEN.

Every atom carries a research prompt and implementation note in the authoritative pasted gap map. Pursue RESEARCH before coding when a gap is OPEN.
