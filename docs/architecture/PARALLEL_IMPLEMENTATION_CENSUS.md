# Parallel Implementation Census

Status: initial repository-wide census, 2026-08-01. This is an evidence report, not a completion claim.

## Repository Baseline

The campaign-supplied baseline was 1,668 code-like files, 218,409 physical lines, 196,684 nonblank lines, 379 exact duplicate excess files, and 22,939 exact duplicate excess lines. The current tracked Kotlin/Python/TypeScript/JavaScript/SQL/config/script slice contains 192,882 physical lines. These measurements use different file classes and must not be compared as if they were one counter.

## Exact And Generated Clusters

| Cluster | Evidence | Status | Canonical action |
|---|---|---|---|
| Web generated API client | `apps/web/src/lib/api/generated.ts` and `apps/specgraph-foundry/apps/web/src/lib/api/generated.ts` have SHA-256 `79de69048b57a4a13c63df9a759a5b60d070ddac130e2215c07041b669810209` | exact duplicate | Keep the output under canonical `apps/web`; make the OpenAPI command the only generator and import location |
| Web graph workspace | Both `src/components/graph/graph-workspace.tsx` files have SHA-256 `7c905ab5750b6d27dd18019e21441d4b2cfea790ea19f5ddfd228e6f65acfc15` | exact duplicate | Keep the root ATROPOS web owner |
| Web application tree | 453 files in `apps/web`, 399 in the nested tree; common source/config files are copied, with 19 differing common files | structural/behavioral duplicate | Root selected and nested runtime removed in `web-canonicalization-002`; post-removal web checks remain |
| Next build output | Tracked `apps/web/.next/**` and `apps/web/tsconfig.tsbuildinfo` | generated residue | Remove from tracking and add/confirm ignore rules in the web owner |
| Supabase migrations | the removed `infra/supabase/migrations` mirror matched canonical historical content under different timestamp names; deployment tooling consumes `supabase/migrations` | duplicate history mirror | Canonical `apps/specgraph-foundry/supabase/migrations` remains; writers and tests no longer reference the removed mirror |

## Semantic Parallel Candidates

| Responsibility | Candidate implementations | Preliminary owner | Proof still required |
|---|---|---|---|
| Source authority | `DloiService`, `HigZeroGuard`, `DloiSourceIndexer` | `DloiService` with guard/indexer as typed collaborators | caller graph and authority precedence |
| DAG structure/execution | `DagStore`, `DagService`, `DagExecutionService`, bootstrap/self-host DAG factories | `DagExecutionService` for execution, `DagStore` for persistence | no alternate coordinator mutates DAG state |
| Territory | `TerritoryService`, `TerritoryEnforcer`, `IsolatedWorktreeService` | `TerritoryService` plus enforcement facade | all mutation call sites |
| Verification/completion | `DeterministicVerifier`, `IndependentVerificationGate`, `VerifiedCompletionGate` | `VerifiedCompletionGate` as completion authority | promotion and self-host call graph |
| Evidence/journal | `EvidenceCollector`, `SelfHostEvidenceBundleExporter`, `EventJournalService`, `RunObserver` | evidence collector for evidence; journal for events | ensure no acceptance judgment in collectors |
| Provider registry/routing | `ProviderDescriptorRegistry`, `StaticProviderDescriptorRegistry`, `AdapterRegistry`, `RoutePolicy` | descriptor/adapter registries plus `RoutePolicy` | registry construction and callers |
| Secret/redaction | `TokenIsolationVault`, `SecretSource`, `RedactionFilter`, `KnownSecretRegistry` | vault for storage, redaction filter for egress | all serialization/rendering sinks |
| Memory/recovery | `LocalMemoryStore`, `RestartCoordinator`, `CrashRecoveryService` | local memory and restart coordinator | durable path and state ownership |

## Web Decision

`apps/web` is canonical. It has the active root package name `@atropos/web`, the newest web commits, and the ATROPOS/HOE route history. The nested tree is an older `@specgraph-foundry/web` copy. Its unique deployment/docs references are migration inputs, not permission to retain a second copied runtime.

## Analysis Limits

Normalized, AST-structural, behavioral, and semantic results are recorded as candidates until their caller/configuration proof is completed. Exact byte equality alone never authorizes deletion.
