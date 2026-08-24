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
- `/keys` — existing local secret status surface.

## Secret and policy boundary

Provider connect stores secrets through `TokenIsolationVault` under the user-local `~/.atropos/secrets` by default; explicit workspace roots remain supported for isolated callers. `providers.json` contains metadata only. Environment and local vault discovery are the default path. FREE/LOCAL providers may run without paid approval; PAID providers must stop for explicit approval before spend. No provider key is persisted in this status file.

## Remaining blocked or partial work

- FTY-03 has a runtime-owned repair callback; a configured real repair run and hosted test evidence are still pending. Absent that callback or command, failures remain failed.
- Root factory focused tests and full root compilation have not completed within the available Gradle heap/time envelope. `:core:jvmTest` does not contain the root factory test source set.
- Root Gradle compilation remains unsuitable for the local device heap; `.github/workflows/compile-gate.yml` is the authoritative hosted compile and focused-test lane. Live provider/MCP probes, install/doctor, and device proof remain external/runtime gates.
- MCP runtime probe remains untested against a real external deployment; the local bounded stdio fixture and core test target now pass. Health labels persist to `.atropos/mcp/health.tsv`; hosted/root integration proof remains pending.
- Quota status projection is source-wired and locally statically checked; hosted bridge tests still need to prove the complete root compilation and response contract.

## Wave residual audit

| residual | status | evidence / caller | remaining truth |
| --- | --- | --- | --- |
| R1 MCP single owner | partial | `McpHostManager.kt` is the only MCP manager; `/mcp`, bridge, and doctor call it; process starts use `BoundedProcessRunner` | hosted tests and a real allowlisted stdio server remain unrun |
| R2 provider prefer/disable | partial | `ProviderOnboardingService.preferredProviderIds`, `RoutePolicy`, and `ProviderCascadeRouter` consume persisted metadata; `/providers prefer|disable` is registered | hosted route tests remain unrun |
| R3 BlockType caller | source-wired | `LandingRenderer.logo` calls `BlockType` on narrow frames | no root UI test run |
| R4 Sentry loop | blocked | no Sentry client, registry, or production caller exists in the current tree | needs an accepted public API/MCP owner and operator credentials; no fake loop added |
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
| P07 AWS Bedrock | blocked | discovery metadata recognizes `AWS_*`; no Bedrock signing/transport owner exists, so it is excluded from routing rather than misrepresented |
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
