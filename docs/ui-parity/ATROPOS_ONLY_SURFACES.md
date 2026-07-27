# ATROPOS-Only Surfaces

Surfaces with **no OpenCode counterpart**. The pinned reference cannot supply
their behavior — only their *design grammar* (tokens, spacing, card structure,
status vocabulary, keyboard model) once that grammar is derived in Batch B.

## Status

Every surface below is **NOT SPECIFIED**. See blocker **B-002**: the governance
documents that define them are absent from the repository. Building them from
inference would fabricate authority, DAG, verification, and provider state,
which the mission explicitly forbids.

| Surface | Depends on (missing) | Status |
|---|---|---|
| RUN (goal/run/atom/phase/live stream/checkpoint/recovery) | Blueprint DAG, atoms export | NOT SPECIFIED |
| GOALS (list/create/import/timeline/continuation) | Blueprint DAG | NOT SPECIFIED |
| DAG (graph/tree/list, runnable/blocked/claimed, territory) | ATROPOS_COMPLETION_BLUEPRINT_DAG | NOT SPECIFIED |
| SOURCE AUTHORITY (registry, hashes, DLOI, HIG=0, trace) | Source Docs 1–2, Source Document Map | NOT SPECIFIED |
| HIERARCHY / TERRITORY (Owner→Worker, escalation, HR routing) | AGENTS, Agent Playbook | NOT SPECIFIED |
| QUEUE / DAEMON (jobs, leases, stale claims, continuation) | ledgers, handoffs | NOT SPECIFIED |
| CONTEXT SOVEREIGNTY (envelope, attestation, drift, fingerprint) | context-sovereignty requirements | NOT SPECIFIED |
| MEMORY / EXPERIENCE (reward, gradients, consolidation) | ledgers | NOT SPECIFIED |
| EVALUATION (gates, benchmarks, reproducibility) | evaluation spec | NOT SPECIFIED |
| APP FACTORY (request→…→install proof timeline) | workbench requirements | NOT SPECIFIED |
| PREVIEW / INSPECTOR (isolated runtime, viewports, hot reload) | live-preview requirements | NOT SPECIFIED |
| ARTIFACTS (reports, JAR/APK, install proofs, exports) | ledgers | NOT SPECIFIED |

## Partially present today

These have **real backend code** and a thin renderer, so they are the only
ATROPOS-only surfaces that could be advanced without new specs:

| Surface | Backend | Renderer |
|---|---|---|
| PROVIDERS / ROUTING / QUOTAS | `core/provider/`, `core/Routing.kt` | `StatusQuotaRenderer`, `StatusProviderDescriptorRenderer` |
| PATCHES | `core/agent/AgentPatchStore.kt` | `AgentCommand`, dashboard PATCH/APPLY card |
| VERIFY | `core/verification/`, `core/verifier/` | `VerificationRenderer` |
| POLICY / SECURITY (paid lock, redaction) | `core/paid/`, `core/security/` | `StatusPaidEmergencyRenderer`, `StatusSecurityRenderer` |
| JOBS (Pass 10 UI, not yet wired) | none yet | `AgentJobRenderer` |
