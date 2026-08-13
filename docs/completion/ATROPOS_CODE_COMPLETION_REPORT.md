# ATROPOS Code-Base Completion Report

Generated: 2026-08-13T07:48:10Z
Current Git HEAD: 1cc96bc
Audit merged from: docs/ATROPOS_UNIMPLEMENTED_LIST.md

## Why this number moved down

The previous report read 100.00% (741/741). The numerator was not wrong; the
denominator was incomplete. A tree audit on 2026-08-13 found 279 obligations
that had never been registered, and a spot check of 42 of them found 35 absent
from the registry entirely -- ExecutionEvent, ProvenanceStream, MarkdownExporter,
ExecutionHistoryStore, AtroposMetrics, BenchmarkRunner, SelfImprovementLoop,
PolicyGate, DeploymentService, ast_symbol_graph, LOCAL_TOOLCHAIN, the eleven
named fallback chains, Dockerfile, GraalVM and Ktor among them. A register that
does not contain the work cannot report on it, and 100% against an incomplete
register is the fake-success outcome AGENTS.md 0.6 forbids.

Six obligations were additionally flipped from WRITTEN to NOT_WRITTEN under the
accounting spec's own rule -- "one broad file cannot silently satisfy a separate
named atomic owner". A001 (SourceDocumentRegistry) rested on DloiService.kt and
C005 (TermuxPathResolver) on build.gradle.kts; neither named symbol exists as a
production symbol. See ATROPOS_CODE_COMPLETION_AMENDMENTS.md.

## Code-Base Obligation Set

Total binary obligations: 1020
Current WRITTEN: 745
Current NOT_WRITTEN: 275
Current CODE-BASE COMPLETION: 73.04% (745/1020)

Previous reported completion: 100.00% (741/741)
Previous denominator was short by 279 obligations.

## By phase

| Phase | Completion | Written/Total |
|---|---|---|
| 0 | 94.90% | 93/98 |
| 1 | 100.00% | 3/3 |
| 2 | 100.00% | 24/24 |
| 3 | 47.06% | 24/51 |
| 4 | 100.00% | 18/18 |
| 5 | 100.00% | 3/3 |
| 6 | 83.33% | 15/18 |
| 7 | 34.78% | 16/46 |
| 8 | 84.00% | 21/25 |
| 9 | 100.00% | 21/21 |
| 10 | 96.00% | 120/125 |
| 11 | 74.00% | 37/50 |
| 12 | 100.00% | 3/3 |
| 13 | 100.00% | 3/3 |
| 14 | 100.00% | 3/3 |
| 15 | 100.00% | 3/3 |
| 16 | 100.00% | 3/3 |
| 17 | 100.00% | 90/90 |
| 18 | 65.03% | 93/143 |
| 19 | 69.47% | 66/95 |
| 20 | 44.10% | 86/195 |

## Reading these numbers

Phases at 100% with small denominators (1, 5, 12-16) are phases whose audited
obligations were already registered; they are not evidence that the phase is
finished, only that nothing new was found unregistered for them. Phase 20 at
44.10% and Phase 7 at 34.78% carry the largest
absolute gaps and are where the remaining work concentrates.

The denominator will keep growing as further audits register obligations that
were never written down. A falling percentage after an audit is the register
becoming honest, not the codebase regressing.
