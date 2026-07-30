# ATROPOS Phase 11 Self-Host Code + Static Verification Status

Status: SANDBOX PROOF RECORDED; CURRENT INSTALLED-RUNTIME PROOF PENDING. Historical installed-runtime evidence below is retained for provenance only and is not acceptance for the current tree. Rerun is required after the 2026-07-29 source-binding, context-packer, auditor, daemon-root, provider-pack, restart, swap, and proof-harness changes. Source-ingest provider context pack code is PRESENT for local path, git, archive, and hash-pinned HTTP bundle bindings.

Scope: Phase 11 self-host backend, provider context binding, recovery, proof script, and thin CLI only. No SpecGraph product work, UI parity, Android, or web-client work was performed for this status.

## Causal Chain

| Step | Symbol | File path | Wired to next symbol | Negative test / refusal |
| --- | --- | --- | --- | --- |
| Natural-language prompt classification | `SelfHostNaturalLanguageRouter.route` | `src/main/kotlin/atropos/cli/commands/SelfHostNaturalLanguageRouter.kt:3`-`28` | Returns `/agent self-host run ...` or `/agent self-host recover` | Unrelated text returns `null`; covered by `SelfHostNaturalLanguageRouterTest.kt:20`-`25` |
| Provider source binding model | `SourceBinding`, `FetchReceipt`, `CodebaseContextPack` | `src/main/kotlin/atropos/core/provider/SourceBindingModels.kt:6`-`74` | Feeds `SourceBindingFetcher` and `CodebaseContextPacker` | Empty allowed paths return `SourcePackResult.Refused` |
| Polyglot source fetch | `SourceBindingFetcher.fetch` | `src/main/kotlin/atropos/core/provider/SourceBindingFetcher.kt:16`-`165` | Yields content-addressed tree + receipt for `git`, `local_path`, `archive`, `http_bundle` | Hash mismatch, unsupported archive, missing HTTP hash pin, git failure return typed failure/unsupported |
| Bounded codebase pack | `CodebaseContextPacker.pack` | `src/main/kotlin/atropos/core/provider/CodebaseContextPacker.kt:10`-`108` | Attaches `SOURCE_PACK_ID`, `FETCH_RECEIPT_ID`, `TREE_HASH`, redacted files | No matching territory, unreadable files, fetch failure return `SourcePackResult.Refused` |
| Agent ask source pack attachment | `AgentContextCollector.collect` | `src/main/kotlin/atropos/core/agent/AgentContextCollector.kt` | Adds source pack text to provider context and records pack/receipt IDs | Missing pack is rendered as unavailable, never as implicit repo visibility |
| DAG provider source-pack gate | `DagProviderNodeExecutor.execute` | `src/main/kotlin/atropos/core/dag/DagProviderNodeExecutor.kt:19`-`93` | Requires declared territory, builds pack, appends pack to task before provider call | Empty territory, pack refusal, local fallback, missing attestation, or missing pack ID blocks node |
| Router delegates NL before generic chat | `CommandRouter.route` | `src/main/kotlin/atropos/cli/CommandRouter.kt:201`-`215` | Calls `agentCommand.execute(selfHostTokens)` | Unknown slash command remains rejected before NL routing |
| CLI self-host dispatcher | `SelfHostCommand.execute` | `src/main/kotlin/atropos/cli/commands/SelfHostCommand.kt:25`-`49` | Dispatches `run`, `recover`, `next`, `promote`, `export-evidence` | Unknown subcommand returns `AgentCommandOutcome.Invalid` |
| NL run handler | `SelfHostCommand.handleRun` | `src/main/kotlin/atropos/cli/commands/SelfHostCommand.kt:231`-`260` | Calls injected `selfHostRunner`, defaulting to `SelfHostGoalService.runNaturalLanguageSelfBuild` | Blank prompt returns usage refusal at `SelfHostCommand.kt:232`-`234` |
| Durable Phase 11 goal start | `SelfHostGoalService.startGoal` | `src/main/kotlin/atropos/core/agent/SelfHostGoalService.kt:54`-`114` | Creates bootstrap DAG and stores territory/current node | Start exceptions return `failed to start self-host goal` at `SelfHostGoalService.kt:115`-`117` |
| Bootstrap DAG construction | `SelfHostBootstrapDagFactory.create` | `src/main/kotlin/atropos/core/agent/SelfHostBootstrapDagFactory.kt:24`-`100` | Creates verify, source marker, and focused test nodes | Generated literals escaped by `kotlinString` at `SelfHostBootstrapDagFactory.kt:103`-`109` |
| Real source marker node | `SelfHostBootstrapDagFactory` marker node | `src/main/kotlin/atropos/core/agent/SelfHostBootstrapDagFactory.kt:29`-`40`, `71`-`83` | Targets `SelfHostWorktreeNodeExecutor` through DAG evaluator | Territory is restricted to `src/main/kotlin/atropos/core/agent` at line `75` |
| Real focused test node | `SelfHostBootstrapDagFactory` test node | `src/main/kotlin/atropos/core/agent/SelfHostBootstrapDagFactory.kt:30`, `41`-`54`, `84`-`98` | Targets `SelfHostWorktreeNodeExecutor` through DAG evaluator | Territory is restricted to core agent main/test paths at `88`-`91` |
| Context envelope source | `SelfHostGoalService.contextEnvelopeForCurrentNode` | `src/main/kotlin/atropos/core/agent/SelfHostGoalService.kt:334`-`340` | Calls `SelfHostContextPreflight.canonicalEnvelope` | Missing goal/node/DAG returns `null` |
| Context preflight | `SelfHostContextPreflight.verify` | `src/main/kotlin/atropos/core/agent/SelfHostContextPreflight.kt:34`-`79` | Allows `SelfHostDagNodeEvaluator.evaluate` to execute node | Missing envelope, identity mismatch, forged hash, field mismatch, or hash mismatch fail at `38`-`74` |
| DAG node evaluation | `SelfHostDagNodeEvaluator.evaluate` | `src/main/kotlin/atropos/core/agent/SelfHostDagNodeEvaluator.kt:14`-`58` | Calls worktree executor for file nodes or `DagExecutionService.evaluateNode` for verify nodes | Failed preflight records evidence and returns false before mutation at `25`-`30` |
| Territory-legal worktree mutation | `SelfHostWorktreeNodeExecutor.execute` | `src/main/kotlin/atropos/core/agent/SelfHostWorktreeNodeExecutor.kt:21`-`79` | Creates isolated worktree, writes non-empty content, calls merge gate | Unsupported payload or territory violation fails before worktree mutation at `22`-`25` |
| Mutation parser rejects unsafe writes | `SelfHostWorktreeNodeExecutor.parseMutation` | `src/main/kotlin/atropos/core/agent/SelfHostWorktreeNodeExecutor.kt:106`-`114` | Produces normalized relative path and nonblank content | Absolute paths, blank paths, blank content, malformed payload return `null` |
| Pre-merge territory gate | `SelfHostWorktreeNodeExecutor.territoryViolation` | `src/main/kotlin/atropos/core/agent/SelfHostWorktreeNodeExecutor.kt:96`-`104` | Allows `IsolatedWorktreeService.createWorktree` only when in territory | Empty territory or out-of-territory path returns explicit violation |
| Isolated worktree creation | `IsolatedWorktreeService.createWorktree` | `src/main/kotlin/atropos/core/worktree/IsolatedWorktreeService.kt:49`-`124` | Creates detached worktree from baseline commit | Missing baseline or git worktree failure returns `WorktreeCreateResult(false, ...)` |
| Verify then merge only path | `IsolatedWorktreeService.verifyAndMerge` | `src/main/kotlin/atropos/core/worktree/IsolatedWorktreeService.kt:192`-`245` | Runs `git diff --check`, then applies diff to repo root | Verification failure, territory violation before merge, or apply failure returns false at `204`-`230` |
| Node completion evidence | `SelfHostWorktreeNodeExecutor.execute` | `src/main/kotlin/atropos/core/agent/SelfHostWorktreeNodeExecutor.kt:58`-`75` | Stores worktree id, merge status, path, sha256 | Any exception writes failed node state via `fail` at `81`-`94` |
| Advance completion | `SelfHostGoalService.advanceGoal` | `src/main/kotlin/atropos/core/agent/SelfHostGoalService.kt:347`-`389` | Selects/evaluates one ready node and completes verified DAG | Failed or blocked nodes complete the goal as terminal failure at `376`-`381` |
| Autonomous NL runner | `SelfHostGoalService.runNaturalLanguageSelfBuild` | `src/main/kotlin/atropos/core/agent/SelfHostGoalService.kt:472`-`480` | Instantiates `SelfHostAutonomousRunner` with jar locator and builder | No direct success path; delegates all control to runner |
| Autonomous loop | `SelfHostAutonomousRunner.run` | `src/main/kotlin/atropos/core/agent/SelfHostAutonomousRunner.kt:8`-`45` | Starts goal, advances ready nodes, exports evidence if not verified | Non-verified terminal state stops before promotion and exports bundle at `32`-`44` |
| Policy-bounded candidate JAR build hook | `SelfHostCandidateJarBuilder.build` | `src/main/kotlin/atropos/core/agent/SelfHostCandidateJarBuilder.kt:32`-`74` | Uses `TypedToolExecutor` + `BoundedAgencyGate` and returns candidate jar | Policy refusal, nonzero exit, missing/empty jar all return typed `ok=false` at `43`-`67` |
| Installed/candidate JAR locator | `SelfHostRuntimeJarLocator.resolve` | `src/main/kotlin/atropos/core/agent/SelfHostRuntimeJarLocator.kt:22`-`42` | Provides `candidateJar` and `targetJar` to promotion | Missing candidate or installed runtime returns typed unavailable at `33`-`40` |
| Pre-promotion bundle | `SelfHostAutonomousRunner.run` | `src/main/kotlin/atropos/core/agent/SelfHostAutonomousRunner.kt:103`-`116` | Calls `SelfHostGoalService.exportEvidenceBundle` before promotion | Failed bundle export stops before promotion |
| Promotion service entry | `SelfHostGoalService.promoteVerifiedJar` | `src/main/kotlin/atropos/core/agent/SelfHostGoalService.kt:426`-`443` | Records pre/post snapshots and delegates to `SelfHostPromotionService.promote` | Promotion result is returned directly; no alternate success path |
| Safety hard-fail before promotion | `SelfHostPromotionService.promote` | `src/main/kotlin/atropos/core/agent/SelfHostPromotionService.kt:37`-`48` | Calls Director only after `SelfHostSafetyHardFailGate` passes | Hard fail persists `self_host_safety` evidence and returns no gate/no swap |
| Director advisory before verification | `SelfHostPromotionService.promote` | `src/main/kotlin/atropos/core/agent/SelfHostPromotionService.kt:50`-`65` | Calls `VerifiedCompletionGate` only after advisory allows | Director refusal persists advisory evidence and returns no gate/no swap |
| Independent completion gate | `SelfHostPromotionService.promote` | `src/main/kotlin/atropos/core/agent/SelfHostPromotionService.kt:67`-`78` | Calls `SafeJarSwapGate` only when `canComplete=true` | Failed gate persists `promotion_gate` evidence and returns no swap |
| Safe jar swap | `SafeJarSwapGate.promote` | `src/main/kotlin/atropos/core/artifact/SafeJarSwapGate.kt:34`-`99` | Copies candidate to target after preserving target backup | Failed evidence, missing/empty candidate, same target, or copy failure refuses/restores |
| Post-promotion checkpoint | `SelfHostPromotionService.promote` | `src/main/kotlin/atropos/core/agent/SelfHostPromotionService.kt:80`-`98` | Records jar swap evidence and `lastVerifiedCheckpoint=jar:<target>` | If swap fails, checkpoint remains unchanged |
| Automatic next action | `SelfHostGoalService.planNextAction` | `src/main/kotlin/atropos/core/agent/SelfHostGoalService.kt:403`-`424` | Returns `ADVANCE_NODE`, `PROMOTE_JAR`, `COMPLETE`, `WAIT_EXTERNAL_INPUT`, or `HARD_STOP` | No ready unfinished node returns typed hard stop |
| Recovery and continuation | `SelfHostGoalService.recoverAndContinue` | `src/main/kotlin/atropos/core/agent/SelfHostGoalService.kt:445`-`465` | Calls `RestartCoordinator.recoverAndSnapshot`, records next action, advances resumable goal | No resumable goal returns false with recovery message |
| Restart snapshot model | `StateSnapshot` | `src/main/kotlin/atropos/core/recovery/StateSnapshot.kt:5`-`60` | Holds goal, territory, evidence hashes, DAG node, claim, attempt, worktree data | Snapshot fields are typed, not free-form success |
| Restart capture | `RestartCoordinator.snapshot` | `src/main/kotlin/atropos/core/recovery/RestartCoordinator.kt:32`-`93` | Persists state and hashes redacted evidence | Snapshot persistence is atomic/fallback move at `119`-`128` |
| Restart recovery | `RestartCoordinator.recoverAndSnapshot` | `src/main/kotlin/atropos/core/recovery/RestartCoordinator.kt:96`-`106` | Calls crash recovery and `DagNodeRestorer.restoreInterruptedNodes` | Result `ok=false` when recovery errors or non-restorable nodes remain |
| Evidence bundle export | `SelfHostEvidenceBundleExporter.export` | `src/main/kotlin/atropos/core/agent/SelfHostEvidenceBundleExporter.kt:20`-`41` | Writes Markdown and JSON, returns content hashes | Missing goal returns typed failure at `21`-`22` |
| Evidence bundle Markdown | `SelfHostEvidenceBundleExporter.renderMarkdown` | `src/main/kotlin/atropos/core/agent/SelfHostEvidenceBundleExporter.kt:43`-`109` | Includes goal, territory, evidence, nodes, output hashes, restart snapshot | Final render passes through `RedactionFilter.redact` at `109` |
| Evidence bundle JSON | `SelfHostEvidenceBundleExporter.renderJson` | `src/main/kotlin/atropos/core/agent/SelfHostEvidenceBundleExporter.kt:111`-`152` | Includes goal, territory, evidence, node outputs/hash, restart snapshot | Final render passes through `RedactionFilter.redact` at `152` |
| Installed proof script | `scripts/selfhost-installed-proof.sh` | `scripts/selfhost-installed-proof.sh` | Runs NL prompt through `java -jar` in sandbox repo and validates mutation/evidence/swap | Exits nonzero if NL routes to provider chat, marker missing, swap missing, backup missing, or evidence missing |
| CLI discoverability | `CommandRegistry.entries` | `src/main/kotlin/atropos/cli/input/CommandRegistry.kt:77`-`90` | Lists run/recover/next/promote/export-evidence | Missing command would disappear from registry tests/operator help |

