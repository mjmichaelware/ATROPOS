# Consolidation Status Matrix

This matrix records observed gates, not ledger claims or percentage estimates.

| Phase/atom | Claimed predicate | Code present | Focused test | Wider gate | Evidence | Truthful status | Next action |
|---|---|---|---|---|---|---|---|
| C1-P4 security | redaction, vault, secret-safe egress | yes | affected Kotlin suite PASS before current digest optimization | full Kotlin suite requires rerun | vault proof + focused report | PARTIAL | rerun focused/full Kotlin suite |
| C1-P5 providers | normalized offline fixture outcomes | yes | fixture matrix PASS | full Kotlin suite unrun | focused Gradle result | PARTIAL | run full Kotlin suite |
| C1-P7 AST | deterministic masked parse/impact | yes | parser and architecture focused PASS | full Kotlin suite unrun | focused Gradle result | PARTIAL | run full Kotlin suite |
| C1-P8 verification | independent completion and constraint refusal | yes | completion focused PASS | full Kotlin suite unrun | focused Gradle result | PARTIAL | run full Kotlin suite |
| C1-P9 memory | redacted, hashed, restart-safe durable memory | yes | LocalMemoryStoreTest 9/9 | full Kotlin suite unrun | append-path test result | PARTIAL | wider memory callers and full suite |
| C1-P10 policy | typed agency and nested-shell refusal | yes | policy focused PASS | full Kotlin suite unrun | focused Gradle result | PARTIAL | full policy callers |
| C1-SB Phase 11 | NL self-build, real mutation, independent gates, evidence, promotion, restart continuity | yes | sandbox proof PASS; installed run reached `VERIFIED_COMPLETE` | installed candidate built and promoted; JAR smoke PASS; restart continuity remains BLOCKED by stale unfinished goal `shg-60f146c8-4c5` with no ready node | goal `shg-7abcea5c-417` bundle and operator JAR hash `91dd9af2a43f03c7b486f2a7feed485c25ed376be86691e118fad572c6b8315f` | PARTIAL, installed promotion PASS | repair stale-goal startup recovery and rerun restart proof |
| C1-X1/CORE-01 | one owner and cross-language architecture gate | yes | checker focused PASS | full architecture census unrun | JSON report + focused result | PARTIAL | caller census, then CORE-02 |
| WEB-01..04 | one canonical web tree and references | yes | static Python tests PASS; 47 files/304 unit tests PASS; shell/MSW hygiene PASS | API generation, typecheck, lint, webpack build PASS; browser E2E platform-blocked on Android | census/deletion manifest + web command output | PARTIAL | retain existing evidence; browser E2E requires non-Android runner |
| DB-01/02 | one future migration owner | yes | 26 deployment/static tests PASS | remote applied-history unverified | migration gate/report | PARTIAL | verify remote history before further deletion |

## Executed Verification

- Kotlin compile: ./gradlew --no-daemon --max-workers=1 ... compileKotlin -> PASS before the current process-runner and digest edits; rerun required.
- Prior full Kotlin run: 607 tests, 0 failures, 0 errors before the current process-runner and digest edits; not reused as current-batch proof.
- Kotlin affected suites: selected Phase 4/7/8/9/10/11, territory, source binding, and architecture tests -> PASS.
- Memory repair: LocalMemoryStoreTest -> 9/9 PASS, including 5,005-record subject filtering.
- SpecGraph security/deployment suites -> 51/51 PASS.
- Architecture report JSON parse and git diff --check -> PASS.
- Web package JSON parse and canonical OpenAPI path inspection -> PASS.
- Web API type generation, typecheck, lint, 47-file/304-test unit suite, and webpack production build -> PASS.
- Web test hygiene: `app-shell.test.tsx` passes without its prior `act(...)` warning; explicit MSW status/recovery handlers preserve strict unhandled-request errors; full suite remains 47/47 files and 304/304 tests.
- Web browser E2E execution -> BLOCKED on Termux Android: Playwright aborts with `Unsupported platform: android` before test discovery.
- Installed proof: goal `shg-7abcea5c-417` emitted the canonical start marker, completed all DAG nodes, produced evidence, built a candidate, promoted the JAR, and passed the fast smoke. Current JAR SHA-256: `91dd9af2a43f03c7b486f2a7feed485c25ed376be86691e118fad572c6b8315f`.
- Restart residual: startup reports `shg-60f146c8-4c5` as unfinished but with no ready node. The operator stopped that stale goal explicitly; clean startup then passed. This is not restart-continuity proof.

No phase percentage is raised to 100% from this batch. Locked baseline percentages remain immutable; current evidence advances installed Phase 11 promotion only, while restart continuity remains open.
