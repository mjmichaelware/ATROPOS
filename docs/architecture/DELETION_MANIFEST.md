# Deletion Manifest

No production deletion is authorized by the initial census batch.

| Path | Reason | Replacement | Proof state |
|---|---|---|---|
| `apps/specgraph-foundry/apps/web/**` | copied web runtime candidate | `apps/web/**` | removed in `web-canonicalization-002` after route/package/history comparison; no nested runtime remains |
| `apps/specgraph-foundry/infra/supabase/migrations/**` | unreachable source/deployment migration mirror | `apps/specgraph-foundry/supabase/migrations/**` | removed in `migration-canonicalization-003`; generator and 25 focused security/RLS tests use canonical paths |
| `docs/ui-parity/phase0/SPECGRAPH_WEB_PATHS.txt` | stale generated path inventory for removed tree | canonical `ATROPOS_WEB_PATHS.txt` output | removed with inventory script consolidation |
| `apps/web/.next/**` | tracked generated build output | ignored local build output | removed from tracking in `generated-residue-001`; web build must regenerate it |
| `apps/web/tsconfig.tsbuildinfo` | tracked compiler cache | ignored local cache | removed from tracking in `generated-residue-001`; typecheck must regenerate it |
| `apps/specgraph-foundry/infra/supabase/migrations/**` | deployment/source migration mirror | `apps/specgraph-foundry/supabase/migrations/**` | blocked pending applied-history verification |
| `\\( HOME/storage/downloads/atropos-codex-audit- \\)(date +%Y%m%d_%H%M%S)/**` | malformed literal-shell audit artifacts with stale deleted-root references | none; generated audit output is not an active source owner | removed in consolidation-runtime-memory-web-007; no callers or build inputs |

This manifest is intentionally conservative. A path is added here before deletion, never after the fact.
