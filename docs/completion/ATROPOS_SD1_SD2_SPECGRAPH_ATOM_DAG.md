# ATROPOS Source Docs 1–2 SpecGraph Atom DAG

## Scope

Source Document 1 and Source Document 2 only.

Do not implement Source Document 3 unless required to satisfy Source Docs 1–2.

## Completion Meaning

An atom is complete only if it is implemented, wired, reachable, tested, verified, documented, non-boilerplate, and source-aligned.

Compile-only does not count.

## Status Values

DONE = implemented, wired, tested, evidenced.
PARTIAL = some implementation exists but incomplete or weakly wired.
STUB = file/class exists but behavior is minimal/artificial.
MISSING = required atom absent.
VERIFY = likely present but needs direct proof.
REMOVE = artificial/dead/boilerplate code should be removed or moved.

## Root DAG

SD1_SD2_COMPLETE
├── A_SOURCE_AUTHORITY_DLOI
├── B_AST_SYMBOL_GRAPH
├── C_LOCAL_TERMUX_RUNTIME
├── D_VERIFICATION_EDELTA_ZERO
├── E_DOPAMINE_REWARD_LOOP
├── F_LAKEHOUSE_CAS_MEMORY
├── G_PROVIDER_GRID
├── H_QUOTA_ROUTE_POLICY
├── I_STATUS_OBSERVABILITY
├── J_AGENT_APP_FACTORY
├── K_SHELL_CLI_TUI
├── L_SECURITY_REDACTION
├── M_BUILD_CI_PACKAGING
├── N_TEST_ACCEPTANCE_MATRIX
├── O_BOILERPLATE_STUB_DEBT
└── P_FINAL_ACCEPTANCE

## A_SOURCE_AUTHORITY_DLOI

A001 SourceDocumentRegistry
Source: SD1 .001
Requirement: register Source Doc 1, Source Doc 2, Source Map, section ids, hashes.
Targets: source authority, DLOI service.
Predicate: app can list active source docs and route to exact sections.
Status: VERIFY/PARTIAL.

A002 SourceSectionAddress
Source: SD1 .001
Requirement: typed section ids for .001 .005 .100 .500 1.0.0-1.0.5 and .200-.325.
Predicate: every source section has durable address.
Status: PARTIAL.

A003 DloiRouter
Source: SD1 .001
Requirement: route intent to DLOI domain/category/leaf.
Targets: OntologicalAddressRouter, DloiService.
Predicate: query resolves source section and code symbol.
Status: PARTIAL.

A004 HIGZeroGuard
Source: SD1 .001 / 1.0.1
Requirement: no blind cosine/RAG fallback when exact address required.
Predicate: no-match asks/returns typed no-match, not guessed answer.
Status: MISSING/PARTIAL.

A005 SourceDocToCodeTrace
Source: SD1 all
Requirement: every implemented feature atom traces back to source section.
Predicate: completion ledger lists source section for every feature.
Status: MISSING.

## B_AST_SYMBOL_GRAPH

B001 AstSymbolGraph
Source: SD1 1.0.1
Requirement: node id, DLOI address, symbol type, file path, byte offsets, dependencies.
Predicate: symbol census can be generated from current Kotlin source.
Status: PARTIAL/VERIFY.

B002 TreeSitterGrammarBridge
Source: SD1 1.0.1
Requirement: real AST extraction, not name-only stub.
Predicate: extracts package/class/function symbols.
Status: VERIFY.

B003 AstNamespaceReconciler
Source: SD1 1.0.1
Requirement: imports resolved deterministically from symbol graph.
Predicate: generated imports not guessed.
Status: VERIFY.

B004 SymbolCensusCommand
Source: SD2 .205/.210
Requirement: after dense batch extract package/class/interface/fun headers.
Predicate: flat symbol report exists.
Status: PARTIAL/VERIFY.

## C_LOCAL_TERMUX_RUNTIME