## Safety Hard-Fail Table

| Fail type | Symbol | File | Blocks where | Focused test |
| --- | --- | --- | --- | --- |
| Context drift | `SelfHostSafetyHardFailGate.contextDrift` | `src/main/kotlin/atropos/core/agent/SelfHostSafetyHardFailGate.kt:43`-`47` | `SelfHostPromotionService.promote` at `37`-`48` | `SelfHostSafetyHardFailGateTest` |
| Secret leak | `SelfHostSafetyHardFailGate.secretLeak` | `src/main/kotlin/atropos/core/agent/SelfHostSafetyHardFailGate.kt:49`-`60` | `SelfHostPromotionService.promote` at `37`-`48`; exporter redacts at `109` and `152` | `SelfHostSafetyHardFailGateTest`, `SelfHostEvidenceBundleExporterTest` |
| Out-of-territory | `SelfHostSafetyHardFailGate.outOfTerritory` | `src/main/kotlin/atropos/core/agent/SelfHostSafetyHardFailGate.kt:62`-`75` | `SelfHostPromotionService.promote`; mutation preflight also blocks in `SelfHostWorktreeNodeExecutor.kt:96`-`104` | `SelfHostSafetyHardFailGateTest`, `SelfHostWorktreeNodeExecutorTest` |
| Self-verification / self-approval | `SelfHostSafetyHardFailGate.selfVerification` | `src/main/kotlin/atropos/core/agent/SelfHostSafetyHardFailGate.kt:77`-`91` | `SelfHostPromotionService.promote` before Director/gate/swap | `SelfHostSafetyHardFailGateTest`, `VerifiedCompletionGateTest` |
| Fake success | `SelfHostSafetyHardFailGate.fakeSuccess` | `src/main/kotlin/atropos/core/agent/SelfHostSafetyHardFailGate.kt:93`-`107` | `SelfHostPromotionService.promote` before Director/gate/swap | `SelfHostSafetyHardFailGateTest`; ordering locked by `SelfHostPromotionServiceTest.kt:137`-`175` |
| Policy bypass | `SelfHostSafetyHardFailGate.policyBypass` | `src/main/kotlin/atropos/core/agent/SelfHostSafetyHardFailGate.kt:109`-`122` | `SelfHostPromotionService.promote`; candidate build also uses `TypedToolExecutor` at `SelfHostCandidateJarBuilder.kt:38`-`52` | `SelfHostSafetyHardFailGateTest`, policy tests |

