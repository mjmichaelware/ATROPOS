# ATROPOS backend status

This file records backend implementation status for the current engine wave. A row is only `done` when the production caller and focused evidence exist; unavailable runtime/toolchain evidence remains `partial` or `blocked`.

| atom/id | status | files | tests | notes |
| --- | --- | --- | --- | --- |
| FTY-01 | done | `src/main/kotlin/atropos/cli/FactoryCommandHandler.kt`, `src/main/kotlin/atropos/core/factory/AppFactoryRouter.kt` | source-compiled in factory slice | `/factory resume <runId>` validates and renders the attested handoff through `AppFactoryRouter.resume` |
| FTY-02 | done | `FactoryRunHandoff.kt`, `FactoryRunOrchestrator.kt`, `FactoryResumeAndRepairTest.kt` | source-compiled in factory slice | prompt, requirements, plan, and freeze artifacts are persisted; freeze hash is checked against the handoff; missing artifacts fail actionable |
| FTY-03 | partial | `FactoryRepairExecutor.kt`, `FactoryLiveRepairAction.kt`, `FactoryRunOrchestrator.kt`, `AppFactoryRouter.kt` | focused missing-command and configured-command evidence tests are wired for hosted execution; root focused tests remain unrun | production router now supplies one bounded, policy-gated repair callback driven only by `ATROPOS_FACTORY_REPAIR_COMMAND`; absent/failed commands remain hard failures, and successful callback evidence must pass unchanged-freeze validation before regeneration and obligation-loop re-entry |
| FTY-04 | done | `DagStore.kt`, `FactoryProgressGuard.kt`, `FactoryProgressGuardTest.kt` | source-compiled; durable restart test written, not Gradle-run | failure signatures and write histories persist under `.atropos/factory/progress-guard.tsv`; persistence errors fail closed |
| FTY-05 | partial | `FactoryObligationLoop.kt`, `FactoryRunOrchestrator.kt`, factory tests | source-compiled; root focused tests not run | open/blocked/failed/improper termination remains rejected; Gradle root test compilation is still environment-limited |
| B-PROV-001/002/003/004 | partial | `src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt`, `ProviderDescriptor.kt`, `StaticProviderDescriptorRegistry.kt`, `adapter/OpenAiCompatibleProviderCatalog.kt`, `ProviderCommandHandler.kt`, `CommandRouter.kt`, `ProviderOnboardingTest.kt`, `install.sh` | focused tests cover aliases, generic namespace, endpoint metadata, Together/Fireworks adapter wiring, and preference ordering; hosted root test lane remains unproven | env/vault discovery, endpoint aliases, metadata-only user-local `~/.atropos/provider/providers.json` by default (workspace-local when an explicit root is supplied), billing class, free/local filtering, private console connect, zero-healthy launch message, and provider CLI exist; Together AI and Fireworks AI now have canonical paid descriptors and OpenAI-compatible transport owners; endpoint-only configuration is `untested` until credentials are present; full root test lane remains unproven |
| B-PROV-012 / free-first paid transition | partial | `ProviderCascadeRouter.kt`, `ProviderCascadeOrder.kt`, `ProviderOnboarding.kt`, agent/chat consumers, focused provider tests | approval/cascade tests wired for hosted execution; root lane remains unproven | free/local exhaustion now produces a `ProviderApprovalCard` instead of silent queueing when a configured paid provider is next; only an explicitly unlocked paid provider can re-enter the canonical cascade; local-only mode never surfaces paid spend |
| B-PROV-012 / paid policy unlock | partial | `src/main/kotlin/atropos/core/policy/ExecutionPolicyEngine.kt`, `EmergencyPaidGate.kt`, provider policy tests | unlock/deny tests wired for hosted execution; root lane remains unproven | the single policy engine now permits a paid proposal only when the canonical paid gate has an active unlock for that exact provider; default paid calls remain denied |
| B-LOCAL-ONLY / B-006 / B-OC-002 | partial | `src/main/kotlin/atropos/core/Config.kt`, `ExecutionPolicyEngine.kt`, `ProviderActionProposals.kt`, `RoutePolicy.kt`, `ProviderCascadeRouter.kt`, `ProviderOnboarding.kt`, `FactoryResearchService.kt`, `FactoryLineageFactory.kt`, `AppFactoryRouter.kt`, `AtroposBridge.kt`, `McpCommandHandler.kt`, `BackendDoctor.kt`, config-bound agent/CLI/factory callers, focused policy/route tests | focused tests added; hosted root test lane remains unproven | `ATROPOS_LOCAL_ONLY` and `RuntimeConfig.localOnly` now feed default and explicit policy/router/research owners, plus bridge/CLI/doctor MCP callers; network and remote provider side effects are denied, route candidates and explicit overrides are filtered, factory lakehouse/bounded research and `/scavenge` are refused, and local providers remain eligible; runtime hosted proof remains |
| B-005 / ADD-W-001 | partial | `src/main/kotlin/atropos/bridge/BridgeEventsHandler.kt`, `BridgeRoutes.kt`, `BridgeEventHub.kt`, `HttpStreamRoute.kt`, `BridgeMcpHandler.kt`, `AtroposBridge.kt`, `bridge/projection/QuotaProjection.kt` | bridge/MCP status, dedicated quota route, body/query call parsing, pre-execution gate tests added; hosted bridge test lane remains unproven | request body/query is preserved and used by the HTTP parser; `/v1/events` and `/v1/events/stream` honor session identity and cursor through the existing event hub; `/v1/mcp/status` and `/v1/mcp/call` use the same configured MCP host bound by the production bridge, with caller/territory admission before process start; `/v1/status` and `/v1/quota` now expose existing quota metadata without secret fields |
| B-011 / ADD-MCP-001..009 | partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/main/kotlin/atropos/cli/McpCommandHandler.kt`, `src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt`, `BridgeRoutes.kt`, `CommandRouter.kt`, `CommandCatalog.kt`, `docs/mcp-examples/*.json`, focused tests | focused MCP status, stdio handshake, pre-execution territory gate, redacted evidence, and local tool-call tests added; hosted root test lane remains unproven | `mcp.json` schema, allowlist, disabled-by-default community, localOnly remote refusal, bounded tool descriptions/arguments/responses, redacted response/evidence hashes or explicit no-evidence reason, probe from configured repo root, CLI `/mcp list|test|call|search`, and bridge `/v1/mcp/status` use one host owner; both direct and bridge calls cross the existing `McpTerritoryBridge`; search is configured-only and never auto-installs or performs remote discovery |
| B-MCP-GHA / Tier-0 | partial | `.github/actions/atropos-verify/action.yml`, `.github/workflows/atropos-verify-example.yml`, `CONTRIBUTING.md`, existing `core/github/GitHubBinding.kt` | YAML/static checks only | local GitHub binding already requires explicit authorization and territory; reusable verify action/check example and no-brand-adapter contribution rule added; no remote OAuth/PAT or live check-run proof |
| B-INST-001..005 / AUD036 | partial | `install.sh`, `scripts/install-contract-test.sh`, `scripts/package-installers.sh`, `scripts/package-installers-test.sh`, `.github/workflows/release.yml`, `src/main/kotlin/atropos/cli/BackendDoctor.kt` | package contract passed previously; first-run and doctor tests are wired but hosted release workflow not executed | installer now bootstraps missing config/provider metadata skeletons, preserves existing config, verifies the installed JAR with `--health`, and the user-facing `/doctor` composes provider/MCP diagnostics without secrets; public release URL and device install remain external |
| hosted verification lane | partial | `.github/workflows/compile-gate.yml`, `.github/actions/atropos-verify/action.yml`, `.github/workflows/atropos-verify-example.yml`, `scripts/atropos-verify-worktree.sh`, `scripts/atropos-fast-gate.sh` | shell syntax, installer contracts, and diff check passed; hosted compile/tests not executed | focused factory/provider/bridge/MCP/MarkItDown/quota-route suites are named in the hosted workflow and reusable action; the fast gate source manifest is Termux-compatible; GitHub Actions execution remains required evidence |

## Commands

- `/factory resume <runId>` — read-only attested resume inspection; execution requires the existing router callback path.
- `/providers list|refresh|test|prefer|disable|connect <provider>` — local provider discovery and vault-backed connect.
- `/mcp list|test|search <query>|call <server> <tool>` — configured MCP inventory/search, health probe, and bounded tool call.
- `/github issues|issue|prs|pr-files|checks|branch-protection <owner/repository> ...` — gated GitHub inspection; `create-issue|comment-issue|create-pr|comment-pr|request-review|create-check|update-check` additionally require `--confirm <id>`.
- `/keys` — existing local secret status surface.

## Secret and policy boundary

Provider connect stores secrets through `TokenIsolationVault` under the user-local `~/.atropos/secrets` by default; explicit workspace roots remain supported for isolated callers. `providers.json` contains metadata only. Environment and local vault discovery are the default path. FREE/LOCAL providers may run without paid approval; PAID providers must stop for explicit approval before spend. No provider key is persisted in this status file.

## Remaining blocked or partial work

- FTY-03 has a runtime-owned repair callback; a configured real repair run and hosted test evidence are still pending. Absent that callback or command, failures remain failed.

### 2026-08-23T09:05:00Z · Backend batch: paged-evidence-index

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-005 evidence listing/limit safety | source-wired / partial | `src/main/kotlin/atropos/bridge/BridgeEvidenceHandler.kt`, `src/main/kotlin/atropos/bridge/queue/ConversationWorkRunner.kt`, `src/main/kotlin/atropos/bridge/queue/AgentQueueWorkRunner.kt`, `src/test/kotlin/atropos/bridge/BridgeEvidenceHandlerTest.kt`, hosted selectors | `GET /v1/evidence` → `BridgeEvidenceHandler` → existing `AgentQueueWorkRunner`/`AgentQueueService` | No-id requests now return bounded metadata-only pages (`limit` 1–100, `offset` 0–1000); content still requires an id and keeps redaction/100 KB cap. Focused test and both GitHub selectors added; local Gradle/root and hosted execution remain pending. |
- Root factory focused tests and full root compilation have not completed within the available Gradle heap/time envelope. `:core:jvmTest` does not contain the root factory test source set.
- Root Gradle compilation remains unsuitable for the local device heap; `.github/workflows/compile-gate.yml` is the authoritative hosted compile and focused-test lane. Live provider/MCP probes, install/doctor, and device proof remain external/runtime gates.
- MCP runtime probe remains untested against a real external deployment; the local bounded stdio fixture and core test target now pass. Health labels persist to `.atropos/mcp/health.tsv`; hosted/root integration proof remains pending.
- Quota status projection is source-wired and locally statically checked; hosted bridge tests still need to prove the complete root compilation and response contract.

### 2026-08-24T22:05:00Z · Backend batch: provider-live-test-exception-boundary

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| P18 / live-test unhealthy without cascade crash | source-wired / partial | `src/main/kotlin/atropos/core/provider/ProviderActivationService.kt`, `src/test/kotlin/atropos/core/provider/ProviderActivationServiceTest.kt` | `/providers live-test` → existing `ProviderCommandHandler` → `ProviderActivationService.liveTest` → canonical `ProviderAdapter.complete` | Adapter exceptions are normalized into the existing `ProviderFailure`/quota/state path, so an injected connection failure returns `OFFLINE` and persists a non-success activation record instead of escaping the command. Hosted selector parity (382), orphan gate, and diff check pass; root/hosted Kotlin execution and real provider calls remain unrun. |

### 2026-08-24T22:40:00Z · Backend verification: provider-live-test-root-timeout

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| P18 focused Kotlin execution | inconclusive / partial | `STATUS-BACKEND.md` | GitHub Actions `focused-backend-tests` → existing `ProviderActivationServiceTest` selector | `timeout 60s ./gradlew --no-daemon :test --rerun --max-workers=1 --tests 'atropos.core.provider.ProviderActivationServiceTest'` exited 124 during Gradle task-graph calculation with no compilation or test output. This is not a pass or source failure; GitHub Actions remains the authoritative execution lane. |

### 2026-08-24T23:05:00Z · Backend batch: bounded-file-upload-envelope

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-005 / ADD-W-029 file upload size bound | source-wired / partial | `src/main/kotlin/atropos/bridge/BridgeFilesHandler.kt`, `src/test/kotlin/atropos/bridge/BridgeFilesHandlerTest.kt` | `POST /v1/files` → existing `BridgeRoutes` → `BridgeFilesHandler.upload` | Existing territory/path and envelope hashing now refuse decoded uploads over 512 KiB with HTTP 413 before directory/file creation; focused test proves no oversized file is written. Selector parity, orphan gate, and diff check pass; root/hosted Kotlin execution remains pending. |

### 2026-08-24T23:25:00Z · Backend batch: bridge-cli-contract-alias

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-005 / ADD-W-001 CLI bridge endpoint | source-wired / partial | `src/main/kotlin/atropos/bridge/BridgeRoutes.kt`, `src/test/kotlin/atropos/bridge/AtroposBridgeTest.kt` | Web/Android/editor bridge client → `POST /v1/cli` → existing `BridgeCommandHandler`/`CommandRouter` owner | Added the declared `/v1/cli` contract route as a compatibility alias to the existing `/v1/command` handler; no second command executor or event bus exists. Real bridge route-description test and hosted selector wiring are present; root/hosted execution remains pending. |

### 2026-08-24T23:45:00Z · Backend batch: bridge-approval-contract-alias

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-005 / ADD-W-001 approval bridge endpoint | source-wired / partial | `src/main/kotlin/atropos/bridge/BridgeRoutes.kt`, `src/test/kotlin/atropos/bridge/AtroposBridgeTest.kt` | Web/Android/editor bridge client → `POST /v1/approve` → existing `BridgeApprovalHandler.decideApproval` → `PendingApprovalStore` | Added the declared `/v1/approve` contract route as an alias to the existing role/decision owner; `/v1/approvals/decide` remains compatible. Route-description proof is wired; root/hosted execution remains pending. |

## Wave residual audit

| residual | status | evidence / caller | remaining truth |
| --- | --- | --- | --- |
| R1 MCP single owner | partial | `McpHostManager.kt` is the only MCP manager; `/mcp`, bridge, and doctor call it; process starts use `BoundedProcessRunner` | hosted tests and a real allowlisted stdio server remain unrun |
| R2 provider prefer/disable | partial | `ProviderOnboardingService.preferredProviderIds`, `RoutePolicy`, and `ProviderCascadeRouter` consume persisted metadata; `/providers prefer|disable` is registered | hosted route tests remain unrun |
| R3 BlockType caller | source-wired | `LandingRenderer.logo` calls `BlockType` on narrow frames | no root UI test run |
| R4 Sentry loop | source-wired / partial | `SentryCommandHandler` → `SentryApiClient` → `SentryRepairCoordinator` → existing worker proposal and `EvidenceStore`; `IntegrationRegistry` owns registration | hosted tests, configured Sentry credentials, and live issue/repair evidence remain unrun; proposal path never silently applies a patch |
| R5 factory resume/handoff | partial | `FactoryCommandHandler` → `AppFactoryRouter.resume` → `FactoryRunHandoff.read`; durable guard exists | configured runtime repair and hosted factory tests remain unrun |
| R6 local git/FS tool wire | partial | existing `ShellCommandHandler`/`BoundedGitWorktreeCommandRunner` own local git; MCP FS remains configured through `McpHostManager` | no standalone `LocalGitTool` symbol exists; full ToolExecutor integration and hosted proof remain open |
| M13 MarkItDown ingest | partial | `/mcp ingest <path>` → `MarkItDownIngestService` → existing MCP host + `DocumentIngestionService` | hosted test and real allowlisted MarkItDown probe remain unrun |

## Provider catalog audit P01–P18

| id | status | current owner / evidence |
| --- | --- | --- |
| P01 DeepSeek | source-wired | `deepseek_direct` descriptor + OpenAI-compatible catalog; alias discovery |
| P02 Mistral | source-wired | descriptor + OpenAI-compatible catalog; paid gate applies |
| P03 Gemini | source-wired | native Gemini adapter; `GOOGLE_API_KEY`/`GEMINI_API_KEY` alias resolution |
| P04 xAI | source-wired | descriptor + OpenAI-compatible catalog; GROK aliases |
| P05 OpenRouter | source-wired | descriptor + OpenAI-compatible catalog |
| P06 Ollama | source-wired | local descriptor + local adapter; `OLLAMA_HOST` discovery |
| P07 AWS Bedrock | source-wired / partial | `aws_bedrock` descriptor → `BuildKernelAdapter` → `BedrockKernelAdapter` → pure `AwsSigV4` signer and Converse transport | hosted adapter tests and real approved AWS transport evidence remain unrun; paid gate still applies |

| P07 AWS Bedrock correction | source-wired / partial | `src/main/kotlin/atropos/core/provider/adapter/AwsSigV4.kt`, `BedrockKernelAdapter.kt`, `StaticProviderDescriptorRegistry.kt`, `BuildKernelAdapter.kt`, focused adapter tests | existing `StaticProviderAdapterRegistry` → `buildKernelAdapter` → paid `aws_bedrock` descriptor → SigV4 Converse transport | The signing/transport owner now exists with injected offline tests; the historical blocked row above is superseded. Hosted execution and real approved AWS calls remain unproven. |
| P08 Cohere | source-wired | descriptor + OpenAI-compatible catalog |
| P09 Perplexity | skipped | no existing Perplexity reference or accepted descriptor in this tree |
| P10 Anthropic | source-wired | native Anthropic adapter/catalog plus alias-aware adapter registry |
| P11 OpenAI/Azure | partial | OpenAI and Azure descriptors/catalogs exist; hosted tests/live calls pending |
| P12 ATROPOS_PROVIDER_* | source-wired | `ProviderOnboardingService.genericProviderKeyMatches` |
| P13 cascade print | source-wired | `CommandRouter` launch onboarding block |
| P14 zero healthy | source-wired | onboarding render remediation branch; no crash path |
| P15 quota | partial | `QuotaLedger`, `/v1/status`, and `/v1/quota`; hosted tests pending |
| P16 paid gate | partial | `ProviderPolicyGate`/`EmergencyPaidGate`; hosted policy tests pending |
| P17 metadata-only providers.json | partial | `ProviderOnboardingService.writeConfig`; no secret values; hosted test pending |
| P18 live-test unhealthy | partial | `ProviderActivationService.liveTest` failure-state path; live provider calls not run |
- Together AI and Fireworks AI catalog wiring is source-complete and fast-gate checked; hosted provider tests and live calls remain unproven, and both remain PAID approval-gated.
- Azure OpenAI now has a canonical paid descriptor and the existing OpenAI-compatible transport accepts its endpoint environment value and `api-key` header mode; no network call is made by the focused wiring test.
- R1/R3 residual audit: no `McpServerManager` symbol exists; `McpHostManager` is the sole MCP owner and now routes both probe/call process starts through `BoundedProcessRunner`. `BlockType` now has a production caller in `LandingRenderer`; no UI redesign was made.
- Provider connect path now resolves canonical adapter credentials through the existing `DefaultSecretSource` precedence and onboarding aliases; vault-backed `/providers connect` no longer stops at metadata discovery.
- R2 is now source-wired: persisted `/providers prefer` order feeds production route/cascade tie-breaking, while `/providers disable` remains excluded by the healthy set; cost/local policy still outranks preference.
- MCP MarkItDown admission now uses the existing `McpHostManager` bounded territory owner: `convert_to_markdown` is explicitly allowed, while arbitrary write operations remain refused before process start. A focused stdio test covers the real operation name; the root test task is still unavailable in the local `:core:jvmTest` target.
- Bridge recovery projection now exposes the existing `RestartCoordinator.snapshot()` as `/v1/recovery` with restored/rebuilt/failed counts, goal/DAG counts, message, and redacted error strings; no second recovery store or event bus was introduced.
- Bridge file uploads now return both a content hash and an envelope hash over session, filename, size, and content hash, with `attested=true`; the existing territory/symlink boundary remains the write owner.
- MCP health/budget/local-only proof is now source-wired: `McpHostManager.statuses()` persists label-only health metadata, `boundedTools()` truncates to the declared budget, `McpTerritoryBridge` carries no authority, and localOnly refuses remote probing. Core test target passed; root integration test target did not finish locally.

### 2026-08-24T13:00:00Z · Backend batch: attested-upload-envelope-proof

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-005 / ADD-W-029 attested file envelope | source-wired / partial | `src/main/kotlin/atropos/bridge/BridgeFilesHandler.kt`, `src/test/kotlin/atropos/bridge/BridgeFilesHandlerTest.kt`, hosted selectors | `POST /v1/files` → existing `BridgeFilesHandler.upload` → bounded workspace upload | The focused test now recomputes and asserts both the content SHA-256 and the session/filename/content/size envelope SHA-256, proving the response is bound to the uploaded identity rather than merely containing an arbitrary hash. Hosted/root Kotlin execution remains pending; no upload-green claim. |

### 2026-08-24T13:20:00Z · Backend batch: mcp-stdio-lifecycle-proof

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-CORE-g stdio lifecycle cleanup | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | CLI/bridge → existing `McpHostManager.callTool` → `BoundedProcessRunner` → `finally` destroy/reap | Added a fixture that records the configured MCP child PID and asserts it is no longer alive after the bounded tool call returns. Hosted/root Kotlin execution remains pending; no MCP-green claim. |

### 2026-08-24T13:40:00Z · Backend batch: hosted-help-bridge-selector

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-HELP-001 / B-005 command registry and JSON help | source-wired / partial | `src/main/kotlin/atropos/cli/input/CommandRegistry.kt`, `src/main/kotlin/atropos/bridge/projection/CommandProjection.kt`, `src/main/kotlin/atropos/bridge/BridgeRoutes.kt`, `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | `CommandCatalog`/`HelpRegistry` → CLI help and `GET /v1/commands` → `CommandProjection` | Added existing `AtroposBridgeTest`, `HelpRegistryTest`, and `CommandRegistryParityTest` to both hosted focused lanes. No new command/help owner was introduced; hosted execution remains pending. |

### 2026-08-24T14:00:00Z · Backend batch: hosted-status-quota-selector

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-005 / S-001 status, six answers, and quota bridge projections | source-wired / partial | `src/main/kotlin/atropos/bridge/BridgeStatusHandler.kt`, `src/main/kotlin/atropos/bridge/projection/SixAnswersProjection.kt`, `src/main/kotlin/atropos/bridge/projection/QuotaProjection.kt`, `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | `BridgeRoutes` → `/v1/status`/`/v1/answers`/`/v1/quota` → existing projections | Added existing `BridgeStatusHandlerTest`, `SixAnswersProjectionTest`, and `QuotaProjectionTest` to both hosted focused lanes. No parallel status or quota owner was introduced; hosted execution remains pending. |

### 2026-08-24T14:20:00Z · Backend batch: hosted-bridge-surface-complete

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-005 bridge transport/projection test coverage | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh`, existing `src/test/kotlin/atropos/bridge/**` | existing `EngineHttpServer`/`BridgeRoutes`/projection owners → full bridge test surface in hosted selectors | Added all existing bridge, conversation, HTTP, menu, and projection test classes to both canonical hosted lanes. No new bridge owner was introduced; hosted execution remains pending. |

### 2026-08-24T14:45:00Z · Backend batch: hosted-factory-integration-surface

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| FTY-01..05 / B-005 / B-011 factory and integration evidence | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh`, existing `src/test/kotlin/atropos/core/factory/**`, `src/test/kotlin/atropos/core/integration/**` | existing factory routers/loops/guards and integration bridge owners → complete factory/integration test selectors | Added all existing factory and integration test classes to both canonical hosted lanes. No implementation owner was duplicated; hosted execution remains pending. |

### 2026-08-24T15:10:00Z · Backend batch: hosted-provider-policy-surface

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-PROV-001..018 / B-006 policy | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh`, existing `src/test/kotlin/atropos/core/provider/**`, `src/test/kotlin/atropos/core/policy/**` | existing provider descriptors/routes/adapters and `ExecutionPolicyEngine` → complete provider/policy test selectors | Added every existing provider test and every existing policy-owner test to both canonical hosted lanes. No second provider registry or policy engine was introduced; hosted execution remains pending. |

### 2026-08-24T15:40:00Z · Backend batch: hosted-verification-security-surface

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-006 / B-007 / verification gates | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh`, existing `src/test/kotlin/atropos/core/verification/**`, `src/test/kotlin/atropos/core/security/**` | existing gate, authority, territory, redaction, and vault owners → complete verification/security test selectors | Added every existing verification and security test class to both canonical hosted lanes. No invariant owner was duplicated; hosted execution remains pending. |

### 2026-08-24T16:10:00Z · Backend batch: hosted-selector-drift-gate

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| hosted focused-test integrity | source-wired / partial | `scripts/hosted-test-selector-contract.sh`, `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | GitHub Actions and reusable verify script → selector contract → all `--tests` identifiers and source paths | Added a fail-closed contract that requires canonical/reusable selector parity and a real Kotlin test source for every selected class. Local execution passes; hosted Kotlin execution remains pending. |

### 2026-08-24T17:00:00Z · Backend batch: hosted-buildstamp-update-proof

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-INST-002 / B-SUP-030 artifact provenance | source-wired / partial | `src/main/kotlin/atropos/core/BuildStamp.kt`, `src/main/kotlin/atropos/core/SelfUpdate.kt`, `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | `Main --version`/installer → existing `BuildStamp` and `SelfUpdate` owners | Added existing build identity and update tests to both hosted lanes. Release/install contracts already verify the JAR and checksum asset coupling; hosted execution remains pending. |

### 2026-08-24T17:30:00Z · Backend batch: hosted-paid-autonomy-proof

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-PROV-012 / B-SUP autonomy policy surface | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh`, existing `src/test/kotlin/atropos/core/paid/**`, `src/test/kotlin/atropos/core/autonomous/**` | existing `EmergencyPaidGate` and `AutonomousOrchestrator` owners → hosted focused selectors | Added the paid approval gate and autonomous backlog/learning tests to both canonical hosted lanes. No second autonomy or policy owner was introduced; hosted execution remains pending. |

### 2026-08-24T18:00:00Z · Backend batch: hosted-cli-command-surface

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| FTY-01 / B-HELP-001 CLI command surface | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh`, existing `src/test/kotlin/atropos/cli/{FactoryCommandHandler,CommandRouterHelp,CommandRouterIdentity}Test.kt`, `src/test/kotlin/atropos/cli/help/HelpGeneratorTest.kt` | `CommandRouter`/`CommandCatalog` → factory resume, help, identity handlers | Added existing user-facing backend command tests to both hosted lanes; UI renderer tests remain outside the backend-only lane. Hosted execution remains pending. |

### 2026-08-24T16:35:00Z · Backend batch: provider-disable-route-proof

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| R2 / B-PROV-004 provider disable route composition | source-wired / partial | `src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt`, `src/main/kotlin/atropos/core/provider/RoutePolicy.kt`, `src/test/kotlin/atropos/core/provider/ProviderOnboardingTest.kt` | `/providers disable` → persisted `ProviderOnboardingService.healthyProviderIds` → `RoutePolicy` | Added an end-to-end fixture proving a disabled configured provider is excluded by the real onboarding healthy-set supplier while the next eligible free provider is selected. Hosted/root Kotlin execution remains pending. |
- Provider route policy now has a focused test covering both the preferred-provider tie-break and exclusion through the healthy set; the root test remains unexecuted locally because `:test` stalled at compilation.
- Factory runtime audit: `FactoryRunOrchestrator.kt:258-290` invokes the injected repair action, then `FactoryRepairExecutor` and the existing obligation loop; `FactoryCommandHandler.kt:20-47` is the user-facing resume caller; `FactoryRunHandoff.kt:84-108` fails actionable on missing/malformed/mismatched artifacts. No factory status was upgraded without root test evidence.
- Clean-checkout proof after commit `b1479fe8`: `./gradlew :core:jvmTest --no-daemon --max-workers=1 --rerun` passed in 52s; XML reports contain 2 tests, 0 failures, 0 errors, 0 skipped. Root Provider/Bridge/MCP/Factory tests were not re-claimed or executed.

### 2026-08-24T00:25:00Z · Backend batch: configured-mcp-search

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| ADD-MCP-005 / `/mcp search` | partial / source-wired | `McpHostManager.kt:105-120`, `McpCommandHandler.kt:21-43`, `CommandCatalog.kt:29-32`, `McpHostManagerTest.kt` | `CommandRouter` → `McpCommandHandler` → existing `McpHostManager.search` | focused test added; no local root test result claimed. Search is config-only, does not network/install/enable/probe, and localOnly omits remote candidates. `git diff --check` passed. |

### 2026-08-24T01:05:00Z · Backend batch: orphan-gate-enforcement

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| S-012 / new production orphan gate | source-wired / partial | `scripts/find-orphans.py:103-177`, `.github/workflows/compile-gate.yml:31-65` | GitHub Actions `focused-backend-tests` invokes `python3 scripts/find-orphans.py --fail-on-new` | Existing canonical census now indexes token callers instead of scanning every file pair; the opt-in gate fails only newly added `.kt`/`.java` production paths with no caller, preserving known baseline orphans. `python3 -m py_compile`, `timeout 60s ... --fail-on-new` (exit 0), and `git diff --check` passed; hosted CI remains unrun. |

### 2026-08-24T01:25:00Z · Backend batch: zero-retention-research-policy

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-OC-004 / zero-retention research plane | source-wired / partial | `src/main/kotlin/atropos/core/Config.kt`, `src/main/kotlin/atropos/core/factory/FactoryResearchService.kt`, `src/main/kotlin/atropos/cli/BackendDoctor.kt`, focused factory/doctor tests, hosted selector scripts | `AtroposConfig.load` → `FactoryResearchService.collect`; `CommandRouter` → `BackendDoctor.render` | `ATROPOS_ZERO_RETENTION` and `zero_retention_research` config are recognized; lakehouse/bounded remote fetches are skipped with explicit audit lines and doctor status. `git diff --check` passed; root compile/tests remain GitHub Actions evidence. |

### 2026-08-24T01:50:00Z · Backend batch: mcp-http-transport

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-CORE-b/c/d/e / HTTP MCP host transport | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt:18-28,236-290`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | existing `CommandRouter`/`BridgeMcpHandler` → `McpHostManager.callTool` and `statuses` | Remote `http`/`sse`/`streamable-http` entries now parse `url`, perform bounded initialize + tools/list + tools/call JSON-RPC over the existing host, and persist redacted evidence. Local-only and territory gates remain in front. Focused injected-transport tests added; no live network or root Gradle result claimed. |

### 2026-08-24T02:10:00Z · Backend batch: bounded-git-diff-caller

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| R6 / B-MCP-GITLOCAL-b / git diff | source-wired / partial | `src/main/kotlin/atropos/cli/ShellCommandHandler.kt`, `src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt`, `src/main/kotlin/atropos/cli/input/CommandCatalog.kt` | `CommandRouter` → `ShellCommandHandler.git` → `ShellCommandRunner.gitDiff` → existing `TypedToolExecutor`/`BoundedAgencyGate` | `/git diff` is read-only and uses literal `git diff --`; no mutation or remote command was added. `git diff --check` and source inspection passed; root tests remain hosted evidence. |

### 2026-08-24T02:25:00Z · Backend batch: reusable-orphan-gate-wire

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| S-012 / reusable CI orphan gate | source-wired / partial | `scripts/atropos-verify-worktree.sh`, `.github/workflows/atropos-verify-example.yml` | reusable composite action → `scripts/atropos-verify-worktree.sh` → canonical `scripts/find-orphans.py --fail-on-new` | Both compile-gate and reusable verify paths now enforce the same new-file caller check; `git diff --check` passed, hosted execution remains pending. |

### 2026-08-24T04:41:10Z · Backend batch: mcp-http-request-validity

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-CORE-e HTTP MCP request validity | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | CLI/bridge → existing `McpHostManager.callTool` and `statuses` | Corrected invalid Kotlin/JSON string construction; added tool/argument assertions and 2xx response enforcement. `git diff --check` passed. The local `:core:jvmTest` attempt was interrupted before a result; no Gradle count or root-green claim. |

### 2026-08-24T04:43:45Z · Backend batch: installer-platform-contract

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-INST-001 / B-INST-003 platform-aware install | source-wired / partial | `install.sh`, `scripts/install-contract-test.sh` | release/install entrypoint → existing installer → generated `atropos` launcher | Detects Linux/Darwin/Termux and aarch64/x86_64, exports `ATROPOS_PLATFORM`, and rejects unsupported targets before download. `bash -n`, installer contract, and `git diff --check` passed; no JAR/release/device proof claimed. |

### 2026-08-24T04:45:46Z · Backend batch: factory-resume-command-catalog

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| FTY-01 resume command discoverability | source-wired / partial | `src/main/kotlin/atropos/cli/input/CommandCatalog.kt`, `src/test/kotlin/atropos/cli/input/CommandCatalogBackendEntriesTest.kt` | shared `CommandCatalog` → CLI completion/help; execution remains `CommandRouter` → `FactoryCommandHandler` | Added `/factory resume <run-id>` to the single registry and a focused assertion. `git diff --check` passed; hosted root test evidence remains pending. |

### 2026-08-24T04:48:06Z · Backend batch: factory-evidence-backed-waves

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| FTY-03 / FTY-05 evidence-backed obligation waves | source-wired / partial | `FactoryEvidenceWaveExecutor.kt`, `FactoryRunOrchestrator.kt`, `FactoryEvidenceWaveExecutorTest.kt` | `FactoryRunOrchestrator` normal and repair paths → `FactoryEvidenceWaveExecutor.execute` → existing `FactoryObligationLoop.executeUntilSettled` | Blind ready-ID acknowledgement was removed. Manifest/freeze/verification/completion/atom coverage is now checked before terminalization. `git diff --check` passed; root factory tests and runtime repair remain unproven. |

### 2026-08-24T04:51:40Z · Backend batch: provider-onboarding-contract-docs

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-HELP-002 / provider onboarding table | source-wired / partial | `docs/PROVIDER_ENVIRONMENT.md`, `README.md` | README install path → authoritative provider onboarding document; runtime owner remains `ProviderOnboardingService` | Documented aliases, vault/env precedence, secret exclusion, free-first/paid approval, zero-healthy UX, and Bedrock blocked status. `git diff --check` passed. Fast Kotlin lane timed out at 120s with no diagnostics; no compile/test claim. |

### 2026-08-24T04:52:47Z · Backend batch: hosted-backend-gate-expansion

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| hosted compile/focused test gate | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | GitHub Actions `compile` and `focused-backend-tests` jobs; reusable verify script shares the same selectors | Added Java compilation and the new factory/catalog selectors. `bash -n` and `git diff --check` passed; workflow has not executed here and remains the required proof source. |

### 2026-08-24T04:53:50Z · Backend batch: open-core-boundary-doc

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-OC-001 / B-OC-003 boundary documentation | source-wired / partial | `docs/OPEN_CORE_BOUNDARY.md`, `README.md` | README → boundary document; runtime enforcement remains existing `AtroposConfig`, `FactoryResearchService`, `McpHostManager`, and policy owners | Documented local authority, additive hosted sync, local-only behavior, and AGPL section-13 review note. Documentation-only; `git diff --check` passed. |

### 2026-08-24T04:55:26Z · Backend batch: gha-verification-check-publication

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-GHA-f/g check publication | source-wired / partial | `.github/workflows/atropos-verify-example.yml` | manual workflow → existing composite `atropos-verify` action → `github-script` check publication | Publishes outcome/commit summary with fail-closed conclusion mapping and scoped `checks: write`. `git diff --check` passed; workflow has not executed. |

### 2026-08-24T05:02:00Z · Backend batch: mcp-tool-budget-production-wire

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| ADD-MCP-002 / M12 tool injection budget | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | `BridgeMcpHandler.call` and `McpCommandHandler` → shared `McpHostManager.callTool` → `tools/list` parsing → existing `boundedTools` | Stdio and HTTP MCP calls now refuse a requested tool unless it is advertised within the configured bounded tool set; fixtures advertise their tools. `git diff --check` passed. Focused Gradle invocation hung before compile/test output and was terminated; no test count or green claim. |

### 2026-08-24T05:08:00Z · Backend batch: mcp-budget-before-call-ordering

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| ADD-MCP-002 / M12 pre-call enforcement | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | existing `McpHostManager.callTool` from bridge and CLI; stdio exchange now handshakes/list-checks before writing request id 3 | Added a focused fixture asserting an over-budget tool is refused before the server sees `tools/call`; `git diff --check` passed. Gradle execution remains inconclusive in this environment; no root or hosted green claim. |

### 2026-08-24T05:14:00Z · Backend batch: mcp-budget-doctor-visibility

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| ADD-MCP-002 doctor budget visibility | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/main/kotlin/atropos/cli/BackendDoctor.kt`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | existing `CommandRouter` → `BackendDoctor.render` → shared `McpHostManager.budgetSummary` | `/doctor` now prints the same default MCP injection limits used by the host; focused assertion added. `git diff --check` passed; hosted/root execution remains pending. |

### 2026-08-24T05:21:00Z · Backend batch: mcp-memory-authority-gate

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| ADD-MCP-007 memory non-authority | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | existing CLI/bridge `McpHostManager.callTool` → memory/server authority-path mutation guard before process start | Memory-named MCP servers are refused for write-like operations targeting SourceAuthority/governance/ledger paths, even when an injected outer gate allows the request; fixture asserts no process start. `git diff --check` passed; hosted/root execution remains pending. |

### 2026-08-24T05:29:00Z · Backend batch: factory-remove-blind-finalizer

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| FTY-05 anti-false-finish/orphan cleanup | source-wired / partial | `src/main/kotlin/atropos/core/factory/FactoryObligationLoop.kt`, `src/test/kotlin/atropos/core/factory/FactoryObligationLoopTest.kt` | production `FactoryRunOrchestrator` → `executeUntilSettled`; no caller remains for the deleted blind finalizer | Removed the test-only `finalizeAfterVerifiedEvidence` method that marked all open nodes complete without executing them. Replaced it with a refusal test for an empty execution wave. Orphan gate exited 0 with only 4 pre-existing baseline orphans; `git diff --check` passed. Hosted/root factory tests remain pending. |

### 2026-08-24T05:48:00Z · Backend verification batch: local-core-lane-inconclusive

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| hosted verification evidence | unchanged / partial | `STATUS-BACKEND.md`, `AGENTS.md` | GitHub Actions compile/focused-test workflow remains the authoritative root caller | `timeout 75s ./gradlew :core:jvmTest --no-daemon --max-workers=1 --rerun` produced only daemon startup output and was terminated; no test count or pass is claimed. Worktree remained clean and `git diff --check` passed. |

### 2026-08-24T05:57:00Z · Backend batch: mcp-transport-validation

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-CORE-a/b transport safety | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | existing `/mcp` and bridge calls → `McpHostManager.statuses/callTool` → supported transport guard | Unknown transports now report `untested` and cannot fall through to stdio process execution; focused refusal/status fixture added. `git diff --check` passed; hosted/root execution remains pending. |

### 2026-08-24T06:05:00Z · Backend batch: provider-discovery-count-summary

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-PROV-003a/b/c/d discovery summary | source-wired / partial | `src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt`, `src/test/kotlin/atropos/core/provider/ProviderOnboardingTest.kt` | launch `CommandRouter` → existing `ProviderOnboardingService.refresh/render` | Provider launch output now reports deterministic discovered, healthy, and disabled counts before the existing rows/cascade; focused renderer assertion added. `git diff --check` passed; hosted/root provider tests remain pending. |

### 2026-08-24T05:36:00Z · Backend batch: mcp-evidence-reason-bridge-field

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| ADD-MCP-003 result evidence contract | source-wired / partial | `src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt`, `src/test/kotlin/atropos/bridge/BridgeMcpHandlerTest.kt` | `/v1/mcp/call` → existing `BridgeMcpHandler.call` → `McpToolCallResult.evidence` | Bridge responses now include `noEvidenceReason` alongside hash/path, preserving explicit no-evidence outcomes instead of making them indistinguishable from missing fields. Injected HTTP fixture asserts the empty reason on durable evidence. `git diff --check` passed; hosted/root tests remain pending. |
### 2026-08-24T06:20:00Z · Backend batch: attested-instruction-context-import

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-IMPORT cursor-rules / copilot-instructions | source-wired / partial | `src/main/kotlin/atropos/core/agent/ImportedInstructionPackStore.kt`, `AgentContextCollector.kt`, `src/main/kotlin/atropos/cli/commands/AgentCommand.kt`, `CommandCatalog.kt`, `src/test/kotlin/atropos/core/agent/AgentContextCollectorTest.kt` | `/agent context import <path>` → `ImportedInstructionPackStore.import`; existing `AgentContextCollector.collect/collectPatch/collectRepair` → verified local packs | Imported files stay inside repo territory, are redacted before persistence, receive a SHA-256 id, and load only when the hash and `CONTEXT_ONLY` authority marker verify. Source Docs/ATROPOS policy remain authoritative. Focused tests added; `git diff --check` and Python orphan-script syntax passed. Gradle/root and hosted execution remain pending; no green claim. |

### 2026-08-24T06:35:00Z · Backend batch: hosted-context-import-test-wire

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-IMPORT hosted acceptance wiring | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | GitHub Actions focused backend lane → `atropos.core.agent.AgentContextCollectorTest` | The canonical hosted workflow and reusable verification script now execute the import/redaction/hash/load test class after root compilation. YAML/shell static checks and hosted execution remain pending. |

### 2026-08-24T06:50:00Z · Backend batch: npm-artifact-hash-fail-closed

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-INST-006 npm fallback integrity | source-wired / partial | `npm/scripts/postinstall.js`, `npm/README.md`, `scripts/npm-installer-contract-test.sh`, `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | npm `postinstall` → existing release artifact URL/checksum files; hosted compile lane and reusable verifier invoke the contract script | npm installation now fails closed when the published checksum is missing, malformed, mismatched, or the download fails; explicit `ATROPOS_SKIP_DOWNLOAD=1` remains the only offline launcher-only path. `NPM_INSTALLER_CONTRACT_OK`, `bash -n`, and `git diff --check` passed; release/network execution remains pending. |

### 2026-08-24T07:05:00Z · Backend batch: shell-installer-hash-fail-closed

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-INST-002 artifact hash verification | source-wired / partial | `install.sh`, `scripts/install-contract-test.sh` | installer download path → required `.sha256` artifact → `sha256sum`/`shasum` verification before atomic jar move | The shell installer now refuses missing/malformed checksums, refuses hosts without a SHA-256 tool, and refuses mismatches; it no longer installs an unverified jar. `bash -n`, `ATROPOS_INSTALL_CONTRACT_OK`, npm contract, and `git diff --check` passed. Live release/device execution remains pending. |

### 2026-08-24T07:20:00Z · Backend batch: release-installer-contract-wire

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-INST release acceptance | source-wired / partial | `.github/workflows/release.yml` | Release JAR job → shell installer contract + npm installer contract before artifact packaging/publish | Release CI now executes both installer contract scripts before publishing the JAR and checksums. Local static checks remain available; hosted release execution has not run. |

### 2026-08-24T07:40:00Z · Backend batch: bounded-local-git-argv-proof

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-GITLOCAL status/diff bounded wire | source-wired / partial | `src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt`, `src/main/kotlin/atropos/cli/ShellCommandHandler.kt`, `src/test/kotlin/atropos/cli/shell/ShellBoundedAgencyTest.kt`, hosted selectors | `/git status|diff` → `ShellCommandHandler` → existing `ShellCommandRunner` → `TypedToolExecutor`/`BoundedAgencyGate` | Focused tests now assert exact literal argv and the `git diff --` path boundary at the process seam; both GitHub backend lanes select the suite. Static selector checks and `git diff --check` passed; root/hosted test execution remains pending. |

### 2026-08-24T08:20:00Z · Backend batch: gated-configured-mcp-search

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| ADD-MCP-005 gated configured search | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | `/mcp search` → `McpCommandHandler` → shared `McpHostManager.search` → existing `McpTerritoryBridge`/policy gate | Configured-only search now requires the same inspect admission and policy decision before reading candidates; a denied gate test proves no search result bypass. No download/install/enable/probe behavior was added. Root/hosted tests remain pending. |

### 2026-08-23T09:30:00Z · Backend batch: mcp-example-catalog-contract

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| ADD-MCP-EX-001..013 catalog examples | source-wired / partial | `docs/mcp-examples/*.json`, `scripts/mcp-example-contract-test.sh`, `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | GitHub compile gate and reusable verifier → `mcp-example-contract-test.sh` → JSON catalog fixtures | Added the missing config-only examples for fetch, SQLite, Postgres, Docker, code intelligence, npm, cloud deploy, browser use, and n8n. Every example is disabled, marked community, and carries no secret; no adapter or auto-install path was added. Contract and JSON parse passed locally; hosted execution remains pending. |

### 2026-08-23T09:45:00Z · Backend batch: command-catalog-backend-parity

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-HELP-001 command/help registry parity | source-wired / partial | `src/main/kotlin/atropos/cli/input/CommandCatalog.kt`, `src/main/kotlin/atropos/cli/CommandRouter.kt`, `src/test/kotlin/atropos/cli/input/CommandCatalogBackendEntriesTest.kt` | `/help` → existing `CommandRouter`/`HelpGenerator` → shared `CommandCatalog` | Focused contract now asserts factory resume, provider connect/prefer/disable, MCP search/call/ingest, keys status, and context import are all discoverable from the one registry. Hosted/root test execution remains pending. |

### 2026-08-23T10:00:00Z · Backend batch: bridge-approval-attribution-proof

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-005 approval endpoint role boundary | source-wired / partial | `src/main/kotlin/atropos/bridge/BridgeApprovalHandler.kt`, `src/main/kotlin/atropos/bridge/BridgeRoutes.kt`, `src/test/kotlin/atropos/bridge/BridgeApprovalHandlerTest.kt`, hosted selectors | `POST /v1/approvals/decide` → `BridgeApprovalHandler` → `PendingApprovalStore.decide` | Focused proof covers required attribution, durable approval recording, and refusal of a second decision; no route or policy owner was duplicated. Root/hosted execution remains pending. |

### 2026-08-23T10:20:00Z · Backend batch: bounded-process-owner-cleanup

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| T03 bounded process ownership | source-wired / partial | `src/main/kotlin/atropos/cli/HistoryCommandHandler.kt`, `src/main/kotlin/atropos/cli/DiffCommandHandler.kt`, `src/main/kotlin/atropos/core/worktree/BoundedGitWorktreeCommandRunner.kt`, hosted selectors | `/history` and `/diff` plus all typed worktree operations → existing `BoundedProcessRunner` | Removed the three direct production `ProcessBuilder` call sites; source scan now leaves only the canonical runner and verifier scanner. Existing bounded worktree and reachability tests are selected in both GitHub lanes; hosted/root execution remains pending. |

### 2026-08-23T11:00:00Z · Backend batch: github-rest-boundary

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-GITHUB issues/PRs/checks | source-wired / partial | `src/main/kotlin/atropos/core/github/GitHubApiClient.kt`, `GitHubBinding.kt`, `src/test/kotlin/atropos/core/github/GitHubApiClientTest.kt`, hosted selectors | existing `GitHubBinding.api` → single `GitHubApiClient` → `BoundedAgencyGate` decision → `SecretSource`/`SecretSinkMatrix` → injected/default HTTPS transport | Added typed list/get/create/comment issue, PR/files, and check-run operations with repository path bounds, response redaction/hash, token aliases plus vault source, and approval-required network policy. No live credential/network call was made; focused injected tests are wired for GitHub Actions. |

### 2026-08-23T11:20:00Z · Backend batch: github-territory-argument-hardening

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-GITHUB territory declaration | source-wired / partial | `src/main/kotlin/atropos/core/github/GitHubApiClient.kt`, `src/test/kotlin/atropos/core/github/GitHubApiClientTest.kt` | `GitHubBinding.api` → `GitHubApiClient.execute` → `ActionProposal.targetPaths` | GitHub API requests now require a non-empty traversal-free declared territory, which is carried into the canonical policy proposal; injected test asserts the territory reaches the gate. Hosted/root execution remains pending. |

### 2026-08-24T08:45:00Z · Backend batch: reusable-action-evidence-output

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-GHA action inputs/output and evidence capture | source-wired / partial | `.github/actions/atropos-verify/action.yml`, `.github/workflows/atropos-verify-example.yml`, `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh`, `scripts/atropos-verify-action-contract-test.sh` | GitHub reusable action → existing `scripts/atropos-verify-worktree.sh`; example workflow publishes the action output | The single reusable action now accepts its working directory and verifier path, preserves the verifier exit code, captures unique SHA-256 tokens, and exposes them as `evidence-hashes`; a local action contract checks the metadata and `PIPESTATUS`/`GITHUB_OUTPUT` behavior. Also fixed the detached `GitHubApiClientTest` selector in the reusable script. `bash -n`, action contract, and `git diff --check` passed; GitHub-hosted execution remains pending. |

| B-MCP-GHA check evidence publication | source-wired / partial | `.github/workflows/atropos-verify-example.yml` | existing reusable action output → `actions/github-script` check summary | The example check now includes the bounded action's evidence-hash output in its published summary while preserving failure/cancelled conclusions. Hosted workflow execution remains pending. |

| B-MCP-GHA pull-request trigger | source-wired / partial | `.github/workflows/atropos-verify-example.yml`, `scripts/atropos-verify-action-contract-test.sh` | GitHub `pull_request` event → existing `atropos-verify` composite action | The example verification check now runs on pull requests as well as manual dispatch; the local workflow contract checks the trigger and required checkout/setup/action/check steps. Hosted execution remains pending. |

### 2026-08-24T09:20:00Z · Backend batch: github-single-transport-owner

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-GITHUB single transport owner | source-wired / partial | `src/main/kotlin/atropos/core/github/GitHubApiClient.kt`, `src/main/kotlin/atropos/core/scavenge/GitHubScavenger.kt` | `/scavenge` → existing `GitHubScavenger` → `GitHubApiClient.searchIssues` → canonical network/secret/territory gate | Read-only public-work discovery now composes onto the gated GitHub client in production; the scavenger callback remains injectable only for deterministic tests. Hosted/root tests and operator-approved network proof remain pending. |

| B-MCP-GITHUB scavenger hosted selector | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | hosted focused backend Gradle command → `atropos.core.scavenge.GitHubScavengerTest` | The existing deterministic scavenger suite is now selected alongside the unified GitHub client suite in both hosted lanes; no local root test result is claimed. |

| backend final ownership audit | verified locally / hosted pending | `src/main/kotlin/atropos/core/github/GitHubApiClient.kt`, `src/main/kotlin/atropos/core/scavenge/GitHubScavenger.kt`, `src/main/kotlin/atropos/core/policy/BoundedProcessRunner.kt` | production `/scavenge`, GitHub binding, and typed process callers | Source scan finds one production GitHub HTTP owner and only the canonical bounded process runner; action and MCP example contracts pass, `git diff --check` passes. The 60-second orphan scan timed out without a result in this audit and is not claimed. |

| B-MCP-GITHUB unified scavenger behavior | source-wired / partial | `src/test/kotlin/atropos/core/scavenge/GitHubScavengerTest.kt`, hosted selectors | focused `GitHubScavengerTest.production_path_delegates_to_the_gated_github_client` → `GitHubApiClient.searchIssues` injected transport | Added behavioral proof that production-mode scavenging uses the gated client, sends both searches through the canonical GitHub endpoint, and never bypasses the token/policy boundary. Hosted/root test execution remains pending. |

| B-MCP-GITHUB typed operation callers | source-wired / partial | `src/main/kotlin/atropos/cli/GitHubCommandHandler.kt`, `src/main/kotlin/atropos/cli/CommandRouter.kt`, `src/main/kotlin/atropos/core/github/GitHubBinding.kt`, `src/main/kotlin/atropos/cli/input/CommandCatalog.kt`, `src/test/kotlin/atropos/cli/input/CommandCatalogBackendEntriesTest.kt` | `/github issues|prs|checks` → `GitHubCommandHandler` → `GitHubBinding` → `GitHubApiClient` | Read-only issue, pull-request, and check-run operations now have a user-facing production caller, shared catalog entries, local-only refusal, bounded redacted rendering, and evidence hashes. Hosted/root tests remain pending. |

| B-MCP-OAUTH-UX | blocked | no new owner added | no production caller | GitHub PAT/env/vault paths work through the existing secret boundary; browser/device OAuth requires an approved OAuth client ID and operator authorization, neither of which is present. No client secret or fake OAuth flow is embedded. |

| B-PROV-006 provider workers | blocked | `AutonomousOrchestrator` has no existing provider-worker fan-out owner | no production caller can be added without a second planner/orchestrator | Deliberately not implemented: provider workers require an existing Director hierarchy seam; creating a parallel orchestrator would violate the non-duplication rule. |

| focused production compile proof | inconclusive / partial | changed GitHub CLI/client slice | GitHub Actions compile job is the authoritative caller | A Termux `kotlinc` compile against the stale JAR classpath hung without diagnostics and was terminated; no focused compile or root-green claim is made. Worktree remains clean. |

| B-MCP-GITHUB binding test selector | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | hosted focused backend Gradle command → `atropos.core.github.GitHubBindingTest` | Existing authorization, territory, push, and repository-provisioning binding tests now run with the typed client/scavenger tests in both hosted lanes. Hosted execution remains pending. |

| B-MCP-GITHUB read operation callers | source-wired / partial | `src/main/kotlin/atropos/core/github/GitHubApiClient.kt`, `GitHubBinding.kt`, `src/main/kotlin/atropos/cli/GitHubCommandHandler.kt`, `CommandCatalog.kt`, `GitHubApiClientTest.kt` | `/github issue|pr-files|branch-protection` → existing `GitHubBinding` → `GitHubApiClient` | Added bounded get-issue, PR-files, and branch-protection read paths with positive-number/ref validation, shared policy/secret gates, catalog callers, and injected endpoint assertions. Hosted/root tests remain pending. |

| B-MCP-GH write micro-atoms | source-wired / partial | `GitHubApiClient.kt`, `GitHubBinding.kt`, `GitHubCommandHandler.kt`, `CommandCatalog.kt`, `GitHubApiClientTest.kt` | `/github create-issue|comment-issue|create-pr|comment-pr|request-review|create-check|update-check` → existing gated client | Added issue/PR/check mutation callers behind mandatory `GitHubWriteAuthorization` (`--confirm <id>`); POST/PATCH refuse before secret lookup/transport without confirmation. No mutation was executed; hosted tests remain pending. |

| caller/contract audit after GitHub CLI wire | passed locally / hosted pending | `scripts/find-orphans.py`, `.github/actions/atropos-verify/action.yml`, `.github/workflows/atropos-verify-example.yml`, `docs/mcp-examples/*.json` | canonical orphan gate and reusable contracts | `find-orphans.py --fail-on-new` exited 0 with 4 pre-existing baseline orphans of 1032 production files; action/workflow contracts and 14 MCP examples passed; root/hosted Gradle remains pending. |

| FTY-03 repair evidence redaction | source-wired / partial | `src/main/kotlin/atropos/core/factory/FactoryLiveRepairAction.kt`, `src/test/kotlin/atropos/core/factory/FactoryLiveRepairActionTest.kt` | `FactoryRunOrchestrator` repair callback → `FactoryLiveRepairAction` → `FactoryAcceptanceFreeze.RepairEvidence` | Repair command evidence is now passed through the existing `RedactionFilter`; execution argv is unchanged. Focused test covers an API-key argument; hosted/root execution remains pending. |

### 2026-08-24T06:29:57Z · Backend batch: github-write-contract-gate

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-GH write authorization regression gate | source-wired / partial | `scripts/github-write-contract-test.sh`, `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | GitHub compile/focused lane and reusable verifier → contract script → `GitHubApiClient`/`GitHubCommandHandler` write boundary | Added a CI contract requiring `GitHubWriteAuthorization`, the pre-secret/pre-transport refusal test, all seven `/github` mutation commands, and `--confirm <id>`. Local result: `GITHUB_WRITE_CONTRACT_OK operations=7`; action/workflow and MCP-example contracts also passed; orphan gate exited 0 with 4 pre-existing orphans. Hosted Gradle/Actions execution remains pending. |

### 2026-08-24T06:33:33Z · Backend batch: provider-runtime-test-selector-completion

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| P02/P18 provider activation and failure cascade proof | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | canonical hosted compile/focused lane and reusable verifier → existing provider test classes | Added the existing `ProviderActivationServiceTest`, `ProviderCascadeOrderTest`, `ProviderFailureClassifierTest`, and `ProviderErrorNormalizerTest` to both GitHub-focused lanes. This covers paid live-test refusal, explicit-network opt-in, cooldown/quota transitions, billing non-retry, and normalized 429/billing failures without live provider calls. `bash -n` and `git diff --check` passed; hosted execution remains pending. |

### 2026-08-24T06:35:49Z · Backend batch: provider-credential-shape-health

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-PROV-002a/b cheap credential-shape health | source-wired / partial | `src/main/kotlin/atropos/core/provider/ProviderOnboarding.kt`, `src/test/kotlin/atropos/core/provider/ProviderOnboardingTest.kt` | launch/CLI `ProviderOnboardingService.refresh` → persisted provider health and RoutePolicy healthy set | Added secret-safe shape validation: control characters/newlines in credential-shaped environment values classify the provider `unhealthy`; no value is persisted or rendered, and provider-specific prefix/length guesses are intentionally avoided. Focused test added; local diff/orphan/contracts pass; hosted provider test execution remains pending. |

### 2026-08-24T06:36:45Z · Backend batch: provider-metadata-reload-proof

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-PROV-002g/h metadata persistence and reload | source-wired / partial | `src/test/kotlin/atropos/core/provider/ProviderOnboardingTest.kt` | fresh `ProviderOnboardingService` instance → existing `providers.json` reader → healthy/preference/disable route metadata | Added a fresh-instance proof that preferred and disabled state survives a process boundary while both environment secret values stay absent from persisted metadata. Local diff/orphan/contract checks pass; hosted provider test execution remains pending. |

### 2026-08-24T06:39:26Z · Backend batch: editor-bridge-contract

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-013 thin editor extension host | source-wired / partial | `src/main/kotlin/atropos/bridge/BridgeEditorHandler.kt`, `BridgeRoutes.kt`, `src/test/kotlin/atropos/bridge/BridgeEditorHandlerTest.kt`, hosted selectors | VS Code/JetBrains/Neovim-compatible local client → `GET /v1/editor/context` and `POST /v1/editor/selection` → existing status/six-answers/checkpoint projections and `BridgeConversationHandler` | Added one bridge adapter exposing existing status, six answers, checkpoint, and bounded selection forwarding. Selection requires attribution, relative territory-safe path, bounded size, valid lines, and redaction; it creates no editor process or orchestration owner. Local diff/orphan/contract checks pass; hosted root tests remain pending. |

| B-013 focused execution evidence | inconclusive / partial | `src/test/kotlin/atropos/bridge/BridgeEditorHandlerTest.kt`, `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | hosted selector → `BridgeEditorHandlerTest` | A 45-second local root selector stalled before compile/test output and was terminated; no local pass/count is claimed. The focused class is wired into both GitHub backend lanes. |

## Explicit backend blockers and scope exclusions

| atom | status | reason | safe next evidence |
| --- | --- | --- | --- |
| B-MCP-SENTRY / B-017 | source-wired / partial | The approved-in-tree REST seam, canonical `IntegrationRegistry`, `BoundedAgencyGate`, territory mapping, proposal, and evidence path now exist; configured credentials and live issue evidence are absent. | Run the existing `/sentry inspect|propose` path with operator-approved credentials and hosted tests; do not claim live repair without that evidence. |
| B-MCP-OAUTH-UX | blocked | GitHub/Linear browser OAuth requires an operator-approved OAuth client ID and authorization; PAT/env/vault paths are already available. | Supply approved client configuration, then add a bounded OAuth flow through the existing vault. |
| B-PROV-006 provider workers | blocked | No existing Director fan-out owner can host provider workers; creating one would create a second orchestrator. | Extend the existing Director hierarchy when that owner is available. |
| P07 AWS Bedrock | source-wired / partial | `aws_bedrock` now has the descriptor, `BuildKernelAdapter` route, pure SigV4 signer, injected Converse transport, and offline-focused tests; hosted execution and approved AWS credentials are absent. | Run the existing focused adapter tests in GitHub Actions; use live AWS only after explicit paid approval. |
| P09 Perplexity | skipped | No accepted descriptor, transport reference, or existing tree owner is present. | Re-open only with an accepted provider contract. |
| B-MCP-GITLOCAL mutation micro-atoms | source-wired / partial | `GitMutationCommand.kt`, `ShellCommandHandler.kt`, `ShellCommandRunner.kt` → existing `TypedToolExecutor`/`BoundedAgencyGate` | Explicit `/git add|commit|rebase-continue --confirm <id>` path now exists; the existing agency gate remains authoritative and no real mutation was run. | Hosted/root focused tests and operator-approved execution evidence remain pending. |
| B-018 Slack/Discord distribution; B-019 browser verification | deferred | Distribution/browser execution is outside the current Tier-0 backend lane; generic MCP examples remain disabled-by-default and no adapter farm is allowed. | Re-open after core bridge/GHA proof is hosted-green and a transport owner is accepted. |

### 2026-08-24T06:48:39Z · Backend batch: anti-synthetic-velocity-output

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| AcceptanceVelocity / verification evidence honesty | source-wired / partial | `src/main/kotlin/atropos/cli/commands/VerifyCommand.kt` | `/verify narrow|wide` → existing `VerifyCommand` → `AcceptanceVelocity.calculate` with an explicitly empty authoritative event set | Removed the fabricated successful `VerificationEvent` that reported velocity before compilation. `/verify` now renders `velocity=unmeasured` until a real event-store projection supplies predicate events. `git diff --check` passed; orphan gate reports only the same 4 pre-existing files; root/hosted tests remain pending. |

### 2026-08-24T06:52:00Z · Backend batch: mcp-process-owner-injection

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-CORE-b/g bounded MCP process ownership | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt` | `/mcp` and bridge → shared `McpHostManager.statuses/callTool` → the manager's injected `BoundedProcessRunner` | Default stdio health probes now use the same injected bounded process runner as tool calls; a hidden second runner was removed. Static source check confirms no direct `BoundedProcessRunner()\.start` remains in the MCP host. Root/hosted tests remain pending. |

### 2026-08-24T06:57:00Z · Backend batch: mcp-sse-frame-normalization

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-CORE-b/c/d/e SSE response framing | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | configured `/mcp` and bridge remote calls → `McpHostManager.remoteExchange` → existing JSON-RPC probe/call parser | Existing HTTP/SSE/streamable-HTTP owner now extracts bounded `data:` frames while leaving raw JSON unchanged; an injected SSE probe fixture proves initialize/tools-list recognition. Root/hosted tests remain pending. |

### 2026-08-24T07:08:00Z · Backend batch: mcp-remote-response-bound

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-CORE-e/f bounded remote response | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | `/mcp` and bridge remote calls → shared `remoteExchange` → bounded HTTP/SSE JSON-RPC parser/evidence path | Remote responses are now size-checked before normalization/parsing; the default HTTP body handler also refuses responses over the host bound. An injected oversized-response fixture proves refusal before `tools/call` can succeed. Root/hosted tests remain pending. |

### 2026-08-24T07:02:00Z · Backend batch: hosted-velocity-selector

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| AcceptanceVelocity hosted proof wiring | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | GitHub compile/focused backend lanes → existing `AcceptanceVelocityTest` | Added the existing metric-owner test to both canonical hosted selectors. `bash -n`, action/workflow contract, `git diff --check`, and orphan gate passed locally; hosted execution remains pending. |

### 2026-08-24T07:18:00Z · Backend batch: gated-local-git-mutations

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-GITLOCAL-c/d/e/f mutation callers | source-wired / partial | `src/main/kotlin/atropos/cli/GitMutationCommand.kt`, `ShellCommandHandler.kt`, `CommandCatalog.kt`, `src/test/kotlin/atropos/cli/GitMutationCommandTest.kt`, hosted selectors | `/git add|commit|rebase-continue` → `GitMutationCommandParser` → existing `ShellCommandRunner` → `TypedToolExecutor`/`BoundedAgencyGate` | Added local Git mutation commands with mandatory `--confirm <id>`, traversal-safe add paths, bounded commit messages, exact rebase shape, and no confirmation token forwarded to Git. Existing agency policy still decides execution; no mutation was run. Root/hosted tests remain pending. |

### 2026-08-24T07:25:00Z · Backend batch: local-git-policy-composition

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-GITLOCAL mutation policy execution | source-wired / partial | `src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt`, `GitMutationCommand.kt`, `src/test/kotlin/atropos/cli/shell/ShellBoundedAgencyTest.kt` | parsed confirmed `/git` command → `ShellCommandRunner.runGitMutation` → repository-scoped `FILE_MUTATION` proposal → existing agency gate | Explicitly confirmed Git mutations now compose as bounded repository file mutations instead of being rejected merely by the read-only `GIT` class; the confirmation token is never sent to the process. The execution seam test asserts exact argv; no real mutation was run. Root/hosted tests remain pending. |

### 2026-08-24T07:32:00Z · Backend batch: installer-latest-release-url

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-INST-002 latest artifact resolution | source-wired / partial | `install.sh`, `scripts/install-contract-test.sh` | installer entrypoint → version branch → exact rolling `latest` tag or pinned-release asset URL | Default installation now uses `/releases/download/latest/ATROPOS.jar` because the rolling `latest` release is prerelease; pinned `ATROPOS_VERSION` continues using `/releases/download/<version>/ATROPOS.jar`. Checksum verification and doctor remain fail-closed. Local installer/release contracts, diff check, and orphan gate passed; live release/device proof remains pending. |

### 2026-08-24T07:45:00Z · Backend batch: installer-release-asset-contract

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-INST-002 release producer/consumer coupling | source-wired / partial | `.github/workflows/release.yml`, `scripts/release-installer-contract-test.sh` | GitHub Release JAR job → contract script → exact `ATROPOS.jar` and `ATROPOS.jar.sha256` assets consumed by `install.sh` | Added a deterministic release contract asserting the rolling `latest` tag, checksum generation, and both asset names. Local `bash -n`, release/install contracts, orphan gate, and `git diff --check` pass. GitHub Actions execution, published release, and device install remain unproven. |

### 2026-08-24T07:55:00Z · Backend batch: installer-prerelease-tag-correction

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-INST-002 rolling artifact selection | source-wired / partial | `install.sh`, `scripts/install-contract-test.sh`, `scripts/release-installer-contract-test.sh` | default installer → exact `latest` release tag → release workflow assets | Corrected default download selection to `/releases/download/latest/ATROPOS.jar`; the release workflow marks that rolling tag prerelease, so `/releases/latest/download` could select an older stable release. Local contracts and syntax checks pass; hosted release/device proof remains pending. |

### 2026-08-24T08:10:00Z · Backend batch: mcp-single-stdio-call

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-CORE-e stdio tool call cardinality | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | CLI/bridge → `McpHostManager.callTool` → one bounded stdio handshake/tools-list/tools-call exchange | Added a focused fixture asserting exactly one `tools/call` reaches the configured server. The production path already has the duplicate exchange removed; hosted/root Gradle execution remains pending. |

### 2026-08-24T08:45:00Z · Backend batch: provider-env-documentation-contract

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-HELP-002a provider environment table parity | source-wired / partial | `scripts/provider-env-contract-test.sh`, `docs/PROVIDER_ENVIRONMENT.md`, `.github/workflows/compile-gate.yml`, `.github/workflows/release.yml` | GitHub compile/release jobs → contract script → canonical `StaticProviderDescriptorRegistry.kt` and onboarding document | Added a Termux-compatible contract deriving every canonical descriptor env name from the existing registry, checking documentation coverage, and checking aliases/globs/AWS discovery names. Local contract, orphan gate, and diff check pass; hosted execution remains pending. |

### 2026-08-24T09:15:00Z · Backend batch: installer-six-answer-doctor

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-INST-005 post-install doctor + six answers | source-wired / partial | `src/main/kotlin/atropos/cli/FirstRunDoctorRenderer.kt`, `src/main/kotlin/atropos/Main.kt`, `install.sh`, `scripts/install-contract-test.sh`, `src/test/kotlin/atropos/cli/FirstRunDoctorRendererTest.kt`, hosted selectors | installer → generated launcher `--doctor` → `FirstRunDoctorRenderer` → existing `HomeStateProvider` six answers + `BackendDoctor` | Added a non-interactive `--doctor` entrypoint that reads all six answers from durable runtime state and composes existing provider/MCP truth; installer now runs it after `--health` and stores the report. Local shell/install/orphan/diff checks pass; root/hosted Kotlin execution remains pending. |

### 2026-08-24T08:25:00Z · Backend batch: bounded-git-conflict-list

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-GITLOCAL-f conflict file list | source-wired / partial | `src/main/kotlin/atropos/cli/ShellCommandHandler.kt`, `src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt`, `src/main/kotlin/atropos/cli/input/CommandCatalog.kt`, `src/test/kotlin/atropos/cli/shell/ShellBoundedAgencyTest.kt` | `/git conflicts` → existing `ShellCommandHandler` → `ShellCommandRunner.gitConflicts` → existing `TypedToolExecutor`/`BoundedAgencyGate` | Added a read-only literal `git diff --name-only --diff-filter=U` caller. No raw process path, mutation, remote operation, or parallel git owner was added; hosted/root tests remain pending. |

### 2026-08-24T11:20:00Z · Backend batch: github-gate-unique-ref

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| Phase 11 remote compile gate / GitHub Actions snapshot isolation | source-wired / partial | `src/main/kotlin/atropos/core/verification/GitHubActionsCompileRunner.kt`, `src/test/kotlin/atropos/core/verification/GitHubActionsCompileRunnerTest.kt`, `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | `GovernedCompileGate.forRepository` → existing `GitHubActionsCompileRunner` → one commit-derived workflow ref → `compile-gate.yml` | Remote snapshots now push without `--force` to `atropos/compile-gate/<commit-prefix>`, dispatch and polling use that same ref, and the focused test asserts the ref and absence of force-push. Shell syntax and `git diff --check` pass; GitHub-hosted execution is still required and no CI green claim is made. |

### 2026-08-24T11:45:00Z · Backend batch: npm-launcher-contract

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-INST-006 npm fallback launcher | source-wired / partial | `npm/bin/atropos.js`, `npm/scripts/postinstall.js`, `scripts/npm-installer-contract-test.sh`, `.github/workflows/compile-gate.yml`, `.github/workflows/release.yml` | `npm install -g @mjmichaelware/atropos` → postinstall checksum path → `npm/bin/atropos.js` → JVM jar | Contract now proves the launcher honors `ATROPOS_JAR` and `ATROPOS_JAVA_OPTS`, inherits terminal streams, preserves JVM exit status, and fails nonzero on signal termination; postinstall remains checksum fail-closed. `NPM_INSTALLER_CONTRACT_OK` passes locally; release/network/device proof remains external. |

### 2026-08-24T12:10:00Z · Backend batch: hosted-snapshot-secret-boundary

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-GHA / T04 hosted compile snapshot secret boundary | source-wired / partial | `src/main/kotlin/atropos/core/verification/GitHubActionsCompileRunner.kt`, `src/main/kotlin/atropos/core/security/ContextPathExclusions.kt`, `src/test/kotlin/atropos/core/verification/GitHubActionsCompileRunnerTest.kt`, `src/test/kotlin/atropos/core/security/ContextPathExclusionsTest.kt`, hosted selectors | `GovernedCompileGate.forRepository` → `GitHubActionsCompileRunner.snapshotWorkingTree` → existing `ContextPathExclusions` | Changed excluded credential paths, including vendor-prefixed `.env` files, now fail the remote snapshot before `git push`; source files remain allowed. Focused refusal tests and selectors were added. Local Kotlin/hosted execution remains pending; no CI green claim. |

### 2026-08-24T12:35:00Z · Backend batch: bridge-event-stream-identity-proof

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-005 / ADD-W-001 event stream request identity | source-wired / partial | `src/main/kotlin/atropos/bridge/http/EngineHttpServer.kt`, `src/main/kotlin/atropos/bridge/BridgeRoutes.kt`, `src/main/kotlin/atropos/bridge/BridgeEventsHandler.kt`, `src/test/kotlin/atropos/bridge/BridgeStreamTest.kt`, hosted selectors | loopback `EngineHttpServer` → existing `BridgeRoutes.streamRoutes` → `BridgeEventsHandler.streamEvents(request, ...)` → `BridgeEventHub` | Added a real-socket focused test proving `/v1/events/stream?session=<id>&after=0` emits the requested session’s event and excludes another session’s event. Hosted/root Kotlin execution remains pending; no bridge-green claim. |

### 2026-08-24T18:30:00Z · Backend batch: hosted-agent-runtime-test-surface

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| Phase 11 self-build/resume/repair focused evidence | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | existing hosted `:jvmTest` invocation → explicit `atropos.core.agent.*Test` selectors → existing agent/self-host production owners | Added the remaining existing `core.agent` test classes (48 selectors; hosted selector set 158 → 206) to both CI entrypoints. Selector/source parity, shell syntax, diff check, and orphan gate pass locally; GitHub-hosted Kotlin execution remains pending, so no Phase 11/root-green claim. |

### 2026-08-24T18:45:00Z · Backend batch: agent-continuation-selector-completion

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| Phase 11 agent continuation/lease/proposal focused evidence | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | existing hosted `:test` invocation → explicit remaining `atropos.core.agent.*Test` selectors → existing continuation, lease, proposal, and redaction owners | Added 17 previously unselected existing agent tests; selector set moves 206 → 223. Structural selector parity, shell syntax, diff check, and orphan gate remain local evidence only; hosted Kotlin execution is still pending. |

### 2026-08-24T19:05:00Z · Backend batch: core-runtime-verification-selector-sweep

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| Backend runtime/recovery/DAG/territory/evidence test coverage | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | existing hosted `:test` invocation → 115 explicit core selectors → existing backend owners | Added the remaining existing core backend test classes for approval, artifacts, authority, DAG/director, recovery, Phase 20 ledgers, storage, territory, verifier, and worktree. Selector set moves 223 → 338; structural checks pass locally. Hosted Kotlin execution remains pending; no root-green claim. |

### 2026-08-24T19:25:00Z · Backend batch: remaining-core-contract-selector-sweep

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| Remaining core ingest/intent/parser/platform/project contract evidence | source-wired / partial | `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | existing hosted `:test` invocation → 39 explicit core selectors → existing ingest, intent, parser, platform, project, and contract owners | Added every remaining existing `src/test/kotlin/atropos/core/**Test.kt` class to both CI entrypoints; selector set moves 338 → 377. Selector/source parity, shell syntax, diff check, and orphan gate pass locally. Hosted Kotlin execution remains pending. |

### 2026-08-24T20:15:00Z · Backend batch: sentry-tier0-composition

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-SENTRY issue→frame→territory→proposal→evidence | source-wired / partial | `src/main/kotlin/atropos/core/sentry/SentryApiClient.kt`, `SentryRepairCoordinator.kt`, `src/main/kotlin/atropos/cli/SentryCommandHandler.kt`, `CommandRouter.kt`, `CommandCatalog.kt`, `CommandRiskCatalog.kt`, focused Sentry tests, hosted selectors | `/sentry inspect|propose` → `SentryCommandHandler` → gated `SentryApiClient` → `SentryRepairCoordinator` → existing `WorkerCodeProposalService` → existing independent verification and `EvidenceStore` | Added an injectable Sentry REST owner with env/vault token precedence, egress/gate checks, redacted response hashing, issue/frame parsing, repository/territory mapping, and proposal evidence. Focused Gradle attempt timed out during root compilation on Termux; selector/static checks remain pending in this batch. No live Sentry credential or network call was used. |

### 2026-08-24T20:35:00Z · Backend batch: first-party-integration-catalog

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-SENTRY registry/doctor ownership | source-wired / partial | `src/main/kotlin/atropos/core/integration/IntegrationRegistry.kt`, `BackendDoctor.kt`, `SentryApiClient.kt`, focused registry test, hosted selectors | `/doctor` → `BackendDoctor` → canonical `IntegrationRegistry`; Sentry request path → `requireRegistered("sentry")` | Added one canonical first-party integration catalog for existing GitHub/MCP/Sentry owners; no parallel registry. Local selector contract, orphan gate, shell syntax, and diff checks pass; Kotlin execution remains pending. |

### 2026-08-24T13:52:33Z · Backend batch: mcp-bridge-single-gate

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| R6 / B-MCP-FS execution gate composition | source-wired / partial | `src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt`, `src/test/kotlin/atropos/bridge/BridgeMcpHandlerTest.kt` | `POST /v1/mcp/call` → existing `BridgeMcpHandler` → bound `McpHostManager.callTool` → its single `McpTerritoryBridge` → bounded process/HTTP transport | Removed the duplicate bridge-side admission for execution calls; `/v1/mcp/judge` remains the explicit preflight surface, while `/v1/mcp/call` now crosses the host's canonical gate exactly once. Added a counting-gate test. `git diff --check`, orphan gate, and hosted selector contract pass; Kotlin/hosted execution remains unproven. |

### 2026-08-24T13:55:00Z · Backend batch: residual-status-reconciliation

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-MCP-SENTRY / P07 Bedrock residual truth | source-wired / partial | `STATUS-BACKEND.md` | Existing Sentry command/registry and Bedrock descriptor/adapter callers | Corrected the current residual table so it no longer says the already-created Sentry transport or Bedrock SigV4 transport is absent. Live credentials, hosted Kotlin execution, and paid AWS operation remain explicitly unproven; no completion claim changed. `git diff --check` remains required. |

### 2026-08-24T14:25:00Z · Backend verification: bridge-mcp-focused-root-timeout

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| R6 / B-005 bridge MCP gate | source-wired / partial | `src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt`, `src/test/kotlin/atropos/bridge/BridgeMcpHandlerTest.kt` | `/v1/mcp/call` → `BridgeMcpHandler` → `McpHostManager` | `timeout 90s ./gradlew --no-daemon :test --rerun --max-workers=1 --tests 'atropos.bridge.BridgeMcpHandlerTest'` exited 124 during Gradle task-graph setup with no test output/count. No local pass or root-green claim; GitHub selector remains wired. |

### 2026-08-24T14:45:00Z · Backend batch: mcp-preflight-operation-parity

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| M13 / ADD-MCP-009 bridge preflight parity | source-wired / partial | `src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt`, `src/main/kotlin/atropos/bridge/BridgeRoutes.kt`, `src/test/kotlin/atropos/bridge/BridgeMcpHandlerTest.kt` | `POST /v1/mcp/judge` → `BridgeMcpHandler.judge` → existing `McpTerritoryBridge` | Default bridge preflight now admits the same bounded `convert_to_markdown` operation already used by `McpHostManager`; focused test added. Static checks pass; root/hosted Kotlin and configured MarkItDown runtime evidence remain pending. |

### 2026-08-24T15:30:00Z · Backend verification: fast-source-compile-timeout

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| hosted/source compile lane | inconclusive / partial | `scripts/atropos-fast-gate.sh` | GitHub compile workflow and reusable verifier → existing source manifest/Gradle compile | `timeout 120s scripts/atropos-fast-gate.sh classes` exited 124 with no compiler output on Termux. This is not a pass; GitHub Actions remains authoritative. |

### 2026-08-24T16:00:00Z · Backend verification: narrow-bridge-classpath-inconclusive

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| bridge production compile | inconclusive / partial | `src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt`, `BridgeRoutes.kt` | local `kotlinc` diagnostic attempt | A narrow compile against the assumed `build/classes/kotlin/main` path failed because that production classpath does not exist in this checkout; errors were unresolved dependencies, not an isolated source verdict. No compile pass/fail claim; hosted Gradle remains authoritative. |

### 2026-08-24T16:30:00Z · Backend verification: stale-jar-compile-inconclusive

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| bridge production compile | inconclusive / partial | `build/libs/ATROPOS.jar`, changed bridge sources | narrow `kotlinc` diagnostic attempt | The prior JAR lacks newer MCP/bridge symbols and cannot serve as a valid current module classpath; the attempt reported unresolved/stale internal symbols. No source verdict is claimed. Full hosted Gradle remains authoritative. |

### 2026-08-24T21:10:00Z · Backend batch: provider-connect-no-echo-fallback

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| B-PROV connect local-only secret entry | source-wired / partial | `src/main/kotlin/atropos/cli/ProviderCommandHandler.kt`, `scripts/provider-connect-contract-test.sh`, `.github/workflows/compile-gate.yml`, `scripts/atropos-verify-worktree.sh` | `/providers connect <provider>` → existing `ProviderCommandHandler` → `TerminalModeManager` echo suppression → `ProviderOnboardingService.connectToVault` → `TokenIsolationVault` | Removed the unsafe console-less `readLine()` fallback. The existing `TerminalModeManager` now owns temporary no-echo input and is restored in `finally`; unavailable TTY returns cancellation without reading an echoed secret. `ATROPOS_PROVIDER_CONNECT_CONTRACT_OK`, selector contract (382 tests), shell syntax, diff check, and orphan gate pass locally. Hosted Kotlin execution remains pending; no provider-connect test-green claim. |

### 2026-08-24T21:35:00Z · Backend batch: mcp-health-execution-gate

| atom | status | files | caller | tests / notes |
| --- | --- | --- | --- | --- |
| ADD-MCP-001 / B-MCP-CORE health exclusion at call time | source-wired / partial | `src/main/kotlin/atropos/core/integration/McpHostManager.kt`, `src/test/kotlin/atropos/core/integration/McpHostManagerTest.kt` | CLI/bridge → existing `McpHostManager.callTool` → `statuses()` probe/persistence → bounded transport | Direct tool calls now consult the canonical health probe after allowlist/policy checks and refuse `UNHEALTHY` servers before process or remote transport starts. Added a fixture proving no unhealthy stdio process starts and health.tsv records the refusal state. Selector parity, diff check, and orphan gate pass locally; hosted/root Kotlin execution remains pending. |