C001 KotlinJvmRuntime
Source: SD1 1.0.3
Requirement: clean Kotlin/JVM Termux runtime under src/main/kotlin/atropos.
Predicate: full compile succeeds.
Status: VERIFY.

C002 LocalToolchainProvider
Source: SD2 .300/.305
Requirement: local compile/git/AST/SQLite/UI root.
Predicate: ATROPOS starts with zero APIs.
Status: PARTIAL/VERIFY.

C003 KotlinCompileProbe
Source: SD2 .305
Requirement: local compile probe.
Predicate: compile errors captured as typed output.
Status: PARTIAL/VERIFY.

C004 GitStateProbe
Source: SD2 .305
Requirement: local git status/diff/recent commits.
Predicate: available without cloud provider.
Status: PARTIAL/VERIFY.

C005 TermuxPathResolver
Source: SD2 .315
Requirement: cwd explicit, Downloads export, no scattered hardcoded paths.
Predicate: workspace root and Downloads export work.
Status: PARTIAL.

## D_VERIFICATION_EDELTA_ZERO

D001 DeterministicVerifier
Source: SD1 1.0.1 / SD2 .215
Requirement: acceptance via deterministic verification.
Predicate: failed gate blocks completion.
Status: PARTIAL/VERIFY.

D002 ConstraintSolverEvaluator
Source: SD1 1.0.1
Requirement: static syntax/boundary evaluation.
Predicate: real checks, not empty class.
Status: VERIFY.

D003 ProbabilisticImmunityEngine
Source: SD1 1.0.1
Requirement: capture stderr and line-level anomaly data.
Predicate: failure stderr is parsed/sliced.
Status: PARTIAL.

D004 NoFakeSuccessGate
Source: SD2 .225/.230
Requirement: no SUCCESS/COMPLETE after failed compile/smoke/jar swap.
Predicate: failure fixture never prints success.
Status: PARTIAL/VERIFY.

D005 SafeJarSwapGate
Source: SD2 .225/.230
Requirement: install jar only after all gates.
Predicate: old jar preserved on failure.
Status: PARTIAL/VERIFY.

## E_DOPAMINE_REWARD_LOOP

E001 SelfImprovingCompilationLoop
Source: SD1 1.0.2 / 1.0.3
Requirement: success/failure compile feedback loop.
Predicate: success logs +1 and failure logs -1.
Status: STUB/PARTIAL.

E002 RewardPenaltyStore
Source: SD1 1.0.3
Requirement: persistent local success_weights or equivalent.
Predicate: reward history survives restart.
Status: MISSING/PARTIAL.

E003 ErrorGradientExtractor
Source: SD1 1.0.1
Requirement: failure -> stderr slice -> localized repair prompt.
Predicate: repair context excludes unrelated files.
Status: PARTIAL.

## F_LAKEHOUSE_CAS_MEMORY

F001 OntologicalAddressRouter
Source: SD1 .001 / 1.0.0
Requirement: DLOI address routing.
Status: PARTIAL/VERIFY.

F002 CloudLakehouseSyncEngine
Source: SD1 .500 / 1.0.1
Requirement: lazy-loading delta replication/CAS sync.
Status: VERIFY.

F003 LocalMemoryStore
Source: SD2 .300/.305
Requirement: SQLite/JSONL local memory root.
Status: PARTIAL/VERIFY.

F004 SqliteVecIntegration
Source: SD1 1.0.3
Requirement: sqlite-vec/vector memory when available.
Status: VERIFY.

F005 Chunking1024Overlap
Source: SD1 1.0.3
Requirement: source docs chunked into 1024-token windows with overlap.
Status: VERIFY.

F006 DriveExportUpload
Source: SD1 .500
Requirement: OAuth Drive upload/export with secrets excluded.
Status: PARTIAL external / VERIFY integrated.

## G_PROVIDER_GRID