## CLI Surface

| CLI surface | Handler | Delegated service path | Runtime boundary |
| --- | --- | --- | --- |
| Natural-language prompt, for example `make ATROPOS build itself from the inside out` | `CommandRouter.route` -> `SelfHostNaturalLanguageRouter.route` | `/agent self-host run ...` -> `SelfHostCommand.handleRun` -> `SelfHostGoalService.runNaturalLanguageSelfBuild` | Operator launches installed JAR and types the prompt |
| `/agent self-host run <prompt>` | `SelfHostCommand.handleRun` at `SelfHostCommand.kt:231`-`260` | `selfHostRunner(prompt)` | Starts full Phase 11 autonomous code chain |
| `/agent self-host recover [goal-id]` | `SelfHostCommand.handleRecover` at `SelfHostCommand.kt:288`-`307` | `SelfHostGoalService.recoverAndContinue` | Restores restart state and advances next resumable goal |
| `/agent self-host next [goal-id]` | `SelfHostCommand.handleNext` at `SelfHostCommand.kt:310`-`320` | `SelfHostGoalService.planNextAction` | Explains local next action without external orchestration |
| `/agent self-host promote <goal-id> <candidate-jar> <target-jar> [node-id]` | `SelfHostCommand.handlePromote` at `SelfHostCommand.kt:358`-`384` | `SelfHostGoalService.promoteVerifiedJar` | Manual promote surface still uses safety, Director, VerifiedCompletionGate, and SafeJarSwapGate |
| `/agent self-host export-evidence <goal-id>` | `SelfHostCommand.handleExportEvidence` at `SelfHostCommand.kt:387`-`403` | `SelfHostGoalService.exportEvidenceBundle` | Writes Markdown + JSON evidence hashes |

