# The original 741 code obligations

The obligation registry as it stood at commit `696bf16`, immediately before the audit merge that added 279 more and took the total to 1020.

**741 obligations = 247 requirements x 3 predicates each** (implementation, integration, semantics). Every requirement must satisfy all three: the code exists, something calls it, and it does the right thing.

## Status now

At `696bf16` all 741 were recorded `WRITTEN`. Against the current registry:

| Status today | Count |
|---|---:|
| `WRITTEN` | 735 |
| `NOT_WRITTEN` | 6 |

## The six that were wrong

Recorded `WRITTEN` at `696bf16`, found unsupported by the later audit and now
`NOT_WRITTEN`. Each was credited to a file that does not contain the symbol.

| Obligation | Requirement | Title | Reason |
|---|---|---|---|
| `A001-edge` | A001 | SourceDocumentRegistry: semantics | named atomic owner SourceDocumentRegistry does not exist as a production symbol; credit rested on src/main/kotlin/atropo |
| `A001-impl` | A001 | SourceDocumentRegistry: implementation | named atomic owner SourceDocumentRegistry does not exist as a production symbol; credit rested on src/main/kotlin/atropo |
| `A001-wire` | A001 | SourceDocumentRegistry: integration | named atomic owner SourceDocumentRegistry does not exist as a production symbol; credit rested on src/main/kotlin/atropo |
| `C005-edge` | C005 | TermuxPathResolver: semantics | named atomic owner TermuxPathResolver does not exist as a production symbol; credit rested on build.gradle.kts, which th |
| `C005-impl` | C005 | TermuxPathResolver: implementation | named atomic owner TermuxPathResolver does not exist as a production symbol; credit rested on build.gradle.kts, which th |
| `C005-wire` | C005 | TermuxPathResolver: integration | named atomic owner TermuxPathResolver does not exist as a production symbol; credit rested on build.gradle.kts, which th |

## Requirements by phase

| Phase | Requirements | Obligations |
|---:|---:|---:|
| 0 | 32 | 96 |
| 1 | 1 | 3 |
| 2 | 8 | 24 |
| 3 | 8 | 24 |
| 4 | 6 | 18 |
| 5 | 1 | 3 |
| 6 | 6 | 18 |
| 7 | 5 | 15 |
| 8 | 7 | 21 |
| 9 | 7 | 21 |
| 10 | 40 | 120 |
| 11 | 12 | 36 |
| 12 | 1 | 3 |
| 13 | 1 | 3 |
| 14 | 1 | 3 |
| 15 | 1 | 3 |
| 16 | 1 | 3 |
| 17 | 30 | 90 |
| 18 | 31 | 93 |
| 19 | 22 | 66 |
| 20 | 26 | 78 |


## Phase 0

### C001 — KotlinJvmRuntime

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.3`
- Checkpoint: `C1`
- Canonical owner: `build.gradle.kts`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `C001-impl` | implementation | WRITTEN | `WRITTEN` |
| `C001-wire` | integration | WRITTEN | `WRITTEN` |
| `C001-edge` | semantics | WRITTEN | `WRITTEN` |

### C002 — LocalToolchainProvider

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.305`
- Checkpoint: `C1`
- Canonical owner: `build.gradle.kts`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `C002-impl` | implementation | WRITTEN | `WRITTEN` |
| `C002-wire` | integration | WRITTEN | `WRITTEN` |
| `C002-edge` | semantics | WRITTEN | `WRITTEN` |

### C003 — KotlinCompileProbe

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .305`
- Checkpoint: `C1`
- Canonical owner: `build.gradle.kts`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `C003-impl` | implementation | WRITTEN | `WRITTEN` |
| `C003-wire` | integration | WRITTEN | `WRITTEN` |
| `C003-edge` | semantics | WRITTEN | `WRITTEN` |

### C004 — GitStateProbe

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .305`
- Checkpoint: `C1`
- Canonical owner: `build.gradle.kts`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `C004-impl` | implementation | WRITTEN | `WRITTEN` |
| `C004-wire` | integration | WRITTEN | `WRITTEN` |
| `C004-edge` | semantics | WRITTEN | `WRITTEN` |

### C005 — TermuxPathResolver

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .315`
- Checkpoint: `C1`
- Canonical owner: `build.gradle.kts`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `C005-impl` | implementation | WRITTEN | `NOT_WRITTEN` |
| `C005-wire` | integration | WRITTEN | `NOT_WRITTEN` |
| `C005-edge` | semantics | WRITTEN | `NOT_WRITTEN` |

### I001 — StatusCommand

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.315`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `I001-impl` | implementation | WRITTEN | `WRITTEN` |
| `I001-wire` | integration | WRITTEN | `WRITTEN` |
| `I001-edge` | semantics | WRITTEN | `WRITTEN` |

### I002 — StatusQuota

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `I002-impl` | implementation | WRITTEN | `WRITTEN` |
| `I002-wire` | integration | WRITTEN | `WRITTEN` |
| `I002-edge` | semantics | WRITTEN | `WRITTEN` |

### I003 — StatusRoute

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `I003-impl` | implementation | WRITTEN | `WRITTEN` |
| `I003-wire` | integration | WRITTEN | `WRITTEN` |
| `I003-edge` | semantics | WRITTEN | `WRITTEN` |

### I004 — StatusFailures

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `I004-impl` | implementation | WRITTEN | `WRITTEN` |
| `I004-wire` | integration | WRITTEN | `WRITTEN` |
| `I004-edge` | semantics | WRITTEN | `WRITTEN` |

### I005 — TerminalWidthSnapshots

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .220/.315`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `I005-impl` | implementation | WRITTEN | `WRITTEN` |
| `I005-wire` | integration | WRITTEN | `WRITTEN` |
| `I005-edge` | semantics | WRITTEN | `WRITTEN` |

### M001 — GradleWrapperPinned

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .205/.210`
- Checkpoint: `C1`
- Canonical owner: `build.gradle.kts`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `M001-impl` | implementation | WRITTEN | `WRITTEN` |
| `M001-wire` | integration | WRITTEN | `WRITTEN` |
| `M001-edge` | semantics | WRITTEN | `WRITTEN` |

### M002 — GradlePropertiesControlPlane

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .205/.210`
- Checkpoint: `C1`
- Canonical owner: `build.gradle.kts`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `M002-impl` | implementation | WRITTEN | `WRITTEN` |
| `M002-wire` | integration | WRITTEN | `WRITTEN` |
| `M002-edge` | semantics | WRITTEN | `WRITTEN` |

### M003 — KotlinCompatScan

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .225`
- Checkpoint: `C1`
- Canonical owner: `scripts/kotlin-compat-scan.sh`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `M003-impl` | implementation | WRITTEN | `WRITTEN` |
| `M003-wire` | integration | WRITTEN | `WRITTEN` |
| `M003-edge` | semantics | WRITTEN | `WRITTEN` |

### M004 — GithubActionsCleanRunner

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .205/.215`
- Checkpoint: `C1`
- Canonical owner: `build.gradle.kts`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `M004-impl` | implementation | WRITTEN | `WRITTEN` |
| `M004-wire` | integration | WRITTEN | `WRITTEN` |
| `M004-edge` | semantics | WRITTEN | `WRITTEN` |

### M005 — SafeJarPackaging

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .225/.230`
- Checkpoint: `C1`
- Canonical owner: `build.gradle.kts`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `M005-impl` | implementation | WRITTEN | `WRITTEN` |
| `M005-wire` | integration | WRITTEN | `WRITTEN` |
| `M005-edge` | semantics | WRITTEN | `WRITTEN` |

