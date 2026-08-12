# ATROPOS Code-Base Completion Report

Generated: 2026-08-12T08:42:44Z
Current Git HEAD: 35d39c5ec51619ef10c49239c81c7f216dbe1e28
Historical reconstruction HEAD: 7e612fcdba571b276a4ae65704835eb762030682 (nearest recoverable commit; exact locked export unavailable)

## Code-Base Obligation Set

Total binary obligations: 741
Current WRITTEN: 609
Current NOT_WRITTEN: 132
Current CODE-BASE COMPLETION: 82.19% (609/741)
Historical WRITTEN: 517
Historical NOT_WRITTEN: 224
Historical CODE-BASE COMPLETION: 69.77% (517/741)
Code-base delta: +12.42 percentage points

The denominator is binary implementation obligations directly juxtaposed with the current codebase. SHA-256 values prove authority identity; document bytes are provenance telemetry, not completion weights. Gap-map atoms are crosswalked to existing requirements and receive no duplicate credit.

## Authority Coverage

Hashed authority documents: 19
Source Doc 4 unique obligations added: 9
Core, HOE, and Phase 20 PDF atoms are registered in `authorityCrosswalk` and mapped to existing obligations unless explicitly unique.

## Phase Accounting

| Phase | Total | Current written | Current code % | Historical written | Delta pp | Not written | Missing IDs |
|---:|---:|---:|---:|---:|---:|---:|---|
| 0 | 96 | 96 | 100.00% | 87 | +9.38 | 0 |  |
| 1 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 2 | 24 | 24 | 100.00% | 24 | +0.00 | 0 |  |
| 3 | 24 | 24 | 100.00% | 24 | +0.00 | 0 |  |
| 4 | 18 | 18 | 100.00% | 18 | +0.00 | 0 |  |
| 5 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 6 | 18 | 18 | 100.00% | 15 | +16.67 | 0 |  |
| 7 | 15 | 15 | 100.00% | 15 | +0.00 | 0 |  |
| 8 | 21 | 18 | 85.71% | 18 | +0.00 | 3 | STRICT-15-OutputValidator-implementation, STRICT-15-OutputValidator-integration, STRICT-15-OutputValidator-semantics |
| 9 | 21 | 21 | 100.00% | 16 | +23.81 | 0 |  |
| 10 | 120 | 120 | 100.00% | 114 | +5.00 | 0 |  |
| 11 | 36 | 36 | 100.00% | 31 | +13.89 | 0 |  |
| 12 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 13 | 3 | 3 | 100.00% | 0 | +100.00 | 0 |  |
| 14 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 15 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 16 | 3 | 3 | 100.00% | 0 | +100.00 | 0 |  |
| 17 | 90 | 54 | 60.00% | 29 | +27.78 | 36 | STRICT-16-StickyHeader-implementation, STRICT-16-StickyHeader-integration, STRICT-16-StickyHeader-semantics, STRICT-17-AnimatedThinkingBuffer-implementation, STRICT-17-AnimatedThinkingBuffer-integration, STRICT-17-AnimatedThinkingBuffer-semantics, STRICT-18-PartialCommandEnterToSelect-implementation, STRICT-18-PartialCommandEnterToSelect-integration, STRICT-18-PartialCommandEnterToSelect-semantics, STRICT-19-ProviderOneLineSummary-implementation ... |
| 18 | 93 | 42 | 45.16% | 39 | +3.23 | 51 | STRICT-28-AndroidBridge-implementation, STRICT-28-AndroidBridge-integration, STRICT-28-AndroidBridge-semantics, STRICT-29-AndroidEngineBridge-implementation, STRICT-29-AndroidEngineBridge-integration, STRICT-29-AndroidEngineBridge-semantics, STRICT-30-LocalEngineBridge-implementation, STRICT-30-LocalEngineBridge-integration, STRICT-30-LocalEngineBridge-semantics, STRICT-31-SideloadApk-implementation ... |
| 19 | 66 | 36 | 54.55% | 18 | +27.27 | 30 | STRICT-01-BoundedWorkExecutor-implementation, STRICT-01-BoundedWorkExecutor-integration, STRICT-01-BoundedWorkExecutor-semantics, STRICT-02-BatchGate-implementation, STRICT-02-BatchGate-integration, STRICT-02-BatchGate-semantics, STRICT-03-GitHubBinding-implementation, STRICT-03-GitHubBinding-integration, STRICT-03-GitHubBinding-semantics, STRICT-04-GraphClaimService-implementation ... |
| 20 | 78 | 66 | 84.62% | 54 | +15.38 | 12 | STRICT-11-AntiGamingAuditor-implementation, STRICT-11-AntiGamingAuditor-integration, STRICT-11-AntiGamingAuditor-semantics, STRICT-12-ReleaseGateEvaluator-implementation, STRICT-12-ReleaseGateEvaluator-integration, STRICT-12-ReleaseGateEvaluator-semantics, STRICT-13-ReproducibilityGate-implementation, STRICT-13-ReproducibilityGate-integration, STRICT-13-ReproducibilityGate-semantics, STRICT-14-ProposalGenerator-implementation ... |