## Natural-Language Examples That Must Route To Self-Host

- `make ATROPOS build itself from the inside out` -> `/agent self-host run ...`
- `ATROPOS self-host phase 11 from the installed jar` -> `/agent self-host run ...`
- `build ATROPOS itself with a bounded source change` -> `/agent self-host run ...`
- `continue ATROPOS self-host after restart` -> `/agent self-host recover`
- `resume ATROPOS inside-out self-host` -> `/agent self-host recover`

Negative examples that must not route:

- `ATROPOS` -> identity responder path, not self-host.
- `build a calculator` -> generic provider chat/factory path, not self-host.
- `/agent self-host start build ATROPOS` -> already explicit command, router returns `null`.

## Bootstrap Artifacts

| Artifact | Produced by | DAG node | Territory |
| --- | --- | --- | --- |
| `src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt` | `SelfHostBootstrapDagFactory.kt:29`-`40` | `${goalId}-source-marker`, `EDIT_FILE`, lines `71`-`83` | `src/main/kotlin/atropos/core/agent` |
| `src/test/kotlin/atropos/core/agent/SelfHostCradleRuntimeStateTest.kt` | `SelfHostBootstrapDagFactory.kt:30`, `41`-`54` | `${goalId}-source-marker-test`, `CREATE_FILE`, lines `84`-`98` | `src/main/kotlin/atropos/core/agent`, `src/test/kotlin/atropos/core/agent` |

These are intentionally tiny ATROPOS source/test changes. They prove the cradle can create a durable goal, advance a DAG node, mutate within a strict territory, produce a non-empty diff, and bind the output to hashes before promotion.

## Evidence Bundle Schema

Markdown and JSON are exported by `SelfHostEvidenceBundleExporter.export` at `src/main/kotlin/atropos/core/agent/SelfHostEvidenceBundleExporter.kt:20`-`41`.

Required fields:

- Goal identity: `goalId`, task, status, terminal, phase, DAG id, current node, baseline commit, dirty fingerprint.
- Territory: `record.territory` in Markdown and JSON.
- Evidence: ordered evidence lines after `RedactionFilter` cleaning.
- DAG nodes: id, state, label, action, territory, result, failure.
- Output hashes: each expected output path plus sha256 from `SelfHostFileHasher`.
- Restart snapshot: snapshot id, captured time, memory record count, goal count, DAG count, worktree count.
- Restart goal state: status, DAG id, current node, territory, evidence hashes.
- Restart node state: DAG id, node id, state, action, territory, expected outputs, result hash, failure hash, claim owner, attempts, max attempts.
- Bundle hashes: returned `markdownSha256` and `jsonSha256`.
- Redaction: Markdown output is redacted at `SelfHostEvidenceBundleExporter.kt:109`; JSON output is redacted at `SelfHostEvidenceBundleExporter.kt:152`.