### M006 — DockerNativeDesktopAndroidWebPlan

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 .005`
- Checkpoint: `C1`
- Canonical owner: `docs/architecture/DOCKER_NATIVE_DESKTOP_ANDROID_WEB_PLAN.md`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `M006-impl` | implementation | WRITTEN | `WRITTEN` |
| `M006-wire` | integration | WRITTEN | `WRITTEN` |
| `M006-edge` | semantics | WRITTEN | `WRITTEN` |

### N001 — ProviderTests

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .230/.305/.320`
- Checkpoint: `C1`
- Canonical owner: `src/test/kotlin/atropos/core/provider/ProviderFixtureMatrixServiceTest.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `N001-impl` | implementation | WRITTEN | `WRITTEN` |
| `N001-wire` | integration | WRITTEN | `WRITTEN` |
| `N001-edge` | semantics | WRITTEN | `WRITTEN` |

### N002 — TerminalTests

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .220/.315`
- Checkpoint: `C1`
- Canonical owner: `src/test/kotlin/atropos/cli/CommandRouterHelpTest.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `N002-impl` | implementation | WRITTEN | `WRITTEN` |
| `N002-wire` | integration | WRITTEN | `WRITTEN` |
| `N002-edge` | semantics | WRITTEN | `WRITTEN` |

### N003 — SourceAuthorityTests

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 .001`
- Checkpoint: `C1`
- Canonical owner: `src/test/kotlin/atropos/dloi/DloiServiceTest.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `N003-impl` | implementation | WRITTEN | `WRITTEN` |
| `N003-wire` | integration | WRITTEN | `WRITTEN` |
| `N003-edge` | semantics | WRITTEN | `WRITTEN` |

### N004 — EndpointParityTests

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .215`
- Checkpoint: `C1`
- Canonical owner: `src/test/kotlin/atropos/core/endpoint/OperationEndpointManifestTest.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `N004-impl` | implementation | WRITTEN | `WRITTEN` |
| `N004-wire` | integration | WRITTEN | `WRITTEN` |
| `N004-edge` | semantics | WRITTEN | `WRITTEN` |

### N005 — FinalAcceptanceCommand

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .215/.230`
- Checkpoint: `C1`
- Canonical owner: `scripts/calculator-final-acceptance.sh`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `N005-impl` | implementation | WRITTEN | `WRITTEN` |
| `N005-wire` | integration | WRITTEN | `WRITTEN` |
| `N005-edge` | semantics | WRITTEN | `WRITTEN` |

### O001 — EmptyNearEmptyKotlinFiles

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `O001 block`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/ArchitectureComplianceChecker.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `O001-impl` | implementation | WRITTEN | `WRITTEN` |
| `O001-wire` | integration | WRITTEN | `WRITTEN` |
| `O001-edge` | semantics | WRITTEN | `WRITTEN` |

### O002 — DisconnectedFrontendPackage

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `O002 block`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/ArchitectureComplianceChecker.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `O002-impl` | implementation | WRITTEN | `WRITTEN` |
| `O002-wire` | integration | WRITTEN | `WRITTEN` |
| `O002-edge` | semantics | WRITTEN | `WRITTEN` |

### O003 — SwarmStubs

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `O003 block`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/ArchitectureComplianceChecker.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `O003-impl` | implementation | WRITTEN | `WRITTEN` |
| `O003-wire` | integration | WRITTEN | `WRITTEN` |
| `O003-edge` | semantics | WRITTEN | `WRITTEN` |

### O004 — CommandRouterConcernMixing

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `O004 block`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/ArchitectureComplianceChecker.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `O004-impl` | implementation | WRITTEN | `WRITTEN` |
| `O004-wire` | integration | WRITTEN | `WRITTEN` |
| `O004-edge` | semantics | WRITTEN | `WRITTEN` |

### O005 — ProviderTransportNormalizationMixing

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `O005 block`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/ArchitectureComplianceChecker.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `O005-impl` | implementation | WRITTEN | `WRITTEN` |
| `O005-wire` | integration | WRITTEN | `WRITTEN` |
| `O005-edge` | semantics | WRITTEN | `WRITTEN` |

### O006 — ProbabilisticImmunityConcernMixing

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `O006 block`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/ArchitectureComplianceChecker.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `O006-impl` | implementation | WRITTEN | `WRITTEN` |
| `O006-wire` | integration | WRITTEN | `WRITTEN` |
| `O006-edge` | semantics | WRITTEN | `WRITTEN` |

### O007 — ManualJsonProviderCode

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `O007 block`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/ArchitectureComplianceChecker.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `O007-impl` | implementation | WRITTEN | `WRITTEN` |
| `O007-wire` | integration | WRITTEN | `WRITTEN` |
| `O007-edge` | semantics | WRITTEN | `WRITTEN` |

### O008 — FakeCommands

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `O008 block`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/ArchitectureComplianceChecker.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `O008-impl` | implementation | WRITTEN | `WRITTEN` |
| `O008-wire` | integration | WRITTEN | `WRITTEN` |
| `O008-edge` | semantics | WRITTEN | `WRITTEN` |

### O009 — PlaceholderGreen

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `O009 block`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/ArchitectureComplianceChecker.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `O009-impl` | implementation | WRITTEN | `WRITTEN` |
| `O009-wire` | integration | WRITTEN | `WRITTEN` |
| `O009-edge` | semantics | WRITTEN | `WRITTEN` |

### O010 — MissingTests

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `O010 block`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/ArchitectureComplianceChecker.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `O010-impl` | implementation | WRITTEN | `WRITTEN` |
| `O010-wire` | integration | WRITTEN | `WRITTEN` |
| `O010-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P00-baseline-lock — Reproducible repository and toolchain baseline artifacts

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 0 gap closure`
- Checkpoint: `C3`
- Canonical owner: `build.gradle.kts`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P00-baseline-lock-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P00-baseline-lock-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P00-baseline-lock-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 1

### BP-P01-activation-doctor — Provider readiness report combines descriptor, adapter, fixture, key, health, quota, route, and use evidence

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 1 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/provider/ProviderActivationService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P01-activation-doctor-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P01-activation-doctor-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P01-activation-doctor-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 2

### G001 — ApiCapability

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.305`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/ProviderActivationService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `G001-impl` | implementation | WRITTEN | `WRITTEN` |
| `G001-wire` | integration | WRITTEN | `WRITTEN` |
| `G001-edge` | semantics | WRITTEN | `WRITTEN` |

### G002 — ProviderDescriptor

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.305`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/ProviderActivationService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `G002-impl` | implementation | WRITTEN | `WRITTEN` |
| `G002-wire` | integration | WRITTEN | `WRITTEN` |
| `G002-edge` | semantics | WRITTEN | `WRITTEN` |

### G003 — ProviderResult

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.320`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/ProviderActivationService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `G003-impl` | implementation | WRITTEN | `WRITTEN` |
| `G003-wire` | integration | WRITTEN | `WRITTEN` |
| `G003-edge` | semantics | WRITTEN | `WRITTEN` |

### G004 — ProviderError

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.320`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/ProviderActivationService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `G004-impl` | implementation | WRITTEN | `WRITTEN` |
| `G004-wire` | integration | WRITTEN | `WRITTEN` |
| `G004-edge` | semantics | WRITTEN | `WRITTEN` |

### G005 — ProviderRegistry30

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/ProviderActivationService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `G005-impl` | implementation | WRITTEN | `WRITTEN` |
| `G005-wire` | integration | WRITTEN | `WRITTEN` |
| `G005-edge` | semantics | WRITTEN | `WRITTEN` |

### G006 — ProviderFixtureMatrix

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .230/.320`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/ProviderFixtureMatrixService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `G006-impl` | implementation | WRITTEN | `WRITTEN` |
| `G006-wire` | integration | WRITTEN | `WRITTEN` |
| `G006-edge` | semantics | WRITTEN | `WRITTEN` |

### G007 — ProviderLiveOptIn

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .230/.320`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/ProviderActivationService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `G007-impl` | implementation | WRITTEN | `WRITTEN` |
| `G007-wire` | integration | WRITTEN | `WRITTEN` |
| `G007-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P02-transport-outcomes — Provider transports have typed success, auth, rate, billing, timeout, malformed, empty, cancellation, and redaction outcomes

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 2 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/provider/adapter/ProviderFailureFixtures.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P02-transport-outcomes-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P02-transport-outcomes-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P02-transport-outcomes-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 3

### H001 — QuotaLedger

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.305`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/RoutePolicy.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `H001-impl` | implementation | WRITTEN | `WRITTEN` |
| `H001-wire` | integration | WRITTEN | `WRITTEN` |
| `H001-edge` | semantics | WRITTEN | `WRITTEN` |

