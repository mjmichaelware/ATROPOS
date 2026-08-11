# ATROPOS Source Docs 1–2 Completion Ledger

Created: 2026-07-20
Audit scope: All 94 DAG atoms + all Kotlin source files

## Overall Status

| Metric | Count |
|--------|-------|
| Total DAG atoms | 94 |
| DONE (fully implemented, wired, tested) | 0 |
| PARTIAL (some impl, weak/partial wiring) | 55 |
| STUB (boilerplate/artificial only) | 10 |
| MISSING (no implementation) | 3 |
| VERIFY (likely present, needs proof) | 12 |
| MUST_AUDIT (O-category structural items) | 10 |
| REMOVE (dead/artificial) | 4 |
| PASSING compile | Yes |
| Tests present | 13 test files |
| Main source files | ~140 Kotlin files |

---

## A_SOURCE_AUTHORITY_DLOI

### A001 SourceDocumentRegistry
- **Status:** PARTIAL
- **Matching files:** `DloiService.kt` (428 lines)
- **What exists:** DloiSection, DloiDocument, DloiCoordinate, DloiResolution data types; can parse documents into sections
- **What's missing:** No persistent SourceDocumentRegistry with hashes; DloiService has load/parse but no manifest/hash verification; needs SourceDoc 1 and 2 explicit registration
- **Tests:** `DloiServiceTest.kt` exists (test)
- **Blocker:** No source doc hash registry; sections parsed but not cryptographically verified
- **Smallest next pass:** Add SourceDocumentRegistry with SHA-256 hashes and source ID registration

### A002 SourceSectionAddress
- **Status:** PARTIAL
- **Matching files:** `DloiService.kt` - DloiCoordinate has documentId, sourceId, sectionId, lineStart, lineEnd
- **What exists:** Typed section addresses for basic document sections
- **What's missing:** No coverage check for .001 .005 .100 .500 1.0.0-1.0.5 and .200-.325 ranges; partial coverage
- **Tests:** Partial
- **Blocker:** Section coverage incomplete
- **Smallest next pass:** Verify all required section ranges are addressable

### A003 DloiRouter
- **Status:** PARTIAL
- **Matching files:** `DloiService.kt`, `OntologicalAddressRouter.kt` (29 lines)
- **What exists:** DloiService has resolve() method; OntologicalAddressRouter has basic coordinate parsing
- **What's missing:** OntologicalAddressRouter is minimal (29 lines); no full domain/category/leaf routing
- **Tests:** Partial
- **Blocker:** OntologicalAddressRouter incomplete
- **Smallest next pass:** Expand OntologicalAddressRouter with proper domain routing

### A004 HIGZeroGuard
- **Status:** MISSING/PARTIAL
- **Matching files:** None directly
- **What exists:** Deterministic verifier checks for shell safety
- **What's missing:** No explicit HIG=0 guard that prevents blind cosine/RAG fallback when exact address required; no no-match typed response
- **Tests:** None
- **Blocker:** No explicit guard
- **Smallest next pass:** Add HIGZeroGuard that returns typed NoMatch when exact address fails

### A005 SourceDocToCodeTrace
- **Status:** MISSING
- **Matching files:** None
- **What exists:** Nothing systematic
- **What's missing:** No feature atom traces back to source section; completion ledger (this file) is the first attempt
- **Tests:** None
- **Blocker:** No traceability mechanism
- **Smallest next pass:** Add source-section annotation to feature implementations

---

## B_AST_SYMBOL_GRAPH

### B001 AstSymbolGraph
- **Status:** PARTIAL/VERIFY
- **Matching files:** `ast/AstSymbolGraph.kt` (248 lines)
- **What exists:** AstSymbol, AstSymbolKind, AstImportResolution, AstImportReconciliationResult types; full symbol extraction
- **What's missing:** Relies on regex-based extraction not full tree-sitter AST; works for basic cases
- **Tests:** `AstSymbolGraphTest.kt` exists
- **Blocker:** Regex-based, not full AST
- **Smallest next pass:** Add more symbol types to extraction

### B002 TreeSitterGrammarBridge
- **Status:** STUB/PARTIAL
- **Matching files:** `core/parser/TreeSitterGrammarBridge.kt` (2 lines)
- **What exists:** Empty stub: `class TreeSitterGrammarBridge { fun parseTree(code: String) = "{}" }`
- **What's missing:** Real AST extraction; native tree-sitter binding
- **Tests:** None
- **Blocker:** Complete stub; not functional
- **Smallest next pass:** Implement basic Kotlin parser or remove as non-required

### B003 AstNamespaceReconciler
- **Status:** MISSING/PARTIAL
- **Matching files:** `AstSymbolGraph.kt` has AstImportReconciliationResult but no full reconciler
- **What exists:** Import resolution types and basic duplicate detection in DeterministicVerifier
- **What's missing:** Full namespace reconciler that resolves imports deterministically from symbol graph
- **Tests:** None
- **Blocker:** No reconciler implementation
- **Smallest next pass:** Add AstNamespaceReconciler class

### B004 SymbolCensusCommand
- **Status:** PARTIAL/VERIFY
- **Matching files:** `AstSymbolGraph.kt` has extraction logic; DeterministicVerifier uses it
- **What exists:** Can extract symbols from files
- **What's missing:** No dedicated /symbol-census command that produces flat symbol report
- **Tests:** None specific to census
- **Blocker:** No dedicated command
- **Smallest next pass:** Add /symbol-census slash command

---

## C_LOCAL_TERMUX_RUNTIME

### C001 KotlinJvmRuntime
- **Status:** VERIFY
- **Matching files:** `build.gradle.kts`, all source files
- **What exists:** Full Kotlin/JVM project; compile succeeds
- **What's missing:** Need to verify recent compile; test runner may need configuration
- **Tests:** Build config exists
- **Blocker:** None immediate
- **Smallest next pass:** Verify compile succeeds with `./gradlew compileKotlin`

### C002 LocalToolchainProvider
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/provider/LocalRoot.kt` - LocalToolchainProbe, LocalStateStore
- **What exists:** LocalToolchainProbe probes kotlinc, git, workspace; LocalStateStore persists events
- **What's missing:** No full "provider" adapter for local toolchain; LocalRoot is a probe not a provider
- **Tests:** None
- **Blocker:** Local toolchain not wrapped as provider
- **Smallest next pass:** Wrap LocalToolchainProbe as a ProviderAdapter

### C003 KotlinCompileProbe
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/knowledge/SelfImprovingCompilationLoop.kt` has JdkVerificationProcessExecutor
- **What exists:** JdkVerificationProcessExecutor runs compile commands and captures output
- **What's missing:** Not wrapped as standalone probe with typed output
- **Tests:** Partial via DeterministicVerifierTest
- **Blocker:** No standalone probe
- **Smallest next pass:** Create standalone KotlinCompileProbe