## Unconventional Verification Performed

### A. Call-Graph / Wiring Audit

Static trace from source:

1. NL prompt -> `SelfHostNaturalLanguageRouter.route`, `SelfHostNaturalLanguageRouter.kt:3`-`28`.
2. Router -> `CommandRouter.route`, `CommandRouter.kt:201`-`215`.
3. CLI command -> `SelfHostCommand.handleRun`, `SelfHostCommand.kt:231`-`260`.
4. Service entry -> `SelfHostGoalService.runNaturalLanguageSelfBuild`, `SelfHostGoalService.kt:472`-`480`.
5. Runner -> `SelfHostAutonomousRunner.run`, `SelfHostAutonomousRunner.kt:8`-`140`.
6. Goal/DAG -> `SelfHostGoalService.startGoal`, `SelfHostGoalService.kt:54`-`114`; `SelfHostBootstrapDagFactory.create`, `SelfHostBootstrapDagFactory.kt:24`-`100`.
7. Context preflight/evaluator -> `SelfHostContextPreflight.verify`, `SelfHostContextPreflight.kt:34`-`79`; `SelfHostDagNodeEvaluator.evaluate`, `SelfHostDagNodeEvaluator.kt:14`-`58`.
8. Territory mutation -> `SelfHostWorktreeNodeExecutor.execute`, `SelfHostWorktreeNodeExecutor.kt:21`-`79`; `IsolatedWorktreeService.verifyAndMerge`, `IsolatedWorktreeService.kt:192`-`245`.
9. Verification/promotion -> `SelfHostPromotionService.promote`, `SelfHostPromotionService.kt:37`-`98`.
10. Safety -> Director -> VerifiedCompletionGate -> swap order is visible at `SelfHostPromotionService.kt:37`-`83`.
11. Safe swap -> `SafeJarSwapGate.promote`, `SafeJarSwapGate.kt:34`-`99`.
12. Snapshot/recovery -> `RestartCoordinator.snapshot`, `RestartCoordinator.kt:32`-`93`; `RestartCoordinator.recoverAndSnapshot`, `RestartCoordinator.kt:96`-`106`; `SelfHostGoalService.recoverAndContinue`, `SelfHostGoalService.kt:445`-`465`.
13. Evidence export -> `SelfHostEvidenceBundleExporter.export`, `SelfHostEvidenceBundleExporter.kt:20`-`41`.
14. Next action -> `SelfHostGoalService.planNextAction`, `SelfHostGoalService.kt:403`-`424`.

Result: no dead delegate or always-success stub found on this causal chain. Typed stops exist for missing context, bad context, unsafe mutation, blocked safety, Director denial, failed `VerifiedCompletionGate`, missing candidate/target JAR, failed candidate build, failed swap, missing goal, and unrecoverable restart state.

### B. Contract Tests As Executable Specification

Focused tests written or extended:

- NL self-build routing: `src/test/kotlin/atropos/cli/commands/SelfHostNaturalLanguageRouterTest.kt:7`-`38`.
- CLI no-build self-host runner seam: `src/test/kotlin/atropos/cli/commands/SelfHostCommandTest.kt:60`-`81`.
- Bootstrap DAG source marker and test-node expectations: `src/test/kotlin/atropos/core/agent/SelfHostGoalServiceTest.kt`.
- Territory-legal worktree mutation and refusal: `src/test/kotlin/atropos/core/agent/SelfHostWorktreeNodeExecutorTest.kt`.
- Promotion green path and failed `VerifiedCompletionGate`: `src/test/kotlin/atropos/core/agent/SelfHostPromotionServiceTest.kt:21`-`88`.
- Director-before-gate refusal: `src/test/kotlin/atropos/core/agent/SelfHostPromotionServiceTest.kt:90`-`135`.
- Safety-before-Director/gate/swap refusal: `src/test/kotlin/atropos/core/agent/SelfHostPromotionServiceTest.kt:137`-`175`.
- Safe jar swap preserves prior jar: `src/test/kotlin/atropos/core/artifact/SafeJarSwapGateTest.kt:30`-`53`.
- Runtime jar locator typed missing paths: `src/test/kotlin/atropos/core/agent/SelfHostRuntimeJarLocatorTest.kt`.
- Evidence exporter hash/redaction structure: `src/test/kotlin/atropos/core/agent/SelfHostEvidenceBundleExporterTest.kt`.
- Safety hard-fail branches: `src/test/kotlin/atropos/core/agent/SelfHostSafetyHardFailGateTest.kt`.
- Restart snapshot/recovery state: `src/test/kotlin/atropos/core/recovery/RestartCoordinatorTest.kt`, `src/test/kotlin/atropos/core/agent/SelfHostStateSnapshotRecorderTest.kt`, `src/test/kotlin/atropos/core/agent/SelfHostRecoveryContinuationTest.kt`.

No Gradle or test binary was executed by Codex because the operator reserved compile/test/build/JAR work. Suggested operator-focused commands:

```bash
./gradlew test --tests atropos.cli.commands.SelfHostNaturalLanguageRouterTest
./gradlew test --tests atropos.cli.commands.SelfHostCommandTest
./gradlew test --tests atropos.core.agent.SelfHostGoalServiceTest
./gradlew test --tests atropos.core.agent.SelfHostWorktreeNodeExecutorTest
./gradlew test --tests atropos.core.agent.SelfHostPromotionServiceTest
./gradlew test --tests atropos.core.artifact.SafeJarSwapGateTest
./gradlew test --tests atropos.core.agent.SelfHostSafetyHardFailGateTest
./gradlew test --tests atropos.core.agent.SelfHostEvidenceBundleExporterTest
./gradlew test --tests atropos.core.recovery.RestartCoordinatorTest
```