### H002 — ProviderUsageEvent

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .305`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/RoutePolicy.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `H002-impl` | implementation | WRITTEN | `WRITTEN` |
| `H002-wire` | integration | WRITTEN | `WRITTEN` |
| `H002-edge` | semantics | WRITTEN | `WRITTEN` |

### H003 — RoutePolicy

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.305`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/RoutePolicy.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `H003-impl` | implementation | WRITTEN | `WRITTEN` |
| `H003-wire` | integration | WRITTEN | `WRITTEN` |
| `H003-edge` | semantics | WRITTEN | `WRITTEN` |

### H004 — FreeModeGuard

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.305`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/RoutePolicy.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `H004-impl` | implementation | WRITTEN | `WRITTEN` |
| `H004-wire` | integration | WRITTEN | `WRITTEN` |
| `H004-edge` | semantics | WRITTEN | `WRITTEN` |

### H005 — EmergencyPaidGate

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.305`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/RoutePolicy.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `H005-impl` | implementation | WRITTEN | `WRITTEN` |
| `H005-wire` | integration | WRITTEN | `WRITTEN` |
| `H005-edge` | semantics | WRITTEN | `WRITTEN` |

### H006 — FallbackChains

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/provider/RoutePolicy.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `H006-impl` | implementation | WRITTEN | `WRITTEN` |
| `H006-wire` | integration | WRITTEN | `WRITTEN` |
| `H006-edge` | semantics | WRITTEN | `WRITTEN` |

### H007 — QueueWhenFreeUnavailable

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.320`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/agent/AgentQueueService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `H007-impl` | implementation | WRITTEN | `WRITTEN` |
| `H007-wire` | integration | WRITTEN | `WRITTEN` |
| `H007-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P03-route-law — Quota and route policy enforce local/free/fallback/queue/offline/paid ordering

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 3 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/provider/RoutePolicy.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P03-route-law-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P03-route-law-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P03-route-law-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 4

### L001 — TokenIsolationVault

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.1 / SD2 .300`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/security/TokenIsolationVault.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `L001-impl` | implementation | WRITTEN | `WRITTEN` |
| `L001-wire` | integration | WRITTEN | `WRITTEN` |
| `L001-edge` | semantics | WRITTEN | `WRITTEN` |

### L002 — SecretSource

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .320`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/security/TokenIsolationVault.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `L002-impl` | implementation | WRITTEN | `WRITTEN` |
| `L002-wire` | integration | WRITTEN | `WRITTEN` |
| `L002-edge` | semantics | WRITTEN | `WRITTEN` |

### L003 — KeyDoctor

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .315`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/security/TokenIsolationVault.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `L003-impl` | implementation | WRITTEN | `WRITTEN` |
| `L003-wire` | integration | WRITTEN | `WRITTEN` |
| `L003-edge` | semantics | WRITTEN | `WRITTEN` |

### L004 — RedactionFilter

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .230/.300/.320`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/security/TokenIsolationVault.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `L004-impl` | implementation | WRITTEN | `WRITTEN` |
| `L004-wire` | integration | WRITTEN | `WRITTEN` |
| `L004-edge` | semantics | WRITTEN | `WRITTEN` |

### L005 — SecretDiffCheck

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .230`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/security/TokenIsolationVault.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `L005-impl` | implementation | WRITTEN | `WRITTEN` |
| `L005-wire` | integration | WRITTEN | `WRITTEN` |
| `L005-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P04-secret-safety — Secret isolation and redaction cover persisted and displayed surfaces

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 4 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/security/RedactionFilter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P04-secret-safety-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P04-secret-safety-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P04-secret-safety-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 5

### BP-P05-fixture-matrix — Every registered provider has an offline normalized fixture matrix

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 5 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/provider/ProviderFixtureMatrixService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P05-fixture-matrix-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P05-fixture-matrix-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P05-fixture-matrix-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 6

### A001 — SourceDocumentRegistry

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 .001`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/dloi/DloiService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `A001-impl` | implementation | WRITTEN | `NOT_WRITTEN` |
| `A001-wire` | integration | WRITTEN | `NOT_WRITTEN` |
| `A001-edge` | semantics | WRITTEN | `NOT_WRITTEN` |

### A002 — SourceSectionAddress

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 .001`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/dloi/DloiService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `A002-impl` | implementation | WRITTEN | `WRITTEN` |
| `A002-wire` | integration | WRITTEN | `WRITTEN` |
| `A002-edge` | semantics | WRITTEN | `WRITTEN` |

### A003 — DloiRouter

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 .001`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/dloi/DloiService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `A003-impl` | implementation | WRITTEN | `WRITTEN` |
| `A003-wire` | integration | WRITTEN | `WRITTEN` |
| `A003-edge` | semantics | WRITTEN | `WRITTEN` |

### A004 — HIGZeroGuard

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 .001 / 1.0.1`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/dloi/DloiService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `A004-impl` | implementation | WRITTEN | `WRITTEN` |
| `A004-wire` | integration | WRITTEN | `WRITTEN` |
| `A004-edge` | semantics | WRITTEN | `WRITTEN` |

### A005 — SourceDocToCodeTrace

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 all`
- Checkpoint: `C1`
- Canonical owner: `docs/completion/ATROPOS_CODE_OBLIGATION_REGISTRY.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `A005-impl` | implementation | WRITTEN | `WRITTEN` |
| `A005-wire` | integration | WRITTEN | `WRITTEN` |
| `A005-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P06-source-authority — Every executable requirement resolves to durable exact source coordinates and typed no-match

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 6 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/dloi/DloiService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P06-source-authority-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P06-source-authority-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P06-source-authority-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 7

### B001 — AstSymbolGraph

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.1`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/ast/AstSymbolGraph.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `B001-impl` | implementation | WRITTEN | `WRITTEN` |
| `B001-wire` | integration | WRITTEN | `WRITTEN` |
| `B001-edge` | semantics | WRITTEN | `WRITTEN` |

### B002 — TreeSitterGrammarBridge

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.1`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/parser/TreeSitterGrammarBridge.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `B002-impl` | implementation | WRITTEN | `WRITTEN` |
| `B002-wire` | integration | WRITTEN | `WRITTEN` |
| `B002-edge` | semantics | WRITTEN | `WRITTEN` |

### B003 — AstNamespaceReconciler

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.1`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/ast/AstSymbolGraph.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `B003-impl` | implementation | WRITTEN | `WRITTEN` |
| `B003-wire` | integration | WRITTEN | `WRITTEN` |
| `B003-edge` | semantics | WRITTEN | `WRITTEN` |

### B004 — SymbolCensusCommand

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .205/.210`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/ast/AstSymbolGraph.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `B004-impl` | implementation | WRITTEN | `WRITTEN` |
| `B004-wire` | integration | WRITTEN | `WRITTEN` |
| `B004-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P07-symbol-impact — Deterministic symbols, callers, references, and impact queries are available

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 7 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/ast/AstSymbolGraph.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P07-symbol-impact-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P07-symbol-impact-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P07-symbol-impact-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 8

### D001 — DeterministicVerifier

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.1 / SD2 .215`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/DeterministicVerifier.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `D001-impl` | implementation | WRITTEN | `WRITTEN` |
| `D001-wire` | integration | WRITTEN | `WRITTEN` |
| `D001-edge` | semantics | WRITTEN | `WRITTEN` |

### D002 — ConstraintSolverEvaluator

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.1`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verifier/ConstraintSolverEvaluator.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `D002-impl` | implementation | WRITTEN | `WRITTEN` |
| `D002-wire` | integration | WRITTEN | `WRITTEN` |
| `D002-edge` | semantics | WRITTEN | `WRITTEN` |

### D003 — ProbabilisticImmunityEngine

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.1`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/DeterministicVerifier.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `D003-impl` | implementation | WRITTEN | `WRITTEN` |
| `D003-wire` | integration | WRITTEN | `WRITTEN` |
| `D003-edge` | semantics | WRITTEN | `WRITTEN` |

### D004 — NoFakeSuccessGate

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .225/.230`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/DeterministicVerifier.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `D004-impl` | implementation | WRITTEN | `WRITTEN` |
| `D004-wire` | integration | WRITTEN | `WRITTEN` |
| `D004-edge` | semantics | WRITTEN | `WRITTEN` |