### C004 GitStateProbe
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/provider/LocalRoot.kt` - LocalToolchainProbe.probeGit()
- **What exists:** git status --short probe
- **What's missing:** No git diff, recent commits, or deeper analysis
- **Tests:** None
- **Blocker:** Limited to status only
- **Smallest next pass:** Add git diff and log to probe

### C005 TermuxPathResolver
- **Status:** PARTIAL
- **Matching files:** `cli/config/ConfigurationManager.kt`, shell commands
- **What exists:** ShellCommandRunner handles cwd; ConfigurationManager has workspace detection
- **What's missing:** No centralized TermuxPathResolver for workspace root, Downloads export, etc.
- **Tests:** None
- **Blocker:** No centralized resolver
- **Smallest next pass:** Create TermuxPathResolver

---

## D_VERIFICATION_EDELTA_ZERO

### D001 DeterministicVerifier
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/verification/DeterministicVerifier.kt` (318 lines)
- **What exists:** Full verifier with invariants: package_path_invariant, duplicate_imports, import_reconciliation, shell_safety
- **What's missing:** More invariants; E(DELTA)=0 enforcement
- **Tests:** `DeterministicVerifierTest.kt` (73 lines)
- **Blocker:** E(DELTA)=0 not enforced
- **Smallest next pass:** Add E(DELTA)=0 invariant check

### D002 ConstraintSolverEvaluator
- **Status:** STUB/PARTIAL
- **Matching files:** `core/verifier/ConstraintSolverEvaluator.kt` (2 lines)
- **What exists:** `class ConstraintSolverEvaluator { fun evaluate() = true }` - always returns true
- **What's missing:** Real static syntax/boundary evaluation
- **Tests:** None
- **Blocker:** Complete stub
- **Smallest next pass:** Implement real constraint evaluation

### D003 ProbabilisticImmunityEngine
- **Status:** PARTIAL
- **Matching files:** `core/verifier/ProbabilisticImmunityEngine.kt` (125 lines), `tests/core/ImmunityEngineTest.kt` (in src/main)
- **What exists:** Parse stderr lines into CompilerDiagnostic; capture stdout/stderr
- **What's missing:** Full anomaly scoring; no tests in test tree (ImmunityEngineTest.kt is in main tree)
- **Tests:** `ImmunityEngineTest.kt` exists but in src/main (wrong location)
- **Blocker:** Test in wrong location; limited anomaly analysis
- **Smallest next pass:** Move ImmunityEngineTest to src/test and add more tests

### D004 NoFakeSuccessGate
- **Status:** PARTIAL/VERIFY
- **Matching files:** VerificationResult.successful computed from execution state
- **What exists:** VerificationResult never returns success if compile failed
- **What's missing:** Explicit gate/fixture that proves failure never prints success
- **Tests:** None specific
- **Blocker:** Missing explicit test
- **Smallest next pass:** Add NoFakeSuccessGate test fixture

### D005 SafeJarSwapGate
- **Status:** PARTIAL/VERIFY
- **Matching files:** `scripts/atropos-safe-jar-swap.sh`
- **What exists:** Shell script for safe jar swap
- **What's missing:** Not wired into Kotlin code; no Java/Kotlin equivalent gate
- **Tests:** None
- **Blocker:** Not in main codebase
- **Smallest next pass:** Add SafeJarSwapGate as Kotlin class

---

## E_DOPAMINE_REWARD_LOOP

### E001 SelfImprovingCompilationLoop
- **Status:** STUB/PARTIAL
- **Matching files:** `core/knowledge/SelfImprovingCompilationLoop.kt` (223 lines)
- **What exists:** Full VerificationRunner implementation with AtomicRewardRecorder
- **What's missing:** Success/failure feedback loop exists but not wired into session; no dopamine-style display
- **Tests:** `AgentSelfBuildLoopTest.kt` exists
- **Blocker:** Loop exists but not surfaced
- **Smallest next pass:** Surface reward history in status

### E002 RewardPenaltyStore
- **Status:** MISSING/PARTIAL
- **Matching files:** AtomicRewardRecorder in SelfImprovingCompilationLoop.kt (persists to rewards.tsv)
- **What exists:** Atomic TSV-based reward persistence
- **What's missing:** Not tested independently; no query interface
- **Tests:** None
- **Blocker:** No independent verification
- **Smallest next pass:** Add RewardPenaltyStore with query support

### E003 ErrorGradientExtractor
- **Status:** PARTIAL
- **Matching files:** `core/verifier/ProbabilisticImmunityEngine.kt` parses stderr
- **What exists:** Parses compiler errors into diagnostics
- **What's missing:** No error-gradient prompt construction; no localized repair context
- **Tests:** None
- **Blocker:** No gradient extraction
- **Smallest next pass:** Add ErrorGradientExtractor

---

## F_LAKEHOUSE_CAS_MEMORY

### F001 OntologicalAddressRouter
- **Status:** PARTIAL/VERIFY
- **Matching files:** `data/lakehouse/OntologicalAddressRouter.kt` (29 lines)
- **What exists:** DloiCoordinate data class; resolve/format methods
- **What's missing:** No full routing; no mapping to documents/sections
- **Tests:** `OntologicalIndexTest.kt` exists (in src/main)
- **Blocker:** Minimal implementation
- **Smallest next pass:** Add DLOI document routing to OntologicalAddressRouter

### F002 CloudLakehouseSyncEngine
- **Status:** STUB/PARTIAL
- **Matching files:** `data/storage/CloudLakehouseSyncEngine.kt` (72 lines)
- **What exists:** Content-addressable storage (CAS) with SHA-256 hashing, atomic moves
- **What's missing:** No cloud sync; no delta replication; local-only CAS
- **Tests:** None
- **Blocker:** No cloud integration
- **Smallest next pass:** Add cloud sync interface (defer actual impl if SD3)