### C. Negative-Path Audit

Typed refusals on the self-host path:

- NL route mismatch: `SelfHostNaturalLanguageRouter.route` returns `null`, lines `21`-`27`.
- CLI blank run: `SelfHostCommand.handleRun` returns usage invalid, lines `231`-`234`.
- Goal start exception: `SelfHostGoalService.startGoal` returns false, lines `115`-`117`.
- Missing goal/DAG/node: `SelfHostDagNodeEvaluator.evaluate` returns false, lines `15`-`22`.
- Missing or wrong context: `SelfHostContextPreflight.verify` fails at lines `38`-`74`; evaluator records evidence and returns false at `SelfHostDagNodeEvaluator.kt:25`-`30`.
- Unsupported/blank/absolute mutation: `SelfHostWorktreeNodeExecutor.parseMutation` returns `null`, lines `106`-`114`.
- Territory violation before mutation: `SelfHostWorktreeNodeExecutor.territoryViolation`, lines `96`-`104`.
- Worktree creation/merge failure: `SelfHostWorktreeNodeExecutor.execute`, lines `34`-`56`.
- Failed cradle verification: `SelfHostDagNodeEvaluator.evaluate`, lines `42`-`56`.
- Failed/blocked DAG terminal state: `SelfHostGoalService.advanceGoal`, lines `376`-`381`.
- Candidate JAR build policy/process/artifact failure: `SelfHostCandidateJarBuilder.build`, lines `43`-`67`.
- Missing installed or candidate JAR: `SelfHostRuntimeJarLocator.resolve`, lines `33`-`40`.
- Failed evidence export before promotion: `SelfHostAutonomousRunner.run`, lines `103`-`116`.
- Safety hard-fail: `SelfHostPromotionService.promote`, lines `37`-`48`.
- Director denial: `SelfHostPromotionService.promote`, lines `50`-`65`.
- Failed `VerifiedCompletionGate`: `SelfHostPromotionService.promote`, lines `67`-`78`.
- Failed swap evidence/candidate/target/copy: `SafeJarSwapGate.promote`, lines `43`-`64`, `86`-`98`.
- No resumable recovery goal: `SelfHostGoalService.recoverAndContinue`, lines `449`-`455`.

Result: no source path found that returns promotion success when territory, attestation, safety, Director, completion gate, or swap fails.

### D. Territory Mutation Audit

Proof from source:

- Bootstrap source marker node declares territory `src/main/kotlin/atropos/core/agent` at `SelfHostBootstrapDagFactory.kt:75`.
- Bootstrap test node declares territory `src/main/kotlin/atropos/core/agent` and `src/test/kotlin/atropos/core/agent` at `SelfHostBootstrapDagFactory.kt:88`-`91`.
- Mutation parsing rejects absolute paths and blank content before write at `SelfHostWorktreeNodeExecutor.kt:106`-`114`.
- Territory is checked before creating the worktree at `SelfHostWorktreeNodeExecutor.kt:22`-`25`.
- Target write is constrained to the isolated worktree root at `SelfHostWorktreeNodeExecutor.kt:40`-`44`.
- Merge is the only path back to repo root: `SelfHostWorktreeNodeExecutor.kt:55` calls `IsolatedWorktreeService.verifyAndMerge`.
- `IsolatedWorktreeService.verifyAndMerge` runs verification first at `IsolatedWorktreeService.kt:197`-`206`.
- `IsolatedWorktreeService.verifyAndMerge` extracts changed paths and blocks out-of-territory diff before `git apply` at `IsolatedWorktreeService.kt:216`-`230`.

Result: the bootstrap node cannot write outside the grant through this path; empty writes are rejected; verified merge is the only apply path.

### E. Installed-Root Audit

Proof from source:

- `AtroposRepoRootLocator.resolve` is the single fallback that starts from `user.dir` and walks to the ATROPOS root, `src/main/kotlin/atropos/core/AtroposRepoRootLocator.kt:6`-`19`.
- `SelfHostCommand` defaults `repoRoot` to `AtroposRepoRootLocator.resolve`, `SelfHostCommand.kt:14`-`23`.
- `SelfHostGoalService` defaults `repoRoot` to `AtroposRepoRootLocator.resolve`, `SelfHostGoalService.kt:16`-`27`.
- `GoalRunStore` defaults `repoRoot` to `AtroposRepoRootLocator.resolve`, `GoalRunStore.kt:15`-`20`.
- `DagStore` defaults root to `AtroposRepoRootLocator.resolve`, `DagStore.kt:14`-`21`.
- `IsolatedWorktreeService` defaults `repoRoot` to `AtroposRepoRootLocator.resolve`, `IsolatedWorktreeService.kt:41`-`48`.
- `RestartCoordinator` defaults `repoRoot` to `AtroposRepoRootLocator.resolve`, `RestartCoordinator.kt:19`-`30`.
- Evidence exporter and promotion service receive the already-resolved `repoRoot` from `SelfHostGoalService`, `SelfHostGoalService.kt:41`-`52`.

Result: self-host durable state, DAGs, worktrees, snapshots, promotion, and evidence use the located ATROPOS root. Raw `user.dir` is isolated to root discovery rather than scattered through self-host stores.

## Executed Inside-Out Proof

Focused command executed by Codex:

```bash
./gradlew test --tests atropos.cli.SelfHostInsideOutSandboxProofTest
```