G001 ApiCapability
Source: SD2 .300/.305
Requirement: capabilities CHAT CODE REPAIR PLAN LARGE_CONTEXT WEB READER EMBED DB EDGE CI ASSET VISION SECRET STORAGE.
Status: VERIFY.

G002 ProviderDescriptor
Source: SD2 .300/.305
Requirement: provider metadata, cost mode, env vars, fallbacks, models.
Status: VERIFY/PARTIAL.

G003 ProviderResult
Source: SD2 .300/.320
Requirement: normalized success/error/usage envelope.
Status: VERIFY/PARTIAL.

G004 ProviderError
Source: SD2 .300/.320
Requirement: auth_failed, rate_limited, billing_required, timeout, malformed, empty, cancelled, internal.
Status: VERIFY/PARTIAL.

G005 ProviderRegistry30
Source: SD2 .300
Requirement: 30 provider slots from LOCAL_TOOLCHAIN through CUSTOM_USER_API.
Status: PARTIAL.

G006 ProviderFixtureMatrix
Source: SD2 .230/.320
Requirement: every provider has success/error/malformed/empty/timeout/redaction fixtures.
Status: VERIFY.

G007 ProviderLiveOptIn
Source: SD2 .230/.320
Requirement: live tests require ATROPOS_LIVE_PROVIDER_TESTS=1.
Status: VERIFY/PARTIAL.

## H_QUOTA_ROUTE_POLICY

H001 QuotaLedger
Source: SD2 .300/.305
Requirement: state, cooldown, reset, usage, last error, paid lock.
Status: PARTIAL/VERIFY.

H002 ProviderUsageEvent
Source: SD2 .305
Requirement: every provider call records usage/failure safely.
Status: PARTIAL/VERIFY.

H003 RoutePolicy
Source: SD2 .300/.305
Requirement: choose provider by capability, cost mode, quota, cooldown, score, latency.
Status: PARTIAL/VERIFY.

H004 FreeModeGuard
Source: SD2 .300/.305
Requirement: paid providers impossible in free_only mode.
Status: PARTIAL/VERIFY.

H005 EmergencyPaidGate
Source: SD2 .300/.305
Requirement: explicit timed paid unlock with reason and expiry.
Status: PARTIAL/VERIFY.

H006 FallbackChains
Source: SD2 .300
Requirement: CHAT_CHAIN, CODE_CHAIN, REPAIR_CHAIN, PLANNING_CHAIN, DOCS_CHAIN, etc.
Status: PARTIAL.

H007 QueueWhenFreeUnavailable
Source: SD2 .300/.320
Requirement: if all free providers unavailable, queue/degrade, do not fail hard.
Status: VERIFY.

## I_STATUS_OBSERVABILITY

I001 StatusCommand
Source: SD2 .300/.315
Requirement: /status shows local root, provider health, route, queue, paid locks.
Status: PARTIAL/VERIFY.

I002 StatusQuota
Source: SD2 .300
Requirement: /status quota.
Status: PARTIAL/VERIFY.

I003 StatusRoute
Source: SD2 .300
Requirement: /status route <task> explains selected/skipped providers.
Status: PARTIAL/VERIFY.

I004 StatusFailures
Source: SD2 .300
Requirement: redacted provider failures only.
Status: PARTIAL/VERIFY.

I005 TerminalWidthSnapshots
Source: SD2 .220/.315
Requirement: status/dashboard snapshots at 40/80/120 and responsive widths.
Status: PARTIAL/VERIFY.

## J_AGENT_APP_FACTORY

J001 AgentAsk
Source: SD2 .230
Requirement: /agent ask.
Status: PARTIAL/VERIFY.

J002 AgentPatch
Source: SD2 .230
Requirement: /agent patch emits unified diff.
Status: PARTIAL/VERIFY.

J003 AgentApplyCheck
Source: SD2 .230
Requirement: /agent apply --check.
Status: PARTIAL/VERIFY.

