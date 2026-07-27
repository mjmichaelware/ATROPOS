# ATROPOS Source Docs 1–2 Continuous Completion Goal

## Mission

Work continuously toward 100% completion of ATROPOS against Source Document 1 and Source Document 2 only.

Source Document 3 is excluded unless a minimal mechanism is strictly required to satisfy Source Docs 1–2.

## Controlling Documents

Read these first:

1. docs/completion/ATROPOS_SD1_SD2_SPECGRAPH_ATOM_DAG.md
2. ATROPOS_SD1_SD2_COMPLETION_LEDGER.md if it already exists

If the completion ledger does not exist, create it before editing production code.

## Required Outputs

Always keep these files updated in the repo root:

- ATROPOS_SD1_SD2_COMPLETION_LEDGER.md
- ATROPOS_SD1_SD2_NEXT_PASS.md
- ATROPOS_SD1_SD2_PROGRESS_REPORT.md
- ATROPOS_SD1_SD2_FINAL_ACCEPTANCE_REPORT.md when complete

## Continuous Work Loop

Repeat this loop until Source Docs 1–2 are 100% complete:

1. Read the DAG.
2. Read the current ledger.
3. Identify the highest-priority incomplete blocker atom.
4. Choose the smallest safe implementation pass.
5. State the pass scope.
6. Edit only files required for that pass.
7. Do not edit Source Document 3 features.
8. Do not use paid providers.
9. Do not expose secrets.
10. Run targeted tests.
11. Run compile.
12. Run smoke checks when applicable.
13. Run secret scan / redaction checks when applicable.
14. Run git diff --check.
15. Update ATROPOS_SD1_SD2_COMPLETION_LEDGER.md.
16. Update ATROPOS_SD1_SD2_PROGRESS_REPORT.md.
17. Update ATROPOS_SD1_SD2_NEXT_PASS.md.
18. Continue to the next blocker atom.

## Hard Stop Conditions

Stop immediately and report if:

- compile fails and repair is unclear
- tests fail and repair is unclear
- a secret appears in diff/output/logs
- a paid provider would be required
- Source Doc 3 work would be required beyond minimal dependency
- a destructive operation is needed
- repository state becomes confusing
- the pass would touch too many unrelated files

## Completion Definition

Source Docs 1–2 are complete only when:

- every DAG atom is DONE or explicitly non-required/deferred with justification
- every Kotlin file is classified
- no production stubs remain
- no fake commands remain
- no artificial boilerplate remains
- no unreachable advertised features remain
- provider grid behavior is tested
- quota route policy is tested
- no paid providers are used accidentally
- no secrets are exposed
- compile passes
- smoke checks pass
- git diff --check passes
- final acceptance report exists

## First Required Task

If ATROPOS_SD1_SD2_COMPLETION_LEDGER.md does not exist, create it first by auditing all 94 DAG atoms and every Kotlin file.

After the ledger exists, begin the continuous loop above.
