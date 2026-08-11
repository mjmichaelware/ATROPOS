# Canonical Owner Registry

Status: initial registry, 2026-08-01. Owners are provisional until the consolidation DAG atom reaches its verification gate.

| ID | Responsibility | Canonical owner | Superseded/parallel candidates | Durable state | Accepted boundary | Verification |
|---|---|---|---|---|---|---|
| OWN-01 | source authority | `src/main/kotlin/atropos/dloi/DloiService.kt` | `HigZeroGuard`, `DloiSourceIndexer` are collaborators, not owners | source coordinates/index | typed query/index helpers | caller audit pending |
| OWN-02 | DAG structure | `src/main/kotlin/atropos/core/dag/DagStore.kt` | bootstrap/self-host factories create data only | DAG records | factories may construct proposals | caller audit pending |
| OWN-03 | DAG execution | `src/main/kotlin/atropos/core/dag/DagExecutionService.kt` | `BootstrapAcceptanceDag`, self-host runner adapters | execution state | typed node executors | caller audit pending |
| OWN-04 | goal state | `src/main/kotlin/atropos/core/agent/GoalRunStore.kt` | self-host goal services are use cases | goal/run records | query/start services | focused persistence audit |
| OWN-05 | territory | `src/main/kotlin/atropos/core/territory/TerritoryAssignment.kt` plus `TerritoryService.kt` persistence | `TerritoryEnforcer` and worktree are enforcement adapters | territory grants | pre-mutation checks | `TerritoryEnforcerTest`, worktree call graph |
| OWN-06 | execution policy | `src/main/kotlin/atropos/core/policy/ExecutionPolicyEngine.kt` | `BoundedAgencyGate`, proposal models are gates/contracts | policy decisions | typed proposals/tools | policy caller audit |
| OWN-07 | deterministic verification | `src/main/kotlin/atropos/core/verification/DeterministicVerifier.kt` | `AgentVerifier`, checker are specialized checks | findings | verifier inputs | verifier graph |
| OWN-08 | completion truth | `src/main/kotlin/atropos/core/verification/VerifiedCompletionGate.kt` | no alternate promotion success path allowed | completion findings | promotion consumes result | promotion audit |
| OWN-09 | evidence collection | `src/main/kotlin/atropos/core/evidence/EvidenceCollector.kt` | self-host exporter serializes; journal records events | evidence entries | exporter/observer adapters | evidence audit |
| OWN-10 | artifact production | `src/main/kotlin/atropos/core/artifact/ArtifactPipeline.kt` | candidate JAR builder is a bounded adapter | artifact metadata | typed artifact output | artifact callers |
| OWN-11 | artifact verification | `src/main/kotlin/atropos/core/artifact/ArtifactVerificationService.kt` | safe swap only swaps verified artifacts | artifact findings | verifier gate | artifact tests |
| OWN-12 | event journal | `src/main/kotlin/atropos/core/journal/EventJournalService.kt` | `RunObserver` observes; does not own durable events | event records | observer/export adapters | journal audit |
| OWN-13 | durable memory | src/main/kotlin/atropos/core/memory/LocalMemoryStore.kt | memory helpers/codecs are boundaries | memory records | codec/retention helpers | LocalMemoryStoreTest 9/9; append/restart/corruption coverage |
| OWN-14 | queue/lease truth | `src/main/kotlin/atropos/core/agent/AgentQueueStore.kt` | queue service/recovery are use cases | queue leases | typed service operations | lease audit |
| OWN-15 | session truth | `src/main/kotlin/atropos/core/agent/SupervisedSessionStore.kt` | provider session services orchestrate | sessions | typed session API | session audit |
| OWN-16 | provider registry | `src/main/kotlin/atropos/core/provider/ProviderDescriptorRegistry.kt` and `AdapterRegistry.kt` | static registries are construction adapters | descriptors/adapters | registry interfaces | registry audit |
| OWN-17 | provider activation | `src/main/kotlin/atropos/core/provider/ProviderActivationService.kt` | activation store persists only | readiness facts | durable report | activation tests |
| OWN-18 | provider routing | `src/main/kotlin/atropos/core/provider/RoutePolicy.kt` | failover orchestrates retries | route decisions | typed provider result | route tests |
| OWN-19 | quota/paid locks | `QuotaLedger.kt` and `EmergencyPaidGate.kt` | no second accounting path allowed | usage/unlock records | route policy consumes facts | quota audit |
| OWN-20 | secret storage | `src/main/kotlin/atropos/core/security/TokenIsolationVault.kt` | secret source/key doctor are adapters | vault ciphertext | typed secret access | vault proof |
| OWN-21 | redaction | `src/main/kotlin/atropos/core/security/RedactionFilter.kt` | renderers must consume it | known secret patterns | redacted values | sink audit |
| OWN-22 | repository/source binding | `AtroposRepoRootLocator` plus `SourceBinding` family | provider context consumers are adapters | bound tree/receipts | packer consumes binding | binding audit |
| OWN-23 | command/action registry | `src/main/kotlin/atropos/cli/input/CommandRegistry.kt` | routers/handlers are dispatch consumers | command metadata | help/alias adapters | CLI audit |
| OWN-24 | restart recovery | `src/main/kotlin/atropos/core/recovery/RestartCoordinator.kt` | crash supervisor delegates | snapshots/recovery state | restorer adapters | recovery audit |
| OWN-25 | source-to-code traceability | `DloiService` + `AstSymbolGraph` | command consumers are clients | coordinates/symbol graph | typed lookup/impact query | trace audit |
| OWN-26 | canonical web app | apps/web | apps/specgraph-foundry/apps/web copied runtime | web source/config | package/deploy consumers | nested tree removed; OpenAPI path statically corrected; Node gates unrun |

No superseded owner is deleted by this registry alone. Each deletion requires the safe-deletion protocol and a completed DAG atom.