### F003 LocalMemoryStore
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/memory/LocalMemoryStore.kt` (480 lines)
- **What exists:** Full LocalMemoryStore with MemoryRecord, MemoryKind, search, persist
- **What's missing:** No SQLite backend; uses file-based JSONL
- **Tests:** `LocalMemoryStoreTest.kt` exists
- **Blocker:** No SQLite
- **Smallest next pass:** Add SQLite backend option

### F004 SqliteVecIntegration
- **Status:** MISSING/PARTIAL
- **Matching files:** None
- **What exists:** Nothing
- **What's missing:** sqlite-vec vector memory integration
- **Tests:** None
- **Blocker:** Not implemented
- **Smallest next pass:** Add SqliteVecIntegration (if required by SD1; otherwise defer)

### F005 Chunking1024Overlap
- **Status:** MISSING/PARTIAL
- **Matching files:** None
- **What exists:** Nothing specific
- **What's missing:** Source doc chunking into 1024-token windows with overlap
- **Tests:** None
- **Blocker:** Not implemented
- **Smallest next pass:** Add Chunking1024Overlap

### F006 DriveExportUpload
- **Status:** PARTIAL external / VERIFY integrated
- **Matching files:** `stream_to_drive.py`, `gdrive_upload.py`, `stream_to_lakehouse.py`
- **What exists:** Python scripts for Drive upload
- **What's missing:** Kotlin integration; secrets exclusion
- **Tests:** None in Kotlin
- **Blocker:** No Kotlin integration
- **Smallest next pass:** Add DriveExportUpload Kotlin class

---

## G_PROVIDER_GRID

### G001 ApiCapability
- **Status:** VERIFY
- **Matching files:** `core/provider/ProviderDescriptor.kt` - ApiCapability enum
- **What exists:** All 16 capabilities: CHAT, CODE, REPAIR, PLAN, LARGE_CONTEXT, VISION, EMBED, ASSET, WEB, READER, VECTOR_DB, DATABASE, STORAGE, EDGE, SECRET, CI, LOCAL_TOOL
- **Tests:** Implicitly tested
- **Blocker:** None
- **Smallest next pass:** None needed

### G002 ProviderDescriptor
- **Status:** VERIFY/PARTIAL
- **Matching files:** `core/provider/ProviderDescriptor.kt` (27 lines), `StaticProviderDescriptorRegistry.kt` (45 lines)
- **What exists:** ProviderDescriptor with id, displayName, costMode, quotaTier, capabilities, requiredEnv, fallbackChain, endpointId, isLocal, notes
- **What's missing:** 30 providers registered (via StaticProviderDescriptorRegistry); meets SD2 .300 requirement
- **Tests:** Implicitly tested
- **Blocker:** None
- **Smallest next pass:** None needed

### G003 ProviderResult
- **Status:** VERIFY/PARTIAL
- **Matching files:** `core/provider/ProviderCallResult.kt` (36 lines)
- **What exists:** ProviderCallResult sealed class with Success, Failure, Queued, LocalOnly; ProviderUsage data class
- **What's missing:** Normalized envelope tested
- **Tests:** Implicitly tested
- **Blocker:** None
- **Smallest next pass:** Verify test coverage

### G004 ProviderError
- **Status:** VERIFY/PARTIAL
- **Matching files:** `core/provider/ProviderFailure.kt` (64 lines) - NormalizedProviderFailureType with AUTH_FAILED, RATE_LIMITED, QUOTA_EXHAUSTED, BILLING_REQUIRED, MODEL_MISSING, TIMEOUT, UNAVAILABLE, MALFORMED_RESPONSE, EMPTY_RESPONSE, CANCELLED, INTERNAL
- **What exists:** ProviderErrorNormalizer maps raw strings to typed failures
- **What's missing:** Some error types may lack test coverage
- **Tests:** Implicitly tested
- **Blocker:** None
- **Smallest next pass:** Add dedicated error normalization tests

### G005 ProviderRegistry30
- **Status:** PARTIAL
- **Matching files:** `StaticProviderDescriptorRegistry.kt` - 30 providers registered
- **What exists:** 30 provider slots filled: local, ollama, groq, gemini, github_models, openrouter, cloudflare_ai, cloudflare_workers, google_drive, jina, cerebras, deepinfra, huggingface, nvidia, sambanova, siliconflow, supabase, pinecone, github_actions, google_cloud_free, deepseek_direct, cohere, mistral, anthropic, openai, xai, fal, replicate, serpapi
- **What's missing:** No CUSTOM_USER_API slot; 30 slots present
- **Tests:** Implicitly tested
- **Blocker:** 29 providers in registry (need to recount)
- **Smallest next pass:** Verify 30-register count

### G006 ProviderFixtureMatrix
- **Status:** PARTIAL/MISSING
- **Matching files:** `core/provider/ProviderFixtureMatrixService.kt` (124 lines)
- **What exists:** Offline fixture matrix for all providers; tests success/error/malformed/empty/timeout/redaction fixtures
- **What's missing:** Not every provider has full fixture coverage; some fixture types missing
- **Tests:** `ProviderFixtureMatrixServiceTest.kt` exists
- **Blocker:** Fixture coverage incomplete
- **Smallest next pass:** Add missing fixture types

### G007 ProviderLiveOptIn
- **Status:** VERIFY/PARTIAL
- **Matching files:** `core/provider/ProviderActivationService.kt` - liveTest() method
- **What exists:** Live tests run only when explicitly requested; paid-locked providers refuse without unlock
- **What's missing:** No explicit ATROPOS_LIVE_PROVIDER_TESTS=1 guard
- **Tests:** `ProviderActivationServiceTest.kt` tests live test refusal
- **Blocker:** Missing env var guard
- **Smallest next pass:** Add ATROPOS_LIVE_PROVIDER_TESTS guard

---

## H_QUOTA_ROUTE_POLICY

### H001 QuotaLedger
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/provider/QuotaLedger.kt` (179 lines)
- **What exists:** InMemoryQuotaLedger, FileQuotaLedger, QuotaLedgerBackup; tracks state, cooldown, reset, usage, last error, paid lock
- **What's missing:** No quota tier enforcement
- **Tests:** `QuotaLedgerRouteTruthTest.kt` tests persist and route fallback
- **Blocker:** No tier enforcement
- **Smallest next pass:** Add quota tier enforcement

### H002 ProviderUsageEvent
- **Status:** PARTIAL/VERIFY
- **Matching files:** QuotaLedger.recordSuccess/recordFailure; LocalStateStore.appendEvent
- **What exists:** Every provider call records usage via ledger; events stored in JSONL
- **What's missing:** No typed ProviderUsageEvent data class
- **Tests:** Implicit
- **Blocker:** No typed event
- **Smallest next pass:** Add ProviderUsageEvent type