### D005 — SafeJarSwapGate

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .225/.230`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/verification/DeterministicVerifier.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `D005-impl` | implementation | WRITTEN | `WRITTEN` |
| `D005-wire` | integration | WRITTEN | `WRITTEN` |
| `D005-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P08-independent-verifier — Failed assertions block completion and promotion with invariant evidence

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 8 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/verification/VerifiedCompletionGate.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P08-independent-verifier-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P08-independent-verifier-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P08-independent-verifier-edge` | semantics | WRITTEN | `WRITTEN` |

### STRICT-OutputValidator — OutputValidator canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 15`
- Checkpoint: `C3`
- Canonical owner: `symbol:OutputValidator`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-15-OutputValidator-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-15-OutputValidator-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-15-OutputValidator-semantics` | semantics | WRITTEN | `WRITTEN` |


## Phase 9

### F001 — OntologicalAddressRouter

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 .001 / 1.0.0`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/memory/LocalMemoryStore.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `F001-impl` | implementation | WRITTEN | `WRITTEN` |
| `F001-wire` | integration | WRITTEN | `WRITTEN` |
| `F001-edge` | semantics | WRITTEN | `WRITTEN` |

### F002 — CloudLakehouseSyncEngine

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 .500 / 1.0.1`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/data/storage/CloudLakehouseSyncEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `F002-impl` | implementation | WRITTEN | `WRITTEN` |
| `F002-wire` | integration | WRITTEN | `WRITTEN` |
| `F002-edge` | semantics | WRITTEN | `WRITTEN` |

### F003 — LocalMemoryStore

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.305`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/memory/LocalMemoryStore.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `F003-impl` | implementation | WRITTEN | `WRITTEN` |
| `F003-wire` | integration | WRITTEN | `WRITTEN` |
| `F003-edge` | semantics | WRITTEN | `WRITTEN` |

### F004 — SqliteVecIntegration

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.3`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/memory/SqliteVecMemoryIndex.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `F004-impl` | implementation | WRITTEN | `WRITTEN` |
| `F004-wire` | integration | WRITTEN | `WRITTEN` |
| `F004-edge` | semantics | WRITTEN | `WRITTEN` |

### F005 — Chunking1024Overlap

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.3`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/memory/MemorySourceChunker.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `F005-impl` | implementation | WRITTEN | `WRITTEN` |
| `F005-wire` | integration | WRITTEN | `WRITTEN` |
| `F005-edge` | semantics | WRITTEN | `WRITTEN` |

### F006 — DriveExportUpload

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 .500`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/memory/LocalMemoryStore.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `F006-impl` | implementation | WRITTEN | `WRITTEN` |
| `F006-wire` | integration | WRITTEN | `WRITTEN` |
| `F006-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P09-memory-integrity — Memory survives restart, has content hashes, exact coordinates, retention, and secret exclusion

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 9 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/memory/LocalMemoryStore.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P09-memory-integrity-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P09-memory-integrity-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P09-memory-integrity-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 10

### K001 — CommandRegistry

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .315/.325`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `K001-impl` | implementation | WRITTEN | `WRITTEN` |
| `K001-wire` | integration | WRITTEN | `WRITTEN` |
| `K001-edge` | semantics | WRITTEN | `WRITTEN` |

### K002 — HelpFromRegistry

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .315`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `K002-impl` | implementation | WRITTEN | `WRITTEN` |
| `K002-wire` | integration | WRITTEN | `WRITTEN` |
| `K002-edge` | semantics | WRITTEN | `WRITTEN` |

### K003 — SlashCommandIntegrity

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .315`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `K003-impl` | implementation | WRITTEN | `WRITTEN` |
| `K003-wire` | integration | WRITTEN | `WRITTEN` |
| `K003-edge` | semantics | WRITTEN | `WRITTEN` |

### K004 — ShellBridge

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .315`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `K004-impl` | implementation | WRITTEN | `WRITTEN` |
| `K004-wire` | integration | WRITTEN | `WRITTEN` |
| `K004-edge` | semantics | WRITTEN | `WRITTEN` |

### K005 — ShellAllowlistTimeoutCwdRedaction

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .315`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `K005-impl` | implementation | WRITTEN | `WRITTEN` |
| `K005-wire` | integration | WRITTEN | `WRITTEN` |
| `K005-edge` | semantics | WRITTEN | `WRITTEN` |

### K006 — RawKeyReader

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .315/.325`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `K006-impl` | implementation | WRITTEN | `WRITTEN` |
| `K006-wire` | integration | WRITTEN | `WRITTEN` |
| `K006-edge` | semantics | WRITTEN | `WRITTEN` |

### K007 — CommandHistory

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .315/.325`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `K007-impl` | implementation | WRITTEN | `WRITTEN` |
| `K007-wire` | integration | WRITTEN | `WRITTEN` |
| `K007-edge` | semantics | WRITTEN | `WRITTEN` |

### K008 — DashboardTabsScreens

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .315`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `K008-impl` | implementation | WRITTEN | `WRITTEN` |
| `K008-wire` | integration | WRITTEN | `WRITTEN` |
| `K008-edge` | semantics | WRITTEN | `WRITTEN` |

### K009 — ResponsiveTermuxDashboard

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .315`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `K009-impl` | implementation | WRITTEN | `WRITTEN` |
| `K009-wire` | integration | WRITTEN | `WRITTEN` |
| `K009-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-001 — Extreme Per-File Atomic Decoupling as Foundational Rule

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 636`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/commands/AgentIdentityResponder.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-001-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-001-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-001-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-002 — Strict Hierarchical Model — Director, Territory, HR Router, Auditor, Custodian

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 3252`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/provider/ContextEnvelope.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-002-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-002-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-002-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-003 — Context Attestation, Drift Detection, and Bounded Agency as First-Class Primitives

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 8332`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/provider/ContextAttestationService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-003-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-003-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-003-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-004 — Evaluation as First-Class Product Subsystem

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 10965`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/agent/SelfHostContextPreflight.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-004-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-004-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-004-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-005 — Observability, Provenance, and Evidence Layer

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 12495`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/agent/AgentExecutionFailure.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-005-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-005-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-005-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-006 — Intent-First Interaction and Canonical Verb+Noun Contract System

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 13626`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/policy/ActionProposal.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-006-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-006-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-006-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-007 — Restart Continuity as Explicit DAG Node

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 14445`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/policy/BoundedAgencyGate.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-007-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-007-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-007-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-008 — Nano-Style Coherent Batch Discipline Applied to Agent Architecture

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 14916`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/territory/TerritoryEnforcer.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-008-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-008-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-008-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-009 — Why Communication-Heavy and Large-Context Designs Are Structurally Flawed

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 15914`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/policy/CapabilityEnforcer.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-009-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-009-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-009-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-010 — Boilerplate, Stub, and Incomplete Implementation Findings from Complete Codebase Analysis

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 17159`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/agent/AgentPromptContract.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-010-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-010-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-010-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-011 — Integration with Existing ATROPOS Architecture Without Duplication

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 19034`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/verification/IndependentVerificationGate.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-011-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-011-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-011-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-036 — Intent-first interaction — natural language becomes the default way to operate ATROPOS.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 23846`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-036-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-036-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-036-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-037 — Canonical typed actions — natural language and commands both resolve to the same deterministic internal action registry.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 23939`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-037-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-037-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-037-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-038 — Command simplification — preserve all 30 current capabilities while reducing the ordinary visible command surface.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 24065`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-038-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-038-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-038-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-039 — Command consolidation — combine overlapping commands such as /home and /dashboard, /exit and /quit, /tab and /tabs.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 24185`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-039-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-039-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-039-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-040 — Aliases without breakage — old commands continue working while visibly resolving to their canonical replacement.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 24306`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-040-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-040-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-040-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-041 — Automatic routine behavior — persistence, recovery, status refresh, provider health, handoffs, evidence capture, and bookkeeping happen automatically.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 24424`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-041-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-041-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-041-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-042 — Autocomplete — typing /st highlights /status without requiring the complete command.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 24580`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-042-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-042-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-042-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-043 — Keyboard navigation — Up, Down, Enter, Tab, Escape, Backspace, and history navigation work correctly.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 24670`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-043-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-043-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-043-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-044 — Touch autocomplete — mobile users can tap command suggestions above the keyboard.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 24777`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-044-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-044-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-044-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-045 — Fuzzy command matching — prefixes, misspellings, aliases, keywords, and recent usage help find commands.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 24864`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-045-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-045-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-045-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-046 — Safe fuzzy execution — uncertain matches are shown for confirmation rather than silently executed.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 24974`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-046-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-046-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-046-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-047 — Context-sensitive suggestions — available actions change based on the current screen, task, failure, patch, or build.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 25078`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-047-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-047-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-047-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-048 — Command palette — search commands, tabs, tasks, files, providers, reports, settings, and recent actions.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 25201`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-048-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-048-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-048-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-049 — Command history — persistent, editable, searchable history with secrets removed.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 25311`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-049-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-049-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-049-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-050 — Inline argument guidance — commands show expected arguments, available values, examples, and current options.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 25397`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-050-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-050-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-050-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-051 — Natural-language mappings — phrases such as “what is running?” resolve to status without requiring /status.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 25512`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-051-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-051-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-051-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-052 — Progressive disclosure — simple, advanced, and expert/debug interaction modes.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 25625`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-052-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-052-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-052-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-053 — Structured command metadata — aliases, descriptions, keywords, visibility, contexts, arguments, and intent phrases stored centrally.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 25709`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-053-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-053-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-053-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-054 — Unified help generation — autocomplete, help, documentation, command palette, and natural-language routing use the same registry.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 25847`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/CommandRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-054-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-054-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-054-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P10-typed-agency — Every side effect follows schema, context, source, territory, redaction, policy, tool, verifier, and evidence

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 10 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/policy/BoundedAgencyGate.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P10-typed-agency-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P10-typed-agency-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P10-typed-agency-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 11

