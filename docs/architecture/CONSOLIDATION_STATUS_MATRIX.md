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
| C1-SB Phase 11 | NL sandbox self-build and evidence | yes | sandbox proof PASS | installed proof artifact exists but is not linked to the current repository fingerprint; restart proof remains unverified | operator proof logs | PARTIAL/BLOCKED runtime | rerun installed and restart proofs against current JAR |
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
- Installed proof harness fixes in current batch: source clone excludes ignored dependency/build state and passes the portable `GRADLE_USER_HOME`; proof is not claimed until rerun.

No phase is 100% complete from this batch. The root tree export remains unchanged because no whole canonical phase has passed its acceptance gate.
