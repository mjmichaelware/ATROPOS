# ATROPOS Code-Base Completion Report

Generated: 2026-08-12T06:06:20Z
Current Git HEAD: f78433ffc3b374f215b2c76715e5d9b37b26edba
Historical reconstruction HEAD: 7e612fcdba571b276a4ae65704835eb762030682 (nearest recoverable commit; exact locked export unavailable)

## Code-Base Obligation Set

Total binary obligations: 609
Current WRITTEN: 582
Current NOT_WRITTEN: 27
Current CODE-BASE COMPLETION: 95.57% (582/609)
Historical WRITTEN: 518
Historical NOT_WRITTEN: 91
Historical CODE-BASE COMPLETION: 85.06% (518/609)
Code-base delta: +10.51 percentage points

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
| 8 | 18 | 18 | 100.00% | 18 | +0.00 | 0 |  |
| 9 | 21 | 21 | 100.00% | 16 | +23.81 | 0 |  |
| 10 | 120 | 120 | 100.00% | 114 | +5.00 | 0 |  |
| 11 | 36 | 36 | 100.00% | 31 | +13.89 | 0 |  |
| 12 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 13 | 3 | 3 | 100.00% | 0 | +100.00 | 0 |  |
| 14 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 15 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 16 | 3 | 3 | 100.00% | 0 | +100.00 | 0 |  |
| 17 | 54 | 29 | 53.70% | 30 | -1.85 | 25 | SD3-017-impl, SD3-017-wire, SD3-017-edge, SD3-018-impl, SD3-018-wire, SD3-018-edge, SD3-019-impl, SD3-019-wire, SD3-019-edge, SD3-020-impl ... |
| 18 | 42 | 42 | 100.00% | 39 | +7.14 | 0 |  |
| 19 | 36 | 34 | 94.44% | 18 | +44.44 | 2 | BP-P19-frontend-generation-wire, BP-P19-frontend-generation-edge |
| 20 | 66 | 66 | 100.00% | 54 | +18.18 | 0 |  |

## Checkpoints and Horizons

| Group | Phases | Total | Current written | Code % | Historical written | Delta pp |
|---|---|---:|---:|---:|---:|---:|
| Checkpoint 1 | 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 | 396 | 396 | 100.00% | 368 | +7.07 |
| Checkpoint 2 | 12, 13, 14, 15, 16 | 15 | 15 | 100.00% | 9 | +40.00 |
| Checkpoint 3 | 17, 18, 19 | 132 | 105 | 79.55% | 87 | +13.64 |
| Checkpoint 4 | 20 | 66 | 66 | 100.00% | 54 | +18.18 |
| Horizon I | 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 | 360 | 360 | 100.00% | 337 | +6.39 |
| Horizon II | 11, 12, 13, 14, 15, 16 | 51 | 51 | 100.00% | 40 | +21.57 |
| Horizon III | 17, 18 | 96 | 71 | 73.96% | 69 | +2.08 |
| Horizon IV | 19 | 36 | 34 | 94.44% | 18 | +44.44 |
| Horizon V | 20 | 66 | 66 | 100.00% | 54 | +18.18 |

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
