# Code-Base Completion Accounting Amendments

## 2026-08-12T12:18:41Z
- Code-base accounting schema advanced to atropos-codebase-accounting-v2.
- Added Source Doc 4 and all three PDF gap-map hashes and byte counts to the source inventory.
- Added only three unique Source Doc 4 acceptance obligations; Core, HOE, and Phase 20 map atoms are crosswalked to existing obligations without duplicate credit.
- Added the strict absent-atom audit as a separate hashed evidence inventory and counted its 44 non-retired, non-test atomic-owner obligations without granting broad-owner credit.
- Exact locked 2026-07-29 export fingerprint unavailable; retain reconstruction warning.

## 2026-08-13T07:48:32Z — audit merge and two contested obligations

**Merged:** 279 obligations from `docs/ATROPOS_UNIMPLEMENTED_LIST.md`, audited
against the tree at `1cc96bc`. Denominator 741 -> 1020. Reported completion
100.00% -> 73.04%.

The register did not contain the work. A spot check of 42 audited items found 35
with no obligation of any kind, so the previous 100% was measured against a set
that excluded most of what remained. Nine of the merged obligations are recorded
WRITTEN because this session closed them (items 1, 90-97).

**Contested and flipped to NOT_WRITTEN**, under the accounting spec's rule that
"one broad file cannot silently satisfy a separate named atomic owner":

| Obligation | Named owner | Credit rested on | Finding |
|---|---|---|---|
| A001-impl / -wire / -edge | `SourceDocumentRegistry` | `src/main/kotlin/atropos/dloi/DloiService.kt` | symbol absent from the tree |
| C005-impl / -wire / -edge | `TermuxPathResolver` | `build.gradle.kts` | symbol absent from the tree |

Neither flip overrides authority. Both apply the accounting spec to evidence the
spec already required, and both are reversible by producing the named symbol.