### H003 RoutePolicy
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/provider/RoutePolicy.kt` (106 lines)
- **What exists:** RoutePolicy.decide() selects by capability, cost mode, quota, cooldown, success score, latency
- **What's missing:** Task priority map may be incomplete
- **Tests:** Implicit via QuotaLedgerRouteTruthTest
- **Blocker:** Priority hardcoded
- **Smallest next pass:** Externalize priority configuration

### H004 FreeModeGuard
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/provider/RoutePolicy.kt` - FreeModeGuard class
- **What exists:** Enforces FREE_ONLY, FREE_AND_CREDIT, LOCAL_ONLY, PAID_EMERGENCY_UNLOCKED policies
- **What's missing:** Needs explicit test proving paid providers impossible in free_only
- **Tests:** Implicit
- **Blocker:** Missing explicit proof test
- **Smallest next pass:** Add FreeModeGuard proof test

### H005 EmergencyPaidGate
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/paid/EmergencyPaidGate.kt` (128 lines)
- **What exists:** EmergencyPaidGate with unlock/lock/status; time-bounded unlocks with audit
- **What's missing:** Needs explicit expiry/audit tests
- **Tests:** Implicit via ProviderActivationServiceTest
- **Blocker:** Missing dedicated tests
- **Smallest next pass:** Add dedicated EmergencyPaidGate tests

### H006 FallbackChains
- **Status:** PARTIAL
- **Matching files:** `core/provider/RoutePolicy.kt` - FallbackResolver; StaticProviderDescriptorRegistry has fallback chains per provider
- **What exists:** CHAT, CODE, REPAIR chains defined in descriptor fallbacks
- **What's missing:** No CHAIN constants; chains resolved dynamically
- **Tests:** None for chain resolution
- **Blocker:** No chain tests
- **Smallest next pass:** Add FallbackChain tests

### H007 QueueWhenFreeUnavailable
- **Status:** PARTIAL/MISSING
- **Matching files:** RoutePolicyDecision has queued/queueReason; AdapterRouteFacade supports queued
- **What exists:** RoutePolicy sets queued=true when no eligible provider
- **What's missing:** No queue/degrade system that enqueues tasks when all free unavailable
- **Tests:** None
- **Blocker:** Queue/degrade not implemented
- **Smallest next pass:** Implement queue/degrade mechanism

---

## I_STATUS_OBSERVABILITY

### I001 StatusCommand
- **Status:** PARTIAL/VERIFY
- **Matching files:** `cli/CommandRouter.kt` handles /status; various renderers
- **What exists:** /status shows session status
- **What's missing:** No unified /status showing local root, provider health, route, queue, paid locks
- **Tests:** Implicit
- **Blocker:** No unified status
- **Smallest next pass:** Add unified /status command

### I002 StatusQuota
- **Status:** PARTIAL/VERIFY
- **Matching files:** `cli/ui/StatusQuotaRenderer.kt`
- **What exists:** /status quota rendering
- **What's missing:** Wire to QuotaLedger
- **Tests:** Implicit
- **Blocker:** Wire verification
- **Smallest next pass:** Verify /status quota wiring

### I003 StatusRoute
- **Status:** PARTIAL/VERIFY
- **Matching files:** `cli/CommandRouter.kt` handles /route; AdapterRouteFacade.renderRoute
- **What exists:** /route <task> explains selected/skipped providers
- **What's missing:** Unified rendering
- **Tests:** Implicit
- **Blocker:** Wire verification
- **Smallest next pass:** Verify /status route wiring

### I004 StatusFailures
- **Status:** PARTIAL/VERIFY
- **Matching files:** `cli/ui/StatusOpsRenderer.kt` (general ops rendering)
- **What exists:** Provider failures are redacted
- **What's missing:** No dedicated /status failures command
- **Tests:** None
- **Blocker:** Missing command
- **Smallest next pass:** Add /status failures

### I005 TerminalWidthSnapshots
- **Status:** PARTIAL/VERIFY
- **Matching files:** `cli/ui/ViewportLayout.kt`, `cli/ui/TerminalCanvas.kt`, `cli/ui/AnsiTerminalEngine.kt`
- **What exists:** Responsive layout; terminal dimension detection
- **What's missing:** No explicit 40/80/120 width snapshot tests
- **Tests:** `PromptStateTest.kt` exists
- **Blocker:** Missing width tests
- **Smallest next pass:** Add terminal width snapshot tests

---

## J_AGENT_APP_FACTORY

### J001 AgentAsk
- **Status:** PARTIAL/VERIFY
- **Matching files:** `cli/commands/AgentCommand.kt`, `core/agent/AgentService.kt`
- **What exists:** /agent ask handler
- **Tests:** Implicit
- **Blocker:** Wire verification
- **Smallest next pass:** Verify /agent ask works

### J002 AgentPatch
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/agent/AgentPatchExtractor.kt`, `core/agent/AgentPatchStore.kt`
- **What exists:** Unified diff extraction
- **Tests:** Implicit
- **Blocker:** Wire verification
- **Smallest next pass:** Verify /agent patch works

### J003 AgentApplyCheck
- **Status:** PARTIAL/VERIFY
- **Matching files:** AgentCommand handles apply --check
- **What exists:** --check flag handling
- **Tests:** Implicit
- **Blocker:** Wire verification
- **Smallest next pass:** Verify /agent apply --check

### J004 AgentApplyLatest
- **Status:** PARTIAL/VERIFY
- **Matching files:** AgentCommand handles apply latest
- **What exists:** Patch apply via AgentCommand
- **Tests:** Implicit
- **Blocker:** Wire verification
- **Smallest next pass:** Verify /agent apply latest

### J005 AgentQueue
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/agent/AgentQueueService.kt`, `core/agent/AgentQueueStore.kt`, `core/agent/AgentQueueModels.kt`, `core/agent/AgentQueueRecovery.kt`, `core/agent/AgentQueueDoctor.kt`
- **What exists:** Full durable agent queue with recovery
- **Tests:** AgentSelfBuildLoopTest covers queue
- **Blocker:** Queue recovery tested
- **Smallest next pass:** Verify queue survival across restarts

### J006 AgentDaemon
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/agent/AgentDaemonService.kt`, `core/agent/AgentDaemonDoctor.kt`, `core/agent/AgentDaemonStore.kt`, `core/agent/AgentDaemonModels.kt`
- **What exists:** Foreground/background daemon, status, doctor
- **Tests:** Implicit
- **Blocker:** Wire verification
- **Smallest next pass:** Verify daemon commands

### J007 AppFactoryRouter
- **Status:** PARTIAL
- **Matching files:** `core/factory/AppFactoryRouter.kt` (141 lines)
- **What exists:** FactoryPlan, FactoryStep, AppFactoryRouter with plan/runLocal/render
- **What's missing:** No full prompt->plan->code->validate->repair->package pipeline; local-only
- **Tests:** None
- **Blocker:** No full pipeline
- **Smallest next pass:** Add code generation step to factory

