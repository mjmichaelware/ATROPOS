# ATROPOS SD1-SD2 Next Pass

Created: 2026-07-20
Source: ATROPOS_SD1_SD2_COMPLETION_LEDGER.md

## Selected Blocker Atom

**A004 HIGZeroGuard** - No guard against blind RAG fallback when exact source address is required

## Why This Atom

A004 is the highest-priority incomplete blocker because:
- It is a root DAG atom under A_SOURCE_AUTHORITY_DLOI
- Source Document 1 section 1.0.1 explicitly requires it
- Without HIG=0 (Hallucination In Guard=0), the system can return guessed answers instead of typed NoMatch
- It blocks proper exact-source addressing (A003) and traceability (A005)

## Pass Scope

Implement HIGZeroGuard — a simple guard class that:
1. Prevents blind cosine/RAG fallback when exact address is required
2. Returns typed `NoMatch` result when resolution fails
3. Is wired into DloiService.resolve()
4. Has deterministic tests proving no-match returns NoMatch, not guessed content

## Files to Edit

- `src/main/kotlin/atropos/dloi/HigZeroGuard.kt` (NEW) - Guard class
- `src/main/kotlin/atropos/dloi/DloiService.kt` (EDIT) - Wire guard into resolve()
- `src/test/kotlin/atropos/dloi/HigZeroGuardTest.kt` (NEW) - Deterministic tests

## Files NOT to Edit

- No Source Document 3 features
- No paid provider code
- No UI/UX files
- No unrelated provider/adapter code

## Verification

1. `./gradlew compileKotlin` - must pass
2. `./gradlew test` - run HIG zero guard tests
3. `git diff --check` - no whitespace errors
4. Secret scan - no raw secrets in new files

## Continue Criteria

After this pass completes:
- Update ATROPOS_SD1_SD2_COMPLETION_LEDGER.md - mark A004 as DONE or PARTIAL
- Update ATROPOS_SD1_SD2_PROGRESS_REPORT.md
- Select next blocker atom from ledger