Result: PASS. This command compiled the touched module/test path and executed a sandbox proof through the production natural-language CLI route. It did not assemble, install, or smoke a live device JAR.

Proof harness:

- `src/test/kotlin/atropos/cli/SelfHostInsideOutSandboxProofTest.kt`
- It creates a temporary ATROPOS-shaped git repo with `settings.gradle.kts`, `build.gradle.kts`, a fake local `gradlew` for focused sandbox gates, and a sandbox installed JAR target.
- It sets `user.dir` to the sandbox root before constructing `CommandRouter`, so `AgentCommand` and `SelfHostCommand` resolve the sandbox through `AtroposRepoRootLocator`.
- It enters with a natural-language prompt through `CommandRouter.handleInput`, not a direct runner call.

Executed prompt:

```text
ATROPOS, build yourself from the inside out and run self-host Phase 11
```

Executed artifact summary from `.atropos/self-hosting/proofs/phase11-inside-out-sandbox-proof.properties`:

| Field | Value |
| --- | --- |
| Sandbox root | `/data/data/com.termux/files/usr/tmp/atropos-inside-out-proof-14303387409231267223` |
| Goal id | `shg-711cd428-d3c` |
| Source worktree id | `wt-67187cd5-35f` |
| Source worktree path | `/data/data/com.termux/files/usr/tmp/atropos-inside-out-proof-14303387409231267223/.atropos/worktrees/wt-67187cd5-35f` |
| Mutated source path | `/data/data/com.termux/files/usr/tmp/atropos-inside-out-proof-14303387409231267223/src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt` |
| Marker before hash | `missing` |
| Marker after sha256 | `63c8ca399373f189defdd7dd0cecec613a87741ce3e981c2652d16af0deeb80c` |
| Mutation status | `?? src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt`; `?? src/test/kotlin/atropos/core/agent/SelfHostCradleRuntimeStateTest.kt` |
| Evidence Markdown | `/data/data/com.termux/files/usr/tmp/atropos-inside-out-proof-14303387409231267223/.atropos/self-hosting/evidence/shg-711cd428-d3c/bundle.md` |
| Evidence Markdown sha256 | `e60447f476cfaae5e31be7627349715c7565455836670ea59a577bdf600a01cd` |
| Evidence JSON | `/data/data/com.termux/files/usr/tmp/atropos-inside-out-proof-14303387409231267223/.atropos/self-hosting/evidence/shg-711cd428-d3c/bundle.json` |
| Evidence JSON sha256 | `964db1cb5ac4a3ed96855f395d215c95ede5c2e3c4c2a45aa81871621a6a6dcf` |
| Sandbox backup JAR | `/data/data/com.termux/files/usr/tmp/atropos-inside-out-proof-14303387409231267223/installed/atropos.jar.backup-1785323290254` |
| Hard-fail promoted | `false` |
| Hard-fail message | `promotion refused by self-host safety hard-fail gate: fake_success: self-host state references fake or evidence-free success` |
| Hard-fail target sha256 | `d100a829cdb8a67d571ef23ce8ef57a11484d265e97cd6d7ba0e21b878cfa94c` |

Gate outcomes from the executed run:

- `self_host_safety passed=true`
- `director_pre_promote allowed=true message=director advisory: no blocking drift`
- `promotion_gate node=shg-711cd428-d3c-source-marker-test canComplete=true | Implementation Exists=PASS | Focused Tests=PASS | Deterministic Verification=PASS | Compile Gate=PASS | Territory & Secrets=PASS | Acceptance Evidence=PASS | Expected Outputs=PASS | Unresolved Dimensions=PASS | Auditor Findings=PASS`
- `jar_swap promoted=true candidate=ATROPOS.jar ... backup=atropos.jar.backup-1785323290254 ... candidate_exists=PASS`

Evidence bundle excerpt from the executed run:

- `context_preflight_verified goal=shg-711cd428-d3c ... node=shg-711cd428-d3c-source-marker`
- `context_attestation system=ATROPOS ... node=shg-711cd428-d3c-source-marker`
- `node_execution node=shg-711cd428-d3c-source-marker ok=true state=COMPLETE result=worktree=wt-67187cd5-35f merged=true path=src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt sha256=63c8ca399373f189defdd7dd0cecec613a87741ce3e981c2652d16af0deeb80c`
- `node_execution node=shg-711cd428-d3c-source-marker-test ok=true state=COMPLETE result=worktree=wt-f174a9b0-89a merged=true path=src/test/kotlin/atropos/core/agent/SelfHostCradleRuntimeStateTest.kt sha256=6f697ed8d01b87c5a04178e515692d9c4eeda2b8a05523cc2d71528d17748f0b`
- `candidate_jar_build ok=true ... candidate=ATROPOS.jar`
- `promotion_gate ... canComplete=true`
- `jar_swap promoted=true ... backup=atropos.jar.backup-1785323290254`

The sandbox JAR swap exercised prior-JAR preservation in `installed/`: active `atropos.jar` was replaced with the sandbox candidate, and `atropos.jar.backup-1785323290254` preserved the prior bytes.

The hard-fail case used the real `SelfHostPromotionService` with an added `fake_success placeholder green` evidence line. `SelfHostSafetyHardFailGate` refused before swap, and `installed/hard-fail-target.jar` remained unchanged.

## Provider Source Ingest Proof

Implemented source bindings:

