# ATROPOS Code-Base Completion Report

Generated: 2026-08-03T12:10:43Z
Current Git HEAD: 84718cb6bd7d5adc536defd4a67acc512dcb92bf
Historical reconstruction HEAD: 7e612fcdba571b276a4ae65704835eb762030682 (nearest recoverable commit; exact locked export unavailable)

## Code-Base Obligation Set

Total binary obligations: 576
Current WRITTEN: 474
Current NOT_WRITTEN: 102
Current CODE-BASE COMPLETION: 82.29% (474/576)
Historical WRITTEN: 471
Historical NOT_WRITTEN: 105
Historical CODE-BASE COMPLETION: 81.77% (471/576)
Code-base delta: +0.52 percentage points

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
| 2 | 24 | 21 | 87.50% | 21 | +0.00 | 3 | G006-impl, G006-wire, G006-edge |
| 3 | 24 | 21 | 87.50% | 21 | +0.00 | 3 | H007-impl, H007-wire, H007-edge |
| 4 | 18 | 18 | 100.00% | 18 | +0.00 | 0 |  |
| 5 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 6 | 18 | 18 | 100.00% | 15 | +16.67 | 0 |  |
| 7 | 15 | 9 | 60.00% | 9 | +0.00 | 6 | B002-impl, B002-wire, B002-edge, B003-impl, B003-wire, B003-edge |
| 8 | 18 | 15 | 83.33% | 15 | +0.00 | 3 | D002-impl, D002-wire, D002-edge |
| 9 | 21 | 12 | 57.14% | 12 | +0.00 | 9 | F002-impl, F002-wire, F002-edge, F004-impl, F004-wire, F004-edge, F005-impl, F005-wire, F005-edge |
| 10 | 120 | 120 | 100.00% | 114 | +5.00 | 0 |  |
| 11 | 36 | 27 | 75.00% | 27 | +0.00 | 9 | J009-impl, J009-wire, J009-edge, J010-impl, J010-wire, J010-edge, J011-impl, J011-wire, J011-edge |
| 12 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 13 | 3 | 3 | 100.00% | 0 | +100.00 | 0 |  |
| 14 | 3 | 0 | 0.00% | 0 | +0.00 | 3 | BP-P14-hr-audit-impl, BP-P14-hr-audit-wire, BP-P14-hr-audit-edge |
| 15 | 3 | 3 | 100.00% | 3 | +0.00 | 0 |  |
| 16 | 3 | 0 | 0.00% | 0 | +0.00 | 3 | BP-P16-hierarchy-dispatch-impl, BP-P16-hierarchy-dispatch-wire, BP-P16-hierarchy-dispatch-edge |
| 17 | 54 | 30 | 55.56% | 30 | +0.00 | 24 | SD3-017-impl, SD3-017-wire, SD3-017-edge, SD3-018-impl, SD3-018-wire, SD3-018-edge, SD3-019-impl, SD3-019-wire, SD3-019-edge, SD3-020-impl ... |
| 18 | 42 | 39 | 92.86% | 39 | +0.00 | 3 | BP-P18-shared-platform-impl, BP-P18-shared-platform-wire, BP-P18-shared-platform-edge |
| 19 | 3 | 0 | 0.00% | 0 | +0.00 | 3 | BP-P19-app-factory-impl, BP-P19-app-factory-wire, BP-P19-app-factory-edge |
| 20 | 66 | 54 | 81.82% | 57 | -4.55 | 12 | E001-impl, E001-wire, E001-edge, E002-impl, E002-wire, E002-edge, P001-impl, P001-wire, P001-edge, SD3-071-impl ... |

## Checkpoints and Horizons

| Group | Phases | Total | Current written | Code % | Historical written | Delta pp |
|---|---|---:|---:|---:|---:|---:|
| Checkpoint 1 | 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 | 396 | 342 | 86.36% | 339 | +0.76 |
| Checkpoint 2 | 12, 13, 14, 15, 16 | 15 | 9 | 60.00% | 6 | +20.00 |
| Checkpoint 3 | 17, 18, 19 | 99 | 69 | 69.70% | 69 | +0.00 |
| Checkpoint 4 | 20 | 66 | 54 | 81.82% | 57 | -4.55 |
| Horizon I | 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 | 360 | 315 | 87.50% | 312 | +0.83 |
| Horizon II | 11, 12, 13, 14, 15, 16 | 51 | 36 | 70.59% | 33 | +5.88 |
| Horizon III | 17, 18 | 96 | 69 | 71.88% | 69 | +0.00 |
| Horizon IV | 19 | 3 | 0 | 0.00% | 0 | +0.00 |
| Horizon V | 20 | 66 | 54 | 81.82% | 57 | -4.55 |

## Required Named Surfaces

### Critical stubs and debt

- ConstraintSolverEvaluator: source atom D002 remains NOT_WRITTEN for semantic obligations.
- TreeSitterGrammarBridge: source atom B002 remains NOT_WRITTEN for semantic obligations.
- DirectorOrchestrator and WorkerCodeSynthesizer: source atoms J010/J011 remain NOT_WRITTEN for semantic obligations.
- Missing obligation IDs in the phase table are the authoritative debt list for this audit; file presence alone does not close a stub semantic obligation.

### HOE

HOE/UI obligations are represented by Source Doc 3 requirements 12-54 and 68-70, mapped to Phases 10, 17, and 18. Their binary counts are included in those phase rows; test and browser proof state is separate.

### App Factory

App Factory obligations are represented by Phase 19 blueprint additions and the relevant Source Doc 3 requirements. The report gives credit only where the exact required production owner paths exist; missing IDs remain NOT_WRITTEN.

### Phase 20

Phase 20 includes evaluation, restart, bounded learning, observability, safety, fallback, and crossover obligations. Installed self-host proof is operational evidence only and does not alter these code counts.

### Implementation surface breakdown

| Surface | Phase groups | Accounting treatment |
|---|---|---|
| Frontend/UI | 17-19 and SD3 UI requirements | Separate code obligations; browser/test proof excluded from code percentage |
| Backend/core | 0-16 | Canonical owner paths and missing semantic atoms determine code status |
| Database/source authority | 6, 9, 19-20 | Migration and source-coordinate obligations are counted only when mapped to a canonical owner |
| Platform/runtime | 0, 11, 18, 20 | Toolchain, self-host, platform, and recovery code obligations; packaging/install proof is separate |

## Historical and Scope Note

The former approximately 42% and 43.6% values mixed implementation, tests, compilation, packaging, installation, restart, deployment, Git cleanliness, and operator proofs. They remain immutable historical records in AGENTS.md and are superseded for future CODE-BASE COMPLETION reporting by this binary obligation method.

This is a conservative static code-base audit. Tests and operational evidence are separate. The nearest recoverable historical commit is not presented as the exact locked export.