J004 AgentApplyLatest
Source: SD2 .230
Requirement: /agent apply latest.
Status: PARTIAL/VERIFY.

J005 AgentQueue
Source: SD2 .300/.315
Requirement: queued jobs survive/retry.
Status: PARTIAL/VERIFY.

J006 AgentDaemon
Source: SD2 .315
Requirement: background/unattended operation.
Status: PARTIAL/VERIFY.

J007 AppFactoryRouter
Source: SD2 .305
Requirement: prompt -> plan -> code -> validate -> repair -> package.
Status: PARTIAL.

J008 EndpointRegistry
Source: SD2 .215
Requirement: every externally invocable operation has stable id and registry entry.
Status: PARTIAL/VERIFY.

J009 EndpointManifest
Source: SD2 .215
Requirement: id, owner, input, output, errors, auth, side effects, timeout, retry, tests.
Status: PARTIAL/MISSING.

J010 DirectorOrchestrator
Source: SD1 1.0.2
Requirement: DAG supervisor.
Status: VERIFY.

J011 WorkerCodeSynthesizer
Source: SD1 1.0.2
Requirement: fan-out code worker.
Status: VERIFY.

## K_SHELL_CLI_TUI

K001 CommandRegistry
Source: SD2 .315/.325
Requirement: real commands advertised from registry.
Status: PARTIAL/VERIFY.

K002 HelpFromRegistry
Source: SD2 .315
Requirement: /help generated from real handlers.
Status: PARTIAL/VERIFY.

K003 SlashCommandIntegrity
Source: SD2 .315
Requirement: every advertised command has handler.
Status: PARTIAL.

K004 ShellBridge
Source: SD2 .315
Requirement: !cmd, /shell, /pwd, /cd, /ls, /git status.
Status: PARTIAL/VERIFY.

K005 ShellAllowlistTimeoutCwdRedaction
Source: SD2 .315
Requirement: shell execution bounded, cwd-aware, redacted.
Status: PARTIAL/VERIFY.

K006 RawKeyReader
Source: SD2 .315/.325
Requirement: arrow navigation, tab completion, key handling.
Status: PARTIAL/VERIFY.

K007 CommandHistory
Source: SD2 .315/.325
Requirement: persistent searchable secret-redacted history.
Status: PARTIAL/VERIFY.

K008 DashboardTabsScreens
Source: SD2 .315
Requirement: Dashboard, Chat, Providers, Factory, Logs, tabs, persistent state.
Status: PARTIAL/VERIFY.

K009 ResponsiveTermuxDashboard
Source: SD2 .315
Requirement: live terminal dimensions, pinch zoom reflow, no dead filler.
Status: PARTIAL.

## L_SECURITY_REDACTION

L001 TokenIsolationVault
Source: SD1 1.0.1 / SD2 .300
Requirement: secrets root local vault/env.
Status: PARTIAL/VERIFY.

L002 SecretSource
Source: SD2 .320
Requirement: provider code does not read env directly; uses secret source.
Status: PARTIAL/VERIFY.

L003 KeyDoctor
Source: SD2 .315
Requirement: /keys doctor diagnoses without raw secrets.
Status: PARTIAL/VERIFY.

L004 RedactionFilter
Source: SD2 .230/.300/.320
Requirement: no raw API keys/tokens/secrets in UI/logs/prompts/diffs/ledger/exports.
Status: PARTIAL/VERIFY.

L005 SecretDiffCheck
Source: SD2 .230
Requirement: before commit, diff contains no secrets.
Status: PARTIAL/VERIFY.

## M_BUILD_CI_PACKAGING

M001 GradleWrapperPinned
Source: SD2 .205/.210
Requirement: wrapper over host gradle.
Status: VERIFY.

M002 GradlePropertiesControlPlane
Source: SD2 .205/.210
Requirement: daemon heap/metaspace/build flags in gradle.properties.
Status: PARTIAL/VERIFY.