### J001 — AgentAsk

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .230`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/agent/AgentService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `J001-impl` | implementation | WRITTEN | `WRITTEN` |
| `J001-wire` | integration | WRITTEN | `WRITTEN` |
| `J001-edge` | semantics | WRITTEN | `WRITTEN` |

### J002 — AgentPatch

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .230`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/agent/AgentService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `J002-impl` | implementation | WRITTEN | `WRITTEN` |
| `J002-wire` | integration | WRITTEN | `WRITTEN` |
| `J002-edge` | semantics | WRITTEN | `WRITTEN` |

### J003 — AgentApplyCheck

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .230`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/agent/AgentService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `J003-impl` | implementation | WRITTEN | `WRITTEN` |
| `J003-wire` | integration | WRITTEN | `WRITTEN` |
| `J003-edge` | semantics | WRITTEN | `WRITTEN` |

### J004 — AgentApplyLatest

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .230`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/agent/AgentService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `J004-impl` | implementation | WRITTEN | `WRITTEN` |
| `J004-wire` | integration | WRITTEN | `WRITTEN` |
| `J004-edge` | semantics | WRITTEN | `WRITTEN` |

### J005 — AgentQueue

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .300/.315`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/agent/AgentService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `J005-impl` | implementation | WRITTEN | `WRITTEN` |
| `J005-wire` | integration | WRITTEN | `WRITTEN` |
| `J005-edge` | semantics | WRITTEN | `WRITTEN` |

### J006 — AgentDaemon

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .315`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/agent/AgentService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `J006-impl` | implementation | WRITTEN | `WRITTEN` |
| `J006-wire` | integration | WRITTEN | `WRITTEN` |
| `J006-edge` | semantics | WRITTEN | `WRITTEN` |

### J007 — AppFactoryRouter

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .305`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/agent/AgentService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `J007-impl` | implementation | WRITTEN | `WRITTEN` |
| `J007-wire` | integration | WRITTEN | `WRITTEN` |
| `J007-edge` | semantics | WRITTEN | `WRITTEN` |

### J008 — EndpointRegistry

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .215`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/agent/AgentService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `J008-impl` | implementation | WRITTEN | `WRITTEN` |
| `J008-wire` | integration | WRITTEN | `WRITTEN` |
| `J008-edge` | semantics | WRITTEN | `WRITTEN` |

### J009 — EndpointManifest

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD2 .215`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/endpoint/OperationEndpoint.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `J009-impl` | implementation | WRITTEN | `WRITTEN` |
| `J009-wire` | integration | WRITTEN | `WRITTEN` |
| `J009-edge` | semantics | WRITTEN | `WRITTEN` |

### J010 — DirectorOrchestrator

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.2`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/director/DirectorDagSupervisor.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `J010-impl` | implementation | WRITTEN | `WRITTEN` |
| `J010-wire` | integration | WRITTEN | `WRITTEN` |
| `J010-edge` | semantics | WRITTEN | `WRITTEN` |

### J011 — WorkerCodeSynthesizer

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.2`
- Checkpoint: `C1`
- Canonical owner: `src/main/kotlin/atropos/core/agent/WorkerCodeProposalService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `J011-impl` | implementation | WRITTEN | `WRITTEN` |
| `J011-wire` | integration | WRITTEN | `WRITTEN` |
| `J011-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P11-self-build-loop — Durable self-build goal reaches mutation, verification, promotion, rollback, and continuation

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 11 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/agent/SelfHostGoalService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P11-self-build-loop-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P11-self-build-loop-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P11-self-build-loop-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 12

### BP-P12-director-observations — Director observations bind goals, claims, worktrees, requirements, and territories

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 12 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/director/DirectorService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P12-director-observations-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P12-director-observations-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P12-director-observations-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 13

### BP-P13-territory-prechecks — Territory blocks create/edit/delete/rename/shell before mutation

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 13 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/territory/TerritoryEnforcer.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P13-territory-prechecks-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P13-territory-prechecks-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P13-territory-prechecks-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 14

### BP-P14-hr-audit — Cross-boundary requests are identity-bound, redacted, and audited

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 14 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/hr/HrRouterService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P14-hr-audit-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P14-hr-audit-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P14-hr-audit-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 15

### BP-P15-auditor-custodian — Auditor independently blocks promotion and Custodian follows cleanup policy

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 15 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/auditor/AuditorService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P15-auditor-custodian-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P15-auditor-custodian-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P15-auditor-custodian-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 16

### BP-P16-hierarchy-dispatch — Hierarchy dispatch carries scope, capability, budget, acceptance, rollback, and parent authority

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 16 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/hierarchy/HierarchyModels.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P16-hierarchy-dispatch-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P16-hierarchy-dispatch-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P16-hierarchy-dispatch-edge` | semantics | WRITTEN | `WRITTEN` |


## Phase 17

