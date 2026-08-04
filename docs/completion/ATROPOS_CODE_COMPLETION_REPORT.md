# ATROPOS Code-Base Completion Report

Generated: 2026-08-04T01:22:27Z
Current Git HEAD: 997e6b5732b977b30e1d97076bf5156b4b64a991
Historical reconstruction HEAD: 7e612fcdba571b276a4ae65704835eb762030682 (nearest recoverable commit; exact locked export unavailable)

## Code-Base Obligation Set

Total binary obligations: 609
Current WRITTEN: 496
Current NOT_WRITTEN: 113
Current CODE-BASE COMPLETION: 81.44% (496/609)
Historical WRITTEN: 492
Historical NOT_WRITTEN: 117
Historical CODE-BASE COMPLETION: 80.79% (492/609)
Code-base delta: +0.66 percentage points

The denominator is binary implementation obligations directly juxtaposed with the current codebase. SHA-256 values prove authority identity; document bytes are provenance telemetry, not completion weights. Gap-map atoms are crosswalked to existing requirements and receive no duplicate credit.

## Authority Coverage

Hashed authority documents: 19
Source Doc 4 unique obligations added: 9
Core, HOE, and Phase 20 PDF atoms are registered in `authorityCrosswalk` and mapped to existing obligations unless explicitly unique.

## Phase Accounting

| Phase | Total | Current written | Current code % | Historical written | Delta pp | Not written | Missing IDs |
|---:|---:|---:|---:|---:|---:|---:|---|
| 0 | 96 | 75 | 78.12% | 81 | -6.25 | 21 | M003-impl, M003-wire, M003-edge, M006-impl, M006-wire, M006-edge, N001-impl, N001-wire, N001-edge, N002-impl ... |
| 1 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 2 | 24 | 20 | 83.33% | 21 | -4.17 | 4 | G006-impl, G006-wire, G006-edge, BP-P02-transport-outcomes-edge |
| 3 | 24 | 21 | 87.50% | 21 | +0.00 | 3 | H007-impl, H007-wire, H007-edge |
| 4 | 18 | 18 | 100.00% | 18 | +0.00 | 0 |  |
| 5 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 6 | 18 | 17 | 94.44% | 15 | +11.11 | 1 | A005-edge |
| 7 | 15 | 9 | 60.00% | 9 | +0.00 | 6 | B002-impl, B002-wire, B002-edge, B003-impl, B003-wire, B003-edge |
| 8 | 18 | 15 | 83.33% | 15 | +0.00 | 3 | D002-impl, D002-wire, D002-edge |
| 9 | 21 | 12 | 57.14% | 12 | +0.00 | 9 | F002-impl, F002-wire, F002-edge, F004-impl, F004-wire, F004-edge, F005-impl, F005-wire, F005-edge |
| 10 | 120 | 118 | 98.33% | 114 | +3.33 | 2 | SD3-001-edge, SD3-004-edge |
| 11 | 36 | 30 | 83.33% | 30 | +0.00 | 6 | J010-impl, J010-wire, J010-edge, J011-impl, J011-wire, J011-edge |
| 12 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 13 | 3 | 3 | 100.00% | 0 | +100.00 | 0 |  |
| 14 | 3 | 0 | 0.00% | 0 | +0.00 | 3 | BP-P14-hr-audit-impl, BP-P14-hr-audit-wire, BP-P14-hr-audit-edge |
| 15 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 16 | 3 | 0 | 0.00% | 0 | +0.00 | 3 | BP-P16-hierarchy-dispatch-impl, BP-P16-hierarchy-dispatch-wire, BP-P16-hierarchy-dispatch-edge |
| 17 | 54 | 29 | 53.70% | 30 | -1.85 | 25 | SD3-017-impl, SD3-017-wire, SD3-017-edge, SD3-018-impl, SD3-018-wire, SD3-018-edge, SD3-019-impl, SD3-019-wire, SD3-019-edge, SD3-020-impl ... |
| 18 | 42 | 39 | 92.86% | 39 | +0.00 | 3 | BP-P18-shared-platform-impl, BP-P18-shared-platform-wire, BP-P18-shared-platform-edge |
| 19 | 36 | 25 | 69.44% | 18 | +19.44 | 11 | BP-P19-frontend-generation-wire, BP-P19-frontend-generation-edge, BP-P19-database-security-impl, BP-P19-database-security-wire, BP-P19-database-security-edge, BP-P19-auth-impl, BP-P19-auth-wire, BP-P19-auth-edge, BP-P19-backend-integrations-impl, BP-P19-backend-integrations-wire ... |
| 20 | 66 | 53 | 80.30% | 57 | -6.06 | 13 | E001-impl, E001-wire, E001-edge, E002-impl, E002-wire, E002-edge, P001-impl, P001-wire, P001-edge, SD3-071-impl ... |

## Checkpoints and Horizons

| Group | Phases | Total | Current written | Code % | Historical written | Delta pp |
|---|---|---:|---:|---:|---:|---:|
| Checkpoint 1 | 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 | 396 | 341 | 86.11% | 342 | -0.25 |
| Checkpoint 2 | 12, 13, 14, 15, 16 | 15 | 9 | 60.00% | 6 | +20.00 |
| Checkpoint 3 | 17, 18, 19 | 132 | 93 | 70.45% | 87 | +4.55 |
| Checkpoint 4 | 20 | 66 | 53 | 80.30% | 57 | -6.06 |
| Horizon I | 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 | 360 | 311 | 86.39% | 312 | -0.28 |
| Horizon II | 11, 12, 13, 14, 15, 16 | 51 | 39 | 76.47% | 36 | +5.88 |
| Horizon III | 17, 18 | 96 | 68 | 70.83% | 69 | -1.04 |
| Horizon IV | 19 | 36 | 25 | 69.44% | 18 | +19.44 |
| Horizon V | 20 | 66 | 53 | 80.30% | 57 | -6.06 |

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