### J008 EndpointRegistry
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/endpoint/OperationRegistry.kt` (interface), `core/endpoint/StaticOperationRegistry.kt` (32 lines)
- **What exists:** StaticOperationRegistry lists endpoints
- **What's missing:** No stable IDs; entries hardcoded
- **Tests:** None
- **Blocker:** No stable IDs
- **Smallest next pass:** Add stable endpoint IDs

### J009 EndpointManifest
- **Status:** PARTIAL/MISSING
- **Matching files:** `core/endpoint/OperationEndpoint.kt` (9 lines)
- **What exists:** OperationEndpoint data class with id, kind, description, configured, available
- **What's missing:** No manifest fields: owner, input, output, errors, auth, side effects, timeout, retry, tests
- **Tests:** None
- **Blocker:** Incomplete manifest
- **Smallest next pass:** Extend EndpointManifest

### J010 DirectorOrchestrator
- **Status:** STUB/PARTIAL
- **Matching files:** `core/swarm/DirectorOrchestrator.kt` (33 lines)
- **What exists:** Blueprint execution graph via AI provider; parsePaths helper
- **What's missing:** True DAG supervisor; relies on AI provider
- **Tests:** None
- **Blocker:** Stub implementation
- **Smallest next pass:** Implement deterministic DAG supervisor

### J011 WorkerCodeSynthesizer
- **Status:** STUB/PARTIAL
- **Matching files:** `core/swarm/WorkerCodeSynthesizer.kt` (71 lines)
- **What exists:** Concurrent code synthesis via AI provider; sanitizeModelOutput
- **What's missing:** Fan-out code worker with real verification
- **Tests:** None
- **Blocker:** Stub implementation
- **Smallest next pass:** Add worker verification

---

## K_SHELL_CLI_TUI

### K001 CommandRegistry
- **Status:** PARTIAL/VERIFY
- **Matching files:** `cli/input/CommandRegistry.kt` (142 lines)
- **What exists:** 86+ advertised commands with descriptions
- **What's missing:** Every advertised command needs handler verification
- **Tests:** Implicit
- **Blocker:** Handler verification
- **Smallest next pass:** Verify all advertised commands have handlers

### K002 HelpFromRegistry
- **Status:** PARTIAL/VERIFY
- **Matching files:** CommandRegistry.helpLines() generates help
- **What exists:** /help generated from CommandRegistry entries
- **What's missing:** Review output quality
- **Tests:** Implicit
- **Blocker:** None
- **Smallest next pass:** Verify /help output

### K003 SlashCommandIntegrity
- **Status:** PARTIAL
- **Matching files:** `cli/CommandRouter.kt` (673 lines) handles slash commands
- **What exists:** CommandRouter dispatches most commands
- **What's missing:** Some advertised commands may lack handlers; router is monolithic
- **Tests:** `CommandRouterTest.kt` in src/main is EMPTY
- **Blocker:** No test coverage; empty test file
- **Smallest next pass:** Remove empty test file or implement tests

### K004 ShellBridge
- **Status:** PARTIAL/VERIFY
- **Matching files:** `cli/shell/ShellCommandRunner.kt`
- **What exists:** !cmd, /shell, /pwd, /cd, /ls, /git status
- **Tests:** Implicit
- **Blocker:** Wire verification
- **Smallest next pass:** Test shell bridge commands

### K005 ShellAllowlistTimeoutCwdRedaction
- **Status:** PARTIAL/VERIFY
- **Matching files:** ExecutionPolicyEngine controls shell; RedactionFilter redacts output
- **What exists:** Shell execution bounded by policy; redacted output
- **What's missing:** Explicit allowlist
- **Tests:** DeterministicVerifierTest has shell safety test
- **Blocker:** No explicit allowlist
- **Smallest next pass:** Add shell allowlist

### K006 RawKeyReader
- **Status:** PARTIAL/VERIFY
- **Matching files:** `cli/input/RawKeyReader.kt`
- **What exists:** Raw terminal key reading for arrow navigation, tab completion
- **Tests:** Implicit
- **Blocker:** None
- **Smallest next pass:** Verify key handling

### K007 CommandHistory
- **Status:** PARTIAL/VERIFY
- **Matching files:** PrompState handles history; RedactionFilter redacts prior to storage
- **What exists:** In-memory history; secrets redacted before display
- **What's missing:** Persistent history; search
- **Tests:** PromptStateTest.kt tests history
- **Blocker:** No persistent history
- **Smallest next pass:** Add persistent history

### K008 DashboardTabsScreens
- **Status:** PARTIAL/VERIFY
- **Matching files:** `cli/session/SessionTabs.kt`, CLI UI renderers
- **What exists:** Dashboard, Chat, Providers, Factory, Logs screens with tabs
- **What's missing:** Persistent tab state across sessions
- **Tests:** Implicit
- **Blocker:** No persistence
- **Smallest next pass:** Add tab persistence

### K009 ResponsiveTermuxDashboard
- **Status:** PARTIAL
- **Matching files:** `cli/ui/ViewportLayout.kt`, `TerminalCanvas.kt`, etc.
- **What exists:** Responsive layout; live terminal dimensions
- **What's missing:** Pinch zoom reflow; no dead filler
- **Tests:** None
- **Blocker:** Responsive quality
- **Smallest next pass:** Test responsive behavior

---

## L_SECURITY_REDACTION

### L001 TokenIsolationVault
- **Status:** STUB/ARTIFICIAL
- **Matching files:** `core/security/TokenIsolationVault.kt` (2 lines)
- **What exists:** `class TokenIsolationVault { fun isolate() = true }` - pure stub
- **What's missing:** Real vault with local env/secret isolation
- **Tests:** None
- **Blocker:** Complete stub
- **Smallest next pass:** Implement real TokenIsolationVault or remove

### L002 SecretSource
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/security/SecretSource.kt` (144 lines)
- **What exists:** SecretSource interface with MapSecretSource, EnvSecretSource, LocalFileSecretSource, CompositeSecretSource; DefaultSecretSource factory
- **What's missing:** Provider code bypasses SecretSource in some places (Config.kt reads env directly)
- **Tests:** Implicit via KeyDoctorServiceTest
- **Blocker:** Config.kt bypasses SecretSource
- **Smallest next pass:** Fix Config.kt to use SecretSource