### SD3-012 — OpenCode-style execution stream — detailed, streaming, expandable records of everything ATROPOS is doing.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 21200`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/observability/RunObserver.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-012-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-012-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-012-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-013 — Copyable output cards — copy individual plans, commands, outputs, diffs, logs, provider responses, tests, and reports.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 21311`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/observability/RunObserver.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-013-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-013-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-013-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-014 — Full-run export — export a run as Markdown, JSON, or another durable trace format.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 21435`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/observability/RunObserver.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-014-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-014-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-014-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-015 — Event provenance — every visible event carries timestamp, role, provider, task, requirement, source, and state.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 21523`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/observability/RunObserver.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-015-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-015-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-015-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-016 — Searchable execution history — filter by agent, provider, task, file, test, error, or event type.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 21640`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/observability/RunObserver.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-016-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-016-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-016-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-017 — Google AI Studio-style live preview — see UI and UX changes while ATROPOS is developing them.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 21743`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/preview/LivePreviewService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-017-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-017-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-017-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-018 — Hot reload — accepted UI patches automatically rebuild the preview.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 21842`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/preview/LivePreviewService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-018-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-018-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-018-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-019 — Preview isolation — generated applications run in a restricted sandbox rather than unrestricted host access.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 21915`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/preview/LivePreviewService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-019-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-019-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-019-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-020 — Responsive preview modes — phone, tablet, desktop, custom dimensions, light mode, and dark mode.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 22029`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/preview/LivePreviewService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-020-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-020-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-020-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-021 — Preview diagnostics — build errors, console errors, runtime failures, network failures, and blank screens remain visible.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 22131`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/preview/LivePreviewService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-021-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-021-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-021-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-022 — Visual comparison — before-and-after screenshots, snapshots, history, and rollback.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 22258`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/preview/LivePreviewService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-022-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-022-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-022-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-023 — UI inspection — connect changed files and symbols to affected screens or components where possible.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 22347`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/preview/LivePreviewService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-023-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-023-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-023-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-068 — Accessibility requirements — keyboard use, focus visibility, screen-reader labels, reduced-motion support, and contrast checks.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 27518`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-068-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-068-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-068-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-069 — Performance requirements — responsive input, bounded rendering, virtualized long logs, and controlled background updates.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 27651`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-069-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-069-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-069-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-070 — Failure-first UX — errors are visible, typed, actionable, copyable, and never hidden behind polished success output.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 27778`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/agent/AgentExecutionFailure.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-070-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-070-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-070-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P17-preview-evidence — Isolated preview actuation, diagnostics, visual comparison, and accessibility evidence

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 17 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/preview/LivePreviewService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P17-preview-evidence-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P17-preview-evidence-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P17-preview-evidence-edge` | semantics | WRITTEN | `WRITTEN` |

### SD4-014 — Competitive surface checklist is represented as an acceptance artifact

- Source: `docs/source/ATROPOS_Source_Doc_4.txt` @ `acceptance item 014`
- Checkpoint: `C3`
- Canonical owner: `docs/ui-parity/OPENCODE_COMPLETE_SURFACE_MATRIX.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD4-014-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD4-014-wire` | integration | WRITTEN | `WRITTEN` |
| `SD4-014-edge` | semantics | WRITTEN | `WRITTEN` |

### SD4-015 — Imagination-layer presentation is bound to real engine state

- Source: `docs/source/ATROPOS_Source_Doc_4.txt` @ `acceptance item 015`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/ui/AnsiTerminalEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD4-015-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD4-015-wire` | integration | WRITTEN | `WRITTEN` |
| `SD4-015-edge` | semantics | WRITTEN | `WRITTEN` |

### STRICT-StickyHeader — StickyHeader canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 16`
- Checkpoint: `C3`
- Canonical owner: `symbol:StickyHeader`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-16-StickyHeader-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-16-StickyHeader-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-16-StickyHeader-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-AnimatedThinkingBuffer — AnimatedThinkingBuffer canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 17`
- Checkpoint: `C3`
- Canonical owner: `symbol:AnimatedThinkingBuffer`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-17-AnimatedThinkingBuffer-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-17-AnimatedThinkingBuffer-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-17-AnimatedThinkingBuffer-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-PartialCommandEnterToSelect — PartialCommandEnterToSelect canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 18`
- Checkpoint: `C3`
- Canonical owner: `symbol:PartialCommandEnterToSelect`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-18-PartialCommandEnterToSelect-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-18-PartialCommandEnterToSelect-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-18-PartialCommandEnterToSelect-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ProviderOneLineSummary — ProviderOneLineSummary canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 19`
- Checkpoint: `C3`
- Canonical owner: `symbol:ProviderOneLineSummary`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-19-ProviderOneLineSummary-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-19-ProviderOneLineSummary-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-19-ProviderOneLineSummary-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-CopyDownloadResponse — CopyDownloadResponse canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 20`
- Checkpoint: `C3`
- Canonical owner: `symbol:CopyDownloadResponse`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-20-CopyDownloadResponse-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-20-CopyDownloadResponse-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-20-CopyDownloadResponse-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ResponsiveNativeGrammar — ResponsiveNativeGrammar canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 21`
- Checkpoint: `C3`
- Canonical owner: `symbol:ResponsiveNativeGrammar`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-21-ResponsiveNativeGrammar-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-21-ResponsiveNativeGrammar-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-21-ResponsiveNativeGrammar-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-BaselineSnapshots — BaselineSnapshots canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 22`
- Checkpoint: `C3`
- Canonical owner: `symbol:BaselineSnapshots`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-22-BaselineSnapshots-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-22-BaselineSnapshots-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-22-BaselineSnapshots-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-TerritoryAsMaterial — TerritoryAsMaterial canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 23`
- Checkpoint: `C3`
- Canonical owner: `symbol:TerritoryAsMaterial`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-23-TerritoryAsMaterial-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-23-TerritoryAsMaterial-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-23-TerritoryAsMaterial-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-AttestationOpticalFocus — AttestationOpticalFocus canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 24`
- Checkpoint: `C3`
- Canonical owner: `symbol:AttestationOpticalFocus`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-24-AttestationOpticalFocus-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-24-AttestationOpticalFocus-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-24-AttestationOpticalFocus-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-RecoveryTectonicRibbon — RecoveryTectonicRibbon canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 25`
- Checkpoint: `C3`
- Canonical owner: `symbol:RecoveryTectonicRibbon`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-25-RecoveryTectonicRibbon-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-25-RecoveryTectonicRibbon-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-25-RecoveryTectonicRibbon-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ModeRetheme — ModeRetheme canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 26`
- Checkpoint: `C3`
- Canonical owner: `symbol:ModeRetheme`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-26-ModeRetheme-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-26-ModeRetheme-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-26-ModeRetheme-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-EvidenceMorph — EvidenceMorph canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 27`
- Checkpoint: `C3`
- Canonical owner: `symbol:EvidenceMorph`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-27-EvidenceMorph-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-27-EvidenceMorph-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-27-EvidenceMorph-semantics` | semantics | WRITTEN | `WRITTEN` |


## Phase 18

### SD3-024 — Browser-style persistent tabs — visible tabs with names, active highlighting, switching, closing, reopening, and reordering.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 22452`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-024-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-024-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-024-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-025 — Per-tab state — each tab preserves its task, scroll position, provider, logs, input, and working context.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 22582`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-025-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-025-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-025-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-026 — Tab restoration — tabs survive navigation, restart, interface changes, and session recovery.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 22693`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-026-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-026-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-026-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-027 — Stable native application shell — /home, /tabs, and all views render inside one persistent ATROPOS frame.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 22791`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-027-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-027-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-027-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-028 — Responsive branding — the ATROPOS logo must never be clipped, including the currently cut-off “POS.”

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 22902`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-028-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-028-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-028-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-029 — Responsive layout system — panels, fonts, borders, input areas, and terminal dimensions adapt to available space.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 23008`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-029-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-029-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-029-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-030 — Mobile-first UX — all major capabilities remain usable on Android rather than merely shrinking a desktop screen.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 23127`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-030-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-030-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-030-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-031 — Web, desktop, terminal, and Android parity — the same tasks, tabs, state, evidence, and background jobs appear everywhere.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 23245`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-031-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-031-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-031-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-032 — Real activity indicators — animations for thinking, provider calls, builds, tests, verification, retries, and queued work.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 23373`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-032-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-032-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-032-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-033 — No fake progress — animations must correspond to real persisted process states.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 23501`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-033-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-033-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-033-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-034 — Background-process panel — show running jobs, children, elapsed time, logs, progress, owner, status, and cancellation controls.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 23586`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-034-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-034-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-034-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-035 — Stall and failure visibility — distinguish running, waiting, blocked, retrying, stalled, failed, cancelled, and complete.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 23719`
- Checkpoint: `C3`
- Canonical owner: `apps/web/package.json`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-035-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-035-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-035-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P18-shared-platform — CLI, web, desktop, and Android expose shared durable core contracts

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 18 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/platform/SharedPlatformContract.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P18-shared-platform-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P18-shared-platform-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P18-shared-platform-edge` | semantics | WRITTEN | `WRITTEN` |

### SD4-013 — Full CLI power is available through Web and Android via a shared bridge

- Source: `docs/source/ATROPOS_Source_Doc_4.txt` @ `acceptance item 013`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/platform/PlatformAbstraction.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD4-013-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD4-013-wire` | integration | WRITTEN | `WRITTEN` |
| `SD4-013-edge` | semantics | WRITTEN | `WRITTEN` |