M003 KotlinCompatScan
Source: SD2 .225
Requirement: avoid risky APIs and unsupported deps.
Status: VERIFY.

M004 GithubActionsCleanRunner
Source: SD2 .205/.215
Requirement: clean-machine validation lane.
Status: PARTIAL/VERIFY.

M005 SafeJarPackaging
Source: SD2 .225/.230
Requirement: jar build/swap only after gates.
Status: PARTIAL/VERIFY.

M006 DockerNativeDesktopAndroidWebPlan
Source: SD1 .005
Requirement: future multi-platform migration plan.
Status: VERIFY.

## N_TEST_ACCEPTANCE_MATRIX

N001 ProviderTests
Source: SD2 .230/.305/.320
Requirement: descriptors, routing, cooldown, paid locks, redaction, dry-run, fixtures.
Status: PARTIAL.

N002 TerminalTests
Source: SD2 .220/.315
Requirement: 40/80/120, NO_COLOR, TERM=dumb, headless.
Status: PARTIAL.

N003 SourceAuthorityTests
Source: SD1 .001
Requirement: source docs registered, hashes/sections verified.
Status: VERIFY.

N004 EndpointParityTests
Source: SD2 .215
Requirement: contract, implementation, CLI/UI exposure, auth, errors, tests, docs.
Status: VERIFY.

N005 FinalAcceptanceCommand
Source: SD2 .215/.230
Requirement: one final report for Source Docs 1–2 acceptance.
Status: VERIFY.

## O_BOILERPLATE_STUB_DEBT

O001 EmptyNearEmptyKotlinFiles
Requirement: classify every empty/near-empty file as implement/remove/future.
Status: MUST_AUDIT.

O002 DisconnectedFrontendPackage
Requirement: frontend files must be wired to active runtime or moved/removed.
Status: MUST_AUDIT.

O003 SwarmStubs
Requirement: Director/Worker must be real or hidden.
Status: MUST_AUDIT.

O004 CommandRouterConcernMixing
Requirement: split routing/session/UI/shell/provider dispatch if oversized.
Status: MUST_AUDIT.

O005 ProviderTransportNormalizationMixing
Requirement: split transport from response normalizer and fixtures.
Status: MUST_AUDIT.

O006 ProbabilisticImmunityConcernMixing
Requirement: split stderr parsing/scoring/verification.
Status: MUST_AUDIT.

O007 ManualJsonProviderCode
Requirement: replace or test tiny JSON builder/parser.
Status: MUST_AUDIT.

O008 FakeCommands
Requirement: advertised commands without handlers must be implemented or hidden.
Status: MUST_AUDIT.

O009 PlaceholderGreen
Requirement: no TODO/NotImplemented/constant success/fabricated no-op production behavior.
Status: MUST_AUDIT.

O010 MissingTests
Requirement: every implemented feature atom gets behavioral tests.
Status: MUST_AUDIT.

## P_FINAL_ACCEPTANCE

P001 FinalSD1SD2Acceptance
Requirement: all atoms DONE or intentionally deferred non-required; compile/smoke/tests/diff/secrets/status clean.
Output: ATROPOS_SD1_SD2_FINAL_ACCEPTANCE_REPORT.md.
Status: MISSING.

## Required First Task For OpenCode

Create ATROPOS_SD1_SD2_COMPLETION_LEDGER.md.

For every atom:
- mark DONE, PARTIAL, STUB, MISSING, VERIFY, or REMOVE
- list matching files/classes/functions
- list missing files/classes/functions
- list unreachable code
- list artificial boilerplate
- list unfinished files
- list fake commands
- list incomplete providers
- list tests found
- list tests missing
- list exact blocker
- list smallest next pass

Also classify every Kotlin file:
- real/wired
- partial
- stub/artificial
- dead/disconnected
- test-only
- remove/move/future

Do not edit production code in the audit pass.
Do not implement Source Document 3.
Do not use paid providers.
Do not expose secrets.