### L003 KeyDoctor
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/security/KeyDoctorService.kt` (90 lines)
- **What exists:** /keys doctor renders precedence, configured sources, missing keys; no raw secrets printed
- **Tests:** `KeyDoctorServiceTest.kt` (46 lines)
- **Blocker:** Needs wiring verification
- **Smallest next pass:** Verify /keys doctor output

### L004 RedactionFilter
- **Status:** PARTIAL/VERIFY
- **Matching files:** `core/security/RedactionFilter.kt` (83 lines)
- **What exists:** RedactionFilter with regex patterns for API keys, bearer tokens, private keys, signed URLs, credential paths; RedactionReport with findings
- **What's missing:** Some surfaces may not use filter (Config.kt debugDump prints status with key presence indicators)
- **Tests:** `RedactionFilterTest.kt` (51 lines)
- **Blocker:** Config.kt may leak key presence
- **Smallest next pass:** Audit all output surfaces for redaction

### L005 SecretDiffCheck
- **Status:** PARTIAL/VERIFY
- **Matching files:** CredentialDiffGuard in RedactionFilter.kt
- **What exists:** CredentialDiffGuard inspects paths and text for secrets
- **What's missing:** Not wired into git pre-commit or verify flow
- **Tests:** Implicit
- **Blocker:** Not wired
- **Smallest next pass:** Wire SecretDiffCheck into verification

---

## M_BUILD_CI_PACKAGING

### M001 GradleWrapperPinned
- **Status:** VERIFY
- **Matching files:** `gradlew`, `gradlew.bat`, `gradle/wrapper/`
- **What exists:** Gradle wrapper present
- **Tests:** Build system works
- **Blocker:** None
- **Smallest next pass:** Verify wrapper version matches build.gradle.kts

### M002 GradlePropertiesControlPlane
- **Status:** PARTIAL/VERIFY
- **Matching files:** No explicit gradle.properties found (check .gradle/)
- **What exists:** Build flags in build.gradle.kts
- **What's missing:** gradle.properties may not have daemon heap/metaspace config
- **Tests:** None
- **Blocker:** Missing gradle.properties
- **Smallest next pass:** Create gradle.properties

### M003 KotlinCompatScan
- **Status:** PARTIAL/MISSING
- **Matching files:** None
- **What exists:** Nothing systematic
- **What's missing:** No scanner for risky APIs or unsupported deps
- **Tests:** None
- **Blocker:** Not implemented
- **Smallest next pass:** Add KotlinCompatScan

### M004 GithubActionsCleanRunner
- **Status:** PARTIAL/VERIFY
- **Matching files:** `.github/` directory exists
- **What exists:** GitHub Actions CI config
- **What's missing:** Need to verify workflow files
- **Tests:** None
- **Blocker:** Not verified
- **Smallest next pass:** Verify CI workflow

### M005 SafeJarPackaging
- **Status:** PARTIAL/VERIFY
- **Matching files:** `scripts/atropos-safe-jar-swap.sh`
- **What exists:** Jar build/swap script
- **What's missing:** Not wired into Kotlin gates
- **Tests:** None
- **Blocker:** Not wired
- **Smallest next pass:** Wire SafeJarPackaging into verify flow

### M006 DockerNativeDesktopAndroidWebPlan
- **Status:** MISSING/PARTIAL
- **Matching files:** None
- **What exists:** Nothing
- **What's missing:** Multi-platform migration plan
- **Tests:** None
- **Blocker:** Not implemented
- **Smallest next pass:** Create migration plan document (deferred if SD3)

---

## N_TEST_ACCEPTANCE_MATRIX

### N001 ProviderTests
- **Status:** PARTIAL
- **Matching files:** `QuotaLedgerRouteTruthTest.kt`, `ProviderActivationServiceTest.kt`, `ProviderFixtureMatrixServiceTest.kt`
- **What exists:** Tests for descriptors, routing, cooldown, paid locks, redaction, dry-run, fixtures
- **What's missing:** Not all provider behaviors have tests
- **Blocker:** Coverage gaps
- **Smallest next pass:** Add missing provider behavior tests

### N002 TerminalTests
- **Status:** PARTIAL
- **Matching files:** `PromptStateTest.kt`
- **What exists:** Basic terminal test
- **What's missing:** No 40/80/120, NO_COLOR, TERM=dumb, headless tests
- **Tests:** PromptStateTest only
- **Blocker:** Coverage gaps
- **Smallest next pass:** Add terminal width/environment tests

### N003 SourceAuthorityTests
- **Status:** MISSING/PARTIAL
- **Matching files:** `DloiServiceTest.kt`
- **What exists:** Basic DLOI test
- **What's missing:** Source doc registration and hash/section verification tests
- **Tests:** DloiServiceTest only
- **Blocker:** No source authority tests
- **Smallest next pass:** Add SourceDocumentRegistry tests

### N004 EndpointParityTests
- **Status:** PARTIAL/MISSING
- **Matching files:** None specifically
- **What exists:** Nothing
- **What's missing:** Contract, implementation, CLI/UI exposure, auth, errors, tests, docs parity
- **Tests:** None
- **Blocker:** Not implemented
- **Smallest next pass:** Add endpoint parity tests

### N005 FinalAcceptanceCommand
- **Status:** MISSING/PARTIAL
- **Matching files:** None
- **What exists:** Nothing
- **What's missing:** One final report command for Source Docs 1–2 acceptance
- **Tests:** None
- **Blocker:** Not implemented
- **Smallest next pass:** Add /acceptance command

---

## O_BOILERPLATE_STUB_DEBT *(MUST_AUDIT)*

### O001 EmptyNearEmptyKotlinFiles
- **Status:** MUST_AUDIT
- **Files identified:**
  - `src/main/kotlin/atropos/core/verifier/ConstraintSolverEvaluator.kt` (2 lines) - STUB
  - `src/main/kotlin/atropos/core/security/TokenIsolationVault.kt` (2 lines) - STUB
  - `src/main/kotlin/atropos/core/parser/TreeSitterGrammarBridge.kt` (2 lines) - STUB
  - `src/main/kotlin/atropos/core/endpoint/OperationEndpoint.kt` (9 lines) - thin
  - `src/main/kotlin/atropos/core/endpoint/OperationRegistry.kt` (7 lines) - interface
  - `src/main/kotlin/atropos/core/provider/ProviderDescriptorRegistry.kt` (9 lines) - interface
  - `src/main/kotlin/atropos/core/provider/ProviderDescriptorReport.kt` (10 lines) - thin
  - `src/main/kotlin/atropos/tests/cli/CommandRouterTest.kt` (0 lines) - EMPTY
  - `src/main/kotlin/atropos/data/lakehouse/OntologicalAddressRouter.kt` (29 lines) - partial
- **Action:** Implement or remove each

### O002 DisconnectedFrontendPackage
- **Status:** MUST_AUDIT
- **Files identified:** CLI UI files (42 files under cli/ui/) are all wired through AnsiTerminalEngine and CommandRouter. No disconnected frontend identified. All UI files appear used.
- **Action:** No action required

### O003 SwarmStubs
- **Status:** MUST_AUDIT
- **Files identified:**
  - `core/swarm/DirectorOrchestrator.kt` (33 lines) - STUB, uses AIProvider for DAG planning
  - `core/swarm/WorkerCodeSynthesizer.kt` (71 lines) - STUB, uses AIProvider for code generation
  - `core/swarm/OllamaClient.kt` - PARTIAL
- **Action:** Implement real DAG supervisor or mark as deferred SD3 feature

### O004 CommandRouterConcernMixing
- **Status:** MUST_AUDIT
- **Files identified:** `cli/CommandRouter.kt` (673 lines) - monolithic: mixes lexing, routing, session, provider dispatch, UI
- **Action:** Split into separate concerns

### O005 ProviderTransportNormalizationMixing
- **Status:** MUST_AUDIT
- **Files identified:** Provider.kt (396 lines) mixes HTTP transport, JSON parsing, and provider implementations in one file
- **Action:** Split transport from response normalizer and fixtures

### O006 ProbabilisticImmunityConcernMixing
- **Status:** MUST_AUDIT
- **Files identified:** `core/verifier/ProbabilisticImmunityEngine.kt` mixes stderr parsing, scoring, and verification
- **Action:** Split into separate concerns

### O007 ManualJsonProviderCode
- **Status:** MUST_AUDIT
- **Files identified:** `core/Provider.kt` has manual JSON building (buildOpenAiCompatiblePayload) and manual JSON parsing (extractOpenAiChatContent, extractAnthropicText, extractQuotedJsonString)
- **Action:** Replace with JSON library or test thoroughly

### O008 FakeCommands
- **Status:** MUST_AUDIT
- **Files identified:** CommandRegistry.kt lists 86+ commands; verify each has handler in CommandRouter
- **Action:** Audit every advertised command for handler

### O009 PlaceholderGreen
- **Status:** MUST_AUDIT
- **Files identified:**
  - `ConstraintSolverEvaluator.evaluate() = true` - always passes
  - `TokenIsolationVault.isolate() = true` - always passes
  - `TreeSitterGrammarBridge.parseTree() = "{}"` - always returns empty
  - `ProviderDescriptorReport.generate()` - thin
- **Action:** Implement real behavior or remove

### O010 MissingTests
- **Status:** MUST_AUDIT
- **Tests found:** 13 test files in src/test, 3 in src/main (misplaced)
- **Tests missing:** Most atoms lack dedicated tests
- **Action:** Add tests for every atom

---

## P_FINAL_ACCEPTANCE

### P001 FinalSD1SD2Acceptance
- **Status:** MISSING
- **Matching files:** None
- **What exists:** Nothing
- **What's missing:** Final acceptance report and command
- **Output required:** `ATROPOS_SD1_SD2_FINAL_ACCEPTANCE_REPORT.md`
- **Blocker:** All other atoms must be DONE or deferred with justification

---

## Kotlin File Classification

### REAL/WIRED (fully functional, wired into runtime)
| File | Lines | Notes |
|------|-------|-------|
| Main.kt | 217 | App entry, interactive/headless run |
| cli/CommandRouter.kt | 673 | Central dispatch, lexing, routing |
| cli/input/CommandRegistry.kt | 142 | 86+ advertised commands |
| cli/input/PromptState.kt | ~200 | Terminal prompt state |
| cli/input/RawKeyReader.kt | ~100 | Raw terminal input |
| cli/input/TerminalModeManager.kt | ~50 | Terminal raw mode |
| cli/session/SessionTabs.kt | ~100 | Tab management |
| core/Provider.kt | 396 | HTTP provider implementations |
| core/ProviderState.kt | 319 | Provider health, cascading, Ollama probe |
| core/provider/QuotaLedger.kt | 179 | In-memory + file quota ledger |
| core/provider/RoutePolicy.kt | 106 | Route decisions, fallback |
| core/provider/ProviderFailure.kt | 64 | Error normalization |
| core/provider/ProviderCallResult.kt | 36 | Result sealed class |
| core/provider/ProviderTask.kt | 38 | Task classification |
| core/provider/ProviderDescriptor.kt | 27 | Descriptor types |
| core/provider/StaticProviderDescriptorRegistry.kt | 45 | 30 providers registered |
| core/provider/ProviderDescriptorValidator.kt | 26 | Validation logic |
| core/provider/ProviderFixtureMatrixService.kt | 124 | Fixture matrix |
| core/provider/ProviderActivationService.kt | 296 | Activation/verification |
| core/provider/ProviderTruthService.kt | 115 | Truth snapshot |
| core/provider/ProviderTruthModels.kt | 54 | Truth record types |
| core/provider/LocalRoot.kt | 43 | Local probes |
| core/provider/adapter/AdapterRouteFacade.kt | 184 | Route facade |
| core/security/SecretSource.kt | 144 | Secret sourcing |
| core/security/RedactionFilter.kt | 83 | Redaction engine |
| core/security/KeyDoctorService.kt | 90 | Key diagnostics |
| core/paid/EmergencyPaidGate.kt | 128 | Paid unlock gate |
| core/knowledge/SelfImprovingCompilationLoop.kt | 223 | Compile loop |
| core/memory/LocalMemoryStore.kt | 480 | Memory store |
| core/factory/AppFactoryRouter.kt | 141 | Factory planning |
| core/verification/DeterministicVerifier.kt | 318 | Verification engine |
| core/verification/VerificationModels.kt | 86 | Verification types |
| core/verifier/ProbabilisticImmunityEngine.kt | 125 | Stderr parsing |
| core/policy/ExecutionPolicyEngine.kt | 218 | Policy engine |
| dloi/DloiService.kt | 428 | Source document service |
| data/storage/CloudLakehouseSyncEngine.kt | 72 | CAS storage |

### PARTIAL (some implementation, incomplete wiring)
| File | Lines | Notes |
|------|-------|-------|
| ast/AstSymbolGraph.kt | 248 | Symbol extraction works, regex-based |
| cli/commands/VerifyCommand.kt | ~100 | Verify command |
| cli/commands/AgentCommand.kt | ~200 | Agent command |
| cli/config/ConfigurationManager.kt | ~100 | Config detection |
| cli/errors/SystemExceptionHandler.kt | ~30 | Error handling |
| cli/input/CommandCompleter.kt | ~100 | Tab completion |
| cli/session/QuotaSessionTracker.kt | ~80 | Quota tracking |
| cli/shell/ShellCommandRunner.kt | ~100 | Shell execution |
| cli/ui/* (most renderers) | varies | UI rendering, mostly functional |
| core/Routing.kt | 100 | Routing engine |
| core/adapter/HardwareProfileAdapter.kt | ~50 | Hardware detection |
| core/agent/* (24 files) | varies | Agent subsystem, most functional |
| core/assets/LocalAssetGenerator.kt | ~100 | Asset generation |
| core/endpoint/EndpointKind.kt | ~20 | Endpoint types |
| core/endpoint/StaticOperationRegistry.kt | 32 | Registry impl |
| core/execution/LocalWorkQueue.kt | ~100 | Work queue |
| core/ops/DeploymentOps.kt | ~50 | Deployment ops |
| core/provider/ProviderActivationModels.kt | 82 | Activation models |
| core/provider/ProviderActivationStore.kt | ~100 | Activation persistence |
| core/provider/ProviderAdapterIntrospection.kt | ~80 | Adapter introspection |
| core/provider/ProviderDescriptorRegistry.kt | 9 | Interface only |
| core/provider/ProviderDescriptorReport.kt | 10 | Thin report |
| core/provider/adapter/AdapterRegistry.kt | ~50 | Adapter registry |
| core/provider/adapter/AdapterRequest.kt | ~30 | Request type |
| core/provider/adapter/ProviderAdapter.kt | 26 | Interface |
| core/provider/adapter/ScaffoldAdapters.kt | ~100 | Adapter scaffold |
| core/swarm/OllamaClient.kt | ~80 | Ollama client |
| core/testing/AtroposTestMatrix.kt | ~80 | Test matrix |
| data/cache/CodebaseDeltaTreeTracker.kt | 274 | Git delta tracking |
| data/indexer/LatentOntologicalIndexer.kt | 66 | Semantic indexing |
| data/lakehouse/OntologicalAddressRouter.kt | 29 | Minimal router |
| src/main/kotlin/atropos/tests/core/ImmunityEngineTest.kt | ~50 | Misplaced test |
| src/main/kotlin/atropos/tests/data/OntologicalIndexTest.kt | ~30 | Misplaced test |

### STUB/ARTIFICIAL (boilerplate, placeholder behavior)
| File | Lines | Notes |
|------|-------|-------|
| core/verifier/ConstraintSolverEvaluator.kt | 2 | `evaluate() = true` |
| core/security/TokenIsolationVault.kt | 2 | `isolate() = true` |
| core/parser/TreeSitterGrammarBridge.kt | 2 | `parseTree() = "{}"` |
| core/endpoint/OperationEndpoint.kt | 9 | Data class only |
| core/endpoint/OperationRegistry.kt | 7 | Interface only |
| core/swarm/DirectorOrchestrator.kt | 33 | AI-dependent DAG planner |
| core/swarm/WorkerCodeSynthesizer.kt | 71 | AI-dependent code synth |
| core/Config.kt | 30 | Still parses JSON manually; may leak key presence |

### DEAD/DISCONNECTED
| File | Lines | Notes |
|------|-------|-------|
| src/main/kotlin/atropos/tests/cli/CommandRouterTest.kt | 0 | EMPTY FILE |

### TEST-ONLY (proper test location)
| File | Lines |
|------|-------|
| src/test/kotlin/atropos/ast/AstSymbolGraphTest.kt | ~80 |
| src/test/kotlin/atropos/cli/input/PromptStateTest.kt | ~80 |
| src/test/kotlin/atropos/core/agent/AgentSecurityRedactionSurfaceTest.kt | ~60 |
| src/test/kotlin/atropos/core/agent/AgentSelfBuildLoopTest.kt | ~100 |
| src/test/kotlin/atropos/core/memory/LocalMemoryStoreTest.kt | ~80 |
| src/test/kotlin/atropos/core/policy/ExecutionPolicyEngineTest.kt | ~60 |
| src/test/kotlin/atropos/core/provider/ProviderActivationServiceTest.kt | 121 |
| src/test/kotlin/atropos/core/provider/ProviderFixtureMatrixServiceTest.kt | 20 |
| src/test/kotlin/atropos/core/provider/QuotaLedgerRouteTruthTest.kt | 68 |
| src/test/kotlin/atropos/core/security/KeyDoctorServiceTest.kt | 46 |
| src/test/kotlin/atropos/core/security/RedactionFilterTest.kt | 51 |
| src/test/kotlin/atropos/core/verification/DeterministicVerifierTest.kt | 73 |
| src/test/kotlin/atropos/dloi/DloiServiceTest.kt | ~80 |

### REMOVE/MOVE/FUTURE
| File | Action |
|------|--------|
| src/main/kotlin/atropos/tests/cli/CommandRouterTest.kt | REMOVE (empty) |
| src/main/kotlin/atropos/tests/core/ImmunityEngineTest.kt | MOVE to src/test |
| src/main/kotlin/atropos/tests/data/OntologicalIndexTest.kt | MOVE to src/test |

---

## Summary of Blocker Atoms for E(DELTA)=0

The top-priority incomplete blocker atoms (highest impact on E(DELTA)=0):

1. **A004 HIGZeroGuard** - No guard against blind RAG fallback when exact address required
2. **A005 SourceDocToCodeTrace** - No traceability from features to source sections
3. **B002 TreeSitterGrammarBridge** - Complete stub (2 lines)
4. **D002 ConstraintSolverEvaluator** - Complete stub (2 lines, always returns true)
5. **D004 NoFakeSuccessGate** - Missing explicit test for no-fake-success
6. **E002 RewardPenaltyStore** - Missing query interface
7. **F004 SqliteVecIntegration** - Not implemented (may be SD3)
8. **F005 Chunking1024Overlap** - Not implemented
9. **G006 ProviderFixtureMatrix** - Incomplete fixture coverage
10. **H007 QueueWhenFreeUnavailable** - Not implemented
11. **L001 TokenIsolationVault** - Complete stub (2 lines)
12. **N005 FinalAcceptanceCommand** - Not implemented
13. **O category** - Structural debt items

## First Required Next Pass

**Smallest safe implementation pass:** 
Implement **A004 HIGZeroGuard** - a simple guard class that prevents blind RAG fallback when exact source address is required, returning a typed NoMatch instead.