## Strict Canonical-Owner Audit

Strict owner atoms audited: 44
Strict owner predicates: 132
Strict predicates WRITTEN: 0
Strict predicates NOT_WRITTEN: 132

Each strict atom requires a production owner symbol, a reachable production reference, and an independent test/evidence reference. Broad subsystem files do not receive silent credit for a named atomic owner.

Strict audit evidence: `docs/completion/ATROPOS_STRICT_ABSENT_ATOM_AUDIT.md`.

## Checkpoints and Horizons

| Group | Phases | Total | Current written | Code % | Historical written | Delta pp |
|---|---|---:|---:|---:|---:|---:|
| Checkpoint 1 | 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 | 399 | 396 | 99.25% | 368 | +7.02 |
| Checkpoint 2 | 12, 13, 14, 15, 16 | 15 | 15 | 100.00% | 9 | +40.00 |
| Checkpoint 3 | 17, 18, 19 | 249 | 132 | 53.01% | 86 | +18.47 |
| Checkpoint 4 | 20 | 78 | 66 | 84.62% | 54 | +15.38 |
| Horizon I | 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 | 363 | 360 | 99.17% | 337 | +6.34 |
| Horizon II | 11, 12, 13, 14, 15, 16 | 51 | 51 | 100.00% | 40 | +21.57 |
| Horizon III | 17, 18 | 183 | 96 | 52.46% | 68 | +15.30 |
| Horizon IV | 19 | 66 | 36 | 54.55% | 18 | +27.27 |
| Horizon V | 20 | 78 | 66 | 84.62% | 54 | +15.38 |

## Required Named Surfaces

### Critical stubs and audit findings

- ConstraintSolverEvaluator: the audit did not accept evidence for source atom D002 semantic obligations.
- TreeSitterGrammarBridge: the audit did not accept evidence for source atom B002 semantic obligations.
- DirectorOrchestrator and WorkerCodeSynthesizer: the audit did not accept evidence for source atoms J010/J011 semantic obligations; this is not a proof that no related implementation exists.
- Missing obligation IDs in the phase table are the authoritative follow-up list for this audit, not a filesystem deletion claim.

### HOE

HOE/UI obligations are represented by Source Doc 3 requirements 12-54 and 68-70, mapped to Phases 10, 17, and 18. Their binary counts are included in those phase rows; test and browser proof state is separate.

### App Factory

App Factory obligations are represented by Phase 19 blueprint additions and the relevant Source Doc 3 requirements. The report gives credit only where the exact required production owner paths exist; `NOT_WRITTEN` records audit non-acceptance, not proven absence.

### Phase 20

Phase 20 includes evaluation, restart, bounded learning, observability, safety, fallback, and crossover obligations. Installed self-host proof is operational evidence only and does not alter these code counts.

### Implementation surface breakdown

| Surface | Phase groups | Accounting treatment |
|---|---|---|
| Frontend/UI | 17-19 and SD3 UI requirements | Separate code obligations; browser/test proof excluded from code percentage |
| Backend/core | 0-16 | Canonical owner paths and audit evidence determine code status |
| Database/source authority | 6, 9, 19-20 | Migration and source-coordinate obligations are counted only when mapped to a canonical owner |
| Platform/runtime | 0, 11, 18, 20 | Toolchain, self-host, platform, and recovery code obligations; packaging/install proof is separate |

## Historical and Scope Note

The former approximately 42% and 43.6% values mixed implementation, tests, compilation, packaging, installation, restart, deployment, Git cleanliness, and operator proofs. They remain immutable historical records in AGENTS.md and are superseded for future CODE-BASE COMPLETION reporting by this binary obligation method.

This is a conservative static code-base audit. `NOT_WRITTEN` means the auditor did not accept qualifying evidence; it does not prove that the implementation is absent. The registry field `auditFinding=NOT_EVIDENCED_BY_AUDIT` identifies this distinction. Tests and operational evidence are separate. The nearest recoverable historical commit is not presented as the exact locked export.