- `git`: any host reachable by local `git`, including local git repositories, yields a content-addressed tree and `FetchReceipt`.
- `local_path`: local tree copied into `.atropos/source-bindings/trees/<treeHash>` and packed from that immutable tree.
- `archive`: `.zip`, `.tar`, `.tar.gz`, `.tgz`; zip extraction defends against path escape; optional SHA-256 pin is enforced.
- `http_bundle`: requires `expectedSha256`, downloads bytes, verifies hash, then processes as archive. Missing or mismatched hash is typed failure, not degraded success.

Context pack behavior:

- `CodebaseContextPacker` packs from the active binding tree regardless of origin.
- Packs are path-bounded by declared territory and size-bounded by request limits.
- Packs contain `SOURCE_PACK_ID`, `FETCH_RECEIPT_ID`, `BINDING`, `TREE_HASH`, `TERRITORY`, and redacted file sections.
- Providers never require GitHub. DAG provider nodes require a pack from the active binding.
- `DagProviderNodeExecutor` refuses blind provider calls with no declared territory and blocks if no pack/attestation exists.

Focused test:

```bash
./gradlew test --tests atropos.core.provider.SourceBindingContextPackerTest
```

Result: PASS as part of the focused causal-chain run.

## Historical Installed-Runtime Proof (Not Current Acceptance)

Proof script:

```bash
./gradlew jar
scripts/selfhost-installed-proof.sh build/libs/ATROPOS.jar
```

Historical result:

```text
ATROPOS_SELFHOST_INSTALLED_PROOF_OK
proof=/data/data/com.termux/files/home/ATROPOS/.atropos/self-hosting/proofs/phase11-installed-runtime-proof.properties
sandbox=/tmp/atropos-installed-proof.k3UhLi
```

Installed proof properties:

| Field | Value |
| --- | --- |
| Prompt | `ATROPOS, build yourself from the inside out and run self-host Phase 11` |
| Installed runtime JAR | `/data/data/com.termux/files/home/ATROPOS/build/libs/ATROPOS.jar` |
| Sandbox root | `/tmp/atropos-installed-proof.k3UhLi` |
| Mutated marker path | `/tmp/atropos-installed-proof.k3UhLi/src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt` |
| Marker sha256 | `63c6c029151d686ba9f01d46d381d46616d010b3a6cf63cbc4d5cbe33f01697d` |
| Mutation status | `?? src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt | ?? src/test/kotlin/atropos/core/agent/SelfHostCradleRuntimeStateTest.kt` |
| Evidence Markdown | `/tmp/atropos-installed-proof.k3UhLi/.atropos/self-hosting/evidence/shg-9850fa76-9d4/bundle.md` |
| Evidence Markdown sha256 | `f5752efe5eb881de47974cc43fb6854eb32b40a1bea35ceac1b5855eae301ee8` |
| Evidence JSON | `/tmp/atropos-installed-proof.k3UhLi/.atropos/self-hosting/evidence/shg-9850fa76-9d4/bundle.json` |
| Evidence JSON sha256 | `c3c40d6d4cef8f04deb8af8e9f67693ae0113d998a50a981bbad54c8f15b244b` |
| Sandbox installed JAR | `/tmp/atropos-installed-proof.k3UhLi/installed/atropos.jar` |
| Sandbox installed JAR sha256 | `5054244a6084c60c3a7be858113bf829cbda8f39516863d39b7cc9d6120a0d7d` |
| Sandbox prior-JAR backup | `/tmp/atropos-installed-proof.k3UhLi/installed/atropos.jar.backup-1785329292443` |
| Sandbox backup sha256 | `b518592741d8dc186777b762d29630cf50d5828a4ab7b2b438ef68e3434852ed` |
| Output log | `/tmp/atropos-installed-proof.k3UhLi/installed-proof.out` |

The historical proof used the production headless JAR entry point with stdin, not a test-only service call. The current proof scripts have since changed and this result is not current acceptance. The current scripts also require a real candidate JAR, exact prior-JAR preservation, ordered gate evidence, and complete evidence bundles.

Initial attempts against stale `./atropos.jar` and pre-existing `build/libs/ATROPOS.jar` failed because those artifacts routed the NL prompt to generic provider chat. After packaging the then-current tree with `./gradlew jar`, the historical installed-runtime proof passed. The current tree has not been rerun.

## Historical Kill / Restart Continuity Proof (Current Rerun Pending)

Focused command:

```bash
./gradlew test --tests atropos.core.agent.SelfHostRecoveryContinuationTest --tests atropos.core.recovery.RestartCoordinatorTest --tests atropos.core.recovery.CrashRecoveryServiceTest --tests atropos.core.recovery.RuntimeContinuitySupervisorTest --tests atropos.core.agent.GoalContinuationServiceTest
```

Historical result: PASS for the recorded focused run; current source changes require rerun.

The installed proof sandbox also wrote restart snapshots under:

```text
/tmp/atropos-installed-proof.k3UhLi/.atropos/recovery/snapshots/
```

Code-level MISSING on the causal chain at the time of the recorded proof: none found in A-E, provider source-ingest, focused recovery tests, sandbox proof, or installed-runtime proof. After later code changes, the proof boundary is PENDING OPERATOR RERUN because this session has not rerun Gradle, package, or installed-JAR proof.

Phase 11 inside-out self-build remains historically proven in the recorded sandbox/runtime run, but is not proven for the current tree. Operator rerun is required:

```bash
./gradlew jar
scripts/selfhost-installed-proof.sh build/libs/ATROPOS.jar
scripts/selfhost-restart-proof.sh build/libs/ATROPOS.jar
```
