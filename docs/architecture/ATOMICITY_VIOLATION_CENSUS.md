# Atomicity Violation Census

Initial ranked census, 2026-08-01. Rankings combine physical size, mixed subsystem imports, mutable state, persistence/process/rendering signals, and fan-out. The existing checker now scans Kotlin, Python, TypeScript, TSX, JavaScript, and JSX. Rankings are triage priorities, not automatic deletion instructions.

| Rank | File | Lines | Signals | Required split direction |
|---:|---|---:|---|---|
| 1 | `apps/specgraph-foundry/scripts/build_execution_receipts.py` | 3,989 | generation, persistence, verification, export | separate receipt model, persistence, canonical serialization, export |
| 2 | `apps/web/src/lib/api/generated.ts` | 3,953 | generated contract | generated output only; move to canonical client package |
| 3 | `apps/specgraph-foundry/src/specgraph_foundry/execution.py` | 2,560 | execution, policy, persistence, receipt creation | extract policy and receipt adapters |
| 4 | `apps/specgraph-foundry/src/specgraph_foundry/exports.py` | 2,519 | serialization, artifact assembly, storage | extract format encoders and storage port |
| 5 | `apps/specgraph-foundry/src/specgraph_foundry/http_api/gateway.py` | 2,247 | routing, auth, normalization, execution | route table, auth boundary, use-case handlers |
| 6 | `src/main/kotlin/atropos/cli/ui/AnsiTerminalEngine.kt` | 634 | input, terminal transport, rendering | input reader, terminal output, renderer |
| 7 | `src/main/kotlin/atropos/core/agent/SelfHostGoalService.kt` | 404 | goal state, DAG execution, evidence, recovery | retain use-case facade; delegate state/evidence/recovery |
| 8 | `src/main/kotlin/atropos/core/agent/AgentRunService.kt` | 398 | routing, provider execution, memory, evidence | run policy, provider call, result persistence |
| 9 | `src/main/kotlin/atropos/core/agent/AgentPatchStore.kt` | 398 | patch persistence, audit, redaction, status | codec, storage, audit boundary |
| 10 | `src/main/kotlin/atropos/cli/CommandRouter.kt` | 800 | command parsing, aliasing, help, dispatch | keep dispatch; delegate command metadata/help |

Additional candidates include `SelfHostEvidenceBundleExporter`, `DagExecutionService`, `DloiService`, `AgentRepairService`, `HierarchyCommand`, and the nested copied web tree. Existing extracted files must be checked for duplicate logic before further splitting.

## Closed Duplicate Policy

`TerritoryEnforcer` previously duplicated path normalization and prefix matching from `TerritoryAssignment`. It now delegates each bulk path check to the canonical assignment predicate; the file remains an adapter because worktree records carry only a list of allowed prefixes.

## Blocking Rules

No file is split mechanically by line count. A split must preserve one canonical owner, migrate callers, add characterization coverage, and leave dependency direction clearer.