### STRICT-AndroidBridge — AndroidBridge canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 28`
- Checkpoint: `C3`
- Canonical owner: `symbol:AndroidBridge`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-28-AndroidBridge-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-28-AndroidBridge-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-28-AndroidBridge-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-AndroidEngineBridge — AndroidEngineBridge canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 29`
- Checkpoint: `C3`
- Canonical owner: `symbol:AndroidEngineBridge`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-29-AndroidEngineBridge-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-29-AndroidEngineBridge-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-29-AndroidEngineBridge-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-LocalEngineBridge — LocalEngineBridge canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 30`
- Checkpoint: `C3`
- Canonical owner: `symbol:LocalEngineBridge`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-30-LocalEngineBridge-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-30-LocalEngineBridge-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-30-LocalEngineBridge-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-SideloadApk — SideloadApk canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 31`
- Checkpoint: `C3`
- Canonical owner: `symbol:SideloadApk`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-31-SideloadApk-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-31-SideloadApk-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-31-SideloadApk-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ApkSigner — ApkSigner canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 32`
- Checkpoint: `C3`
- Canonical owner: `symbol:ApkSigner`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-32-ApkSigner-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-32-ApkSigner-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-32-ApkSigner-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ComposeAppShell — ComposeAppShell canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 33`
- Checkpoint: `C3`
- Canonical owner: `symbol:ComposeAppShell`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-33-ComposeAppShell-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-33-ComposeAppShell-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-33-ComposeAppShell-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ChatListScreen — ChatListScreen canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 34`
- Checkpoint: `C3`
- Canonical owner: `symbol:ChatListScreen`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-34-ChatListScreen-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-34-ChatListScreen-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-34-ChatListScreen-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ConversationScreen — ConversationScreen canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 35`
- Checkpoint: `C3`
- Canonical owner: `symbol:ConversationScreen`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-35-ConversationScreen-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-35-ConversationScreen-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-35-ConversationScreen-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ComposerScreen — ComposerScreen canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 36`
- Checkpoint: `C3`
- Canonical owner: `symbol:ComposerScreen`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-36-ComposerScreen-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-36-ComposerScreen-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-36-ComposerScreen-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-CheckpointChip — CheckpointChip canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 37`
- Checkpoint: `C3`
- Canonical owner: `symbol:CheckpointChip`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-37-CheckpointChip-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-37-CheckpointChip-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-37-CheckpointChip-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ThinkingSheet — ThinkingSheet canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 38`
- Checkpoint: `C3`
- Canonical owner: `symbol:ThinkingSheet`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-38-ThinkingSheet-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-38-ThinkingSheet-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-38-ThinkingSheet-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-OneHandDensity — OneHandDensity canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 39`
- Checkpoint: `C3`
- Canonical owner: `symbol:OneHandDensity`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-39-OneHandDensity-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-39-OneHandDensity-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-39-OneHandDensity-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-SessionTabModel — SessionTabModel canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 40`
- Checkpoint: `C3`
- Canonical owner: `symbol:SessionTabModel`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-40-SessionTabModel-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-40-SessionTabModel-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-40-SessionTabModel-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-StreamingApprovalCards — StreamingApprovalCards canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 41`
- Checkpoint: `C3`
- Canonical owner: `symbol:StreamingApprovalCards`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-41-StreamingApprovalCards-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-41-StreamingApprovalCards-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-41-StreamingApprovalCards-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-DeveloperToolsContainer — DeveloperToolsContainer canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 42`
- Checkpoint: `C3`
- Canonical owner: `symbol:DeveloperToolsContainer`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-42-DeveloperToolsContainer-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-42-DeveloperToolsContainer-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-42-DeveloperToolsContainer-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ViewTransitionEvidence — ViewTransitionEvidence canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 43`
- Checkpoint: `C3`
- Canonical owner: `symbol:ViewTransitionEvidence`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-43-ViewTransitionEvidence-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-43-ViewTransitionEvidence-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-43-ViewTransitionEvidence-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-WebMergeArchitecture — WebMergeArchitecture canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 44`
- Checkpoint: `C3`
- Canonical owner: `symbol:WebMergeArchitecture`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-44-WebMergeArchitecture-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-44-WebMergeArchitecture-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-44-WebMergeArchitecture-semantics` | semantics | WRITTEN | `WRITTEN` |


## Phase 19

### BP-P19-project-creation — Natural-language project creation and iterative editing

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 19 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/factory/AppFactoryRouter.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P19-project-creation-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P19-project-creation-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P19-project-creation-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P19-source-tree-git — Real source tree, editable code, exact project paths, and Git history

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 19 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/factory/AppProjectGenerator.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P19-source-tree-git-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P19-source-tree-git-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P19-source-tree-git-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P19-live-preview — Live preview, hot reload, diagnostics, screenshots, and rollback

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 19 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/multimodal/LivePreviewEvidenceService.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P19-live-preview-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P19-live-preview-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P19-live-preview-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P19-frontend-generation — Frontend pages, components, navigation, state, forms, and design-system generation

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 19 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/factory/AppSourceTemplate.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P19-frontend-generation-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P19-frontend-generation-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P19-frontend-generation-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P19-database-security — Database/entity/schema and migration planning with explicit security rules

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 19 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/factory/AppDatabaseSecurityPlanner.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P19-database-security-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P19-database-security-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P19-database-security-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P19-auth — Authentication, sessions, roles, permissions, and authorization

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 19 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/factory/AppAuthPlanner.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P19-auth-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P19-auth-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P19-auth-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P19-backend-integrations — Backend functions, APIs, scheduled tasks, storage, realtime behavior, and integrations

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 19 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/factory/AppBackendIntegrationPlanner.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P19-backend-integrations-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P19-backend-integrations-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P19-backend-integrations-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P19-secret-management — Environment and secret management with zero raw-secret exposure

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 19 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/security/TokenIsolationVault.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P19-secret-management-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P19-secret-management-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P19-secret-management-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P19-browser-verification — Browser-driven user-flow testing and deterministic backend verification

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 19 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/multimodal/BrowserActuator.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P19-browser-verification-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P19-browser-verification-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P19-browser-verification-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P19-portable-github — GitHub import/sync/export, local development, cloning, templates, and portable ownership

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 19 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/worktree/BoundedGitWorktreeCommandRunner.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P19-portable-github-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P19-portable-github-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P19-portable-github-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P19-deployment — Packaging, hosting/deployment, preview/live environments, domains, HTTPS, and release rollback

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 19 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/artifact/ArtifactPipeline.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P19-deployment-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P19-deployment-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P19-deployment-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P19-activity-monitor — Activity monitor for plans, providers, tools, diffs, tests, verifiers, artifacts, and deployments

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 19 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/observability/RunObserver.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P19-activity-monitor-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P19-activity-monitor-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P19-activity-monitor-edge` | semantics | WRITTEN | `WRITTEN` |

### STRICT-BoundedWorkExecutor — BoundedWorkExecutor canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 1`
- Checkpoint: `C3`
- Canonical owner: `symbol:BoundedWorkExecutor`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-01-BoundedWorkExecutor-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-01-BoundedWorkExecutor-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-01-BoundedWorkExecutor-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-BatchGate — BatchGate canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 2`
- Checkpoint: `C3`
- Canonical owner: `symbol:BatchGate`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-02-BatchGate-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-02-BatchGate-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-02-BatchGate-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-GitHubBinding — GitHubBinding canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 3`
- Checkpoint: `C3`
- Canonical owner: `symbol:GitHubBinding`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-03-GitHubBinding-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-03-GitHubBinding-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-03-GitHubBinding-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-GraphClaimService — GraphClaimService canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 4`
- Checkpoint: `C3`
- Canonical owner: `symbol:GraphClaimService`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-04-GraphClaimService-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-04-GraphClaimService-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-04-GraphClaimService-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-GraphTransitionService — GraphTransitionService canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 5`
- Checkpoint: `C3`
- Canonical owner: `symbol:GraphTransitionService`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-05-GraphTransitionService-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-05-GraphTransitionService-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-05-GraphTransitionService-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-IntentParser — IntentParser canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 6`
- Checkpoint: `C3`
- Canonical owner: `symbol:IntentParser`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-06-IntentParser-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-06-IntentParser-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-06-IntentParser-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-FuzzyMatcher — FuzzyMatcher canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 7`
- Checkpoint: `C3`
- Canonical owner: `symbol:FuzzyMatcher`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-07-FuzzyMatcher-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-07-FuzzyMatcher-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-07-FuzzyMatcher-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-SuggestionEngine — SuggestionEngine canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 8`
- Checkpoint: `C3`
- Canonical owner: `symbol:SuggestionEngine`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-08-SuggestionEngine-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-08-SuggestionEngine-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-08-SuggestionEngine-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-HelpRegistry — HelpRegistry canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 9`
- Checkpoint: `C3`
- Canonical owner: `symbol:HelpRegistry`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-09-HelpRegistry-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-09-HelpRegistry-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-09-HelpRegistry-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-CommandHistoryStore — CommandHistoryStore canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 10`
- Checkpoint: `C3`
- Canonical owner: `symbol:CommandHistoryStore`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-10-CommandHistoryStore-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-10-CommandHistoryStore-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-10-CommandHistoryStore-semantics` | semantics | WRITTEN | `WRITTEN` |


## Phase 20

### E001 — SelfImprovingCompilationLoop

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.2 / 1.0.3`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/knowledge/SelfImprovingCompilationLoop.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `E001-impl` | implementation | WRITTEN | `WRITTEN` |
| `E001-wire` | integration | WRITTEN | `WRITTEN` |
| `E001-edge` | semantics | WRITTEN | `WRITTEN` |

### E002 — RewardPenaltyStore

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.3`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/autonomy/RewardPenaltyStore.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `E002-impl` | implementation | WRITTEN | `WRITTEN` |
| `E002-wire` | integration | WRITTEN | `WRITTEN` |
| `E002-edge` | semantics | WRITTEN | `WRITTEN` |

### E003 — ErrorGradientExtractor

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `SD1 1.0.1`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/agent/SelfHostCandidateJarBuilder.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `E003-impl` | implementation | WRITTEN | `WRITTEN` |
| `E003-wire` | integration | WRITTEN | `WRITTEN` |
| `E003-edge` | semantics | WRITTEN | `WRITTEN` |

### P001 — FinalSD1SD2Acceptance

- Source: `docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md` @ `P001 block`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/acceptance/FinalSD1SD2Acceptance.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `P001-impl` | implementation | WRITTEN | `WRITTEN` |
| `P001-wire` | integration | WRITTEN | `WRITTEN` |
| `P001-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-055 — Evaluation scoring subsystem — implement the ATROPOS Evaluation Spec as a real product feature.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 25982`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/evaluation/EvaluationEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-055-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-055-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-055-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-056 — Benchmark support — SWE-bench Verified, Terminal-Bench, Aider Polyglot, PR acceptance, and time-to-accepted-PR.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 26083`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/evaluation/EvaluationEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-056-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-056-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-056-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-057 — ATROPOS-specific metrics — restart recovery, verifier-first catches, coordination efficiency, territory safety, and secret safety.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 26200`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/evaluation/EvaluationEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-057-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-057-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-057-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-058 — Context and UX metrics — identity recognition, context attestation, drift detection, trace completeness, copy fidelity, preview success, and event determinism.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 26336`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/evaluation/EvaluationEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-058-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-058-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-058-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-059 — Correct normalization — repair the zero-target division flaw in lower-is-better metrics.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 26501`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/evaluation/EvaluationEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-059-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-059-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-059-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-060 — Correct release gates — clearly separate score reduction, minimum failure, competitive failure, frontier failure, and safety hard failure.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 26595`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/evaluation/EvaluationEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-060-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-060-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-060-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-061 — Zero-secret-leak release rule — any confirmed secret leak blocks release.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 26739`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/evaluation/EvaluationEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-061-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-061-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-061-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-062 — Evidence-backed metrics — no unsupported manual percentages; every metric links to raw immutable evidence.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 26818`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/evaluation/EvaluationEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-062-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-062-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-062-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-063 — Historical evaluation records — retain benchmark versions, environment fingerprints, source fingerprints, and result history.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 26930`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/evaluation/EvaluationEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-063-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-063-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-063-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-064 — Minimum, competitive, and frontier classifications — calculate all three explicitly.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 27061`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/evaluation/EvaluationEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-064-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-064-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-064-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-065 — Evaluation CLI and dashboard — results available in terminal, web, machine-readable, and human-readable forms.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 27151`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/evaluation/EvaluationEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-065-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-065-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-065-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-066 — Anti-gaming and reproducibility — metric definitions, fixtures, environments, and scoring must be repeatable and auditable.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 27267`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/evaluation/EvaluationEngine.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-066-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-066-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-066-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-067 — Restart continuity — restore run state, task state, tabs, preview, logs, evidence, and the next executable DAG node.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 27396`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/recovery/RestartCoordinator.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-067-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-067-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-067-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-071 — Canonical acceptance tests — tests for context recognition, territory blocks, autocomplete, tabs, responsive rendering, previews, exports, recovery, and process visibility.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 27900`
- Checkpoint: `C3`
- Canonical owner: `src/test/kotlin/atropos/core/acceptance/CanonicalAcceptanceTests.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-071-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-071-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-071-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-072 — Evaluation-spec integration — this document becomes an addendum to the current source authority, not a disconnected wishlist.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 28078`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/acceptance/EvaluationSpecIntegration.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-072-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-072-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-072-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-073 — No duplicate architecture — reuse and extend existing context, policy, DLOI, DAG, queue, memory, provider, verifier, renderer, and persistence systems.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 28209`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/verification/ArchitectureComplianceChecker.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-073-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-073-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-073-edge` | semantics | WRITTEN | `WRITTEN` |

### SD3-074 — Core UX principle — ATROPOS absorbs its own complexity; the user expresses intent instead of memorizing implementation details.

- Source: `docs/source/ATROPOS_Source_Doc_3.txt` @ `line 28366`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/cli/commands/SelfHostCommand.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `SD3-074-impl` | implementation | WRITTEN | `WRITTEN` |
| `SD3-074-wire` | integration | WRITTEN | `WRITTEN` |
| `SD3-074-edge` | semantics | WRITTEN | `WRITTEN` |

### BP-P20-long-horizon — Restart, evaluation, bounded learning, provenance export, fallback, and crossover hooks

- Source: `docs/source/ATROPOS_100pct_Completion_Blueprint.txt` @ `Phase 20 gap closure`
- Checkpoint: `C3`
- Canonical owner: `src/main/kotlin/atropos/core/recovery/RestartCoordinator.kt`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `BP-P20-long-horizon-impl` | implementation | WRITTEN | `WRITTEN` |
| `BP-P20-long-horizon-wire` | integration | WRITTEN | `WRITTEN` |
| `BP-P20-long-horizon-edge` | semantics | WRITTEN | `WRITTEN` |

### STRICT-AntiGamingAuditor — AntiGamingAuditor canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 11`
- Checkpoint: `C3`
- Canonical owner: `symbol:AntiGamingAuditor`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-11-AntiGamingAuditor-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-11-AntiGamingAuditor-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-11-AntiGamingAuditor-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ReleaseGateEvaluator — ReleaseGateEvaluator canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 12`
- Checkpoint: `C3`
- Canonical owner: `symbol:ReleaseGateEvaluator`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-12-ReleaseGateEvaluator-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-12-ReleaseGateEvaluator-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-12-ReleaseGateEvaluator-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ReproducibilityGate — ReproducibilityGate canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 13`
- Checkpoint: `C3`
- Canonical owner: `symbol:ReproducibilityGate`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-13-ReproducibilityGate-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-13-ReproducibilityGate-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-13-ReproducibilityGate-semantics` | semantics | WRITTEN | `WRITTEN` |

### STRICT-ProposalGenerator — ProposalGenerator canonical atomic owner

- Source: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md` @ `strict absent-atom audit item 14`
- Checkpoint: `C3`
- Canonical owner: `symbol:ProposalGenerator`

| Obligation | Predicate | Status then | Status now |
|---|---|---|---|
| `STRICT-14-ProposalGenerator-implementation` | implementation | WRITTEN | `WRITTEN` |
| `STRICT-14-ProposalGenerator-integration` | integration | WRITTEN | `WRITTEN` |
| `STRICT-14-ProposalGenerator-semantics` | semantics | WRITTEN | `WRITTEN` |
