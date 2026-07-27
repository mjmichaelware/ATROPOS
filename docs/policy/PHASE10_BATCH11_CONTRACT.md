# Batch 11 — the deterministic verifier reports instead of crashing

**Priority advanced:** #5 — deterministic failure-first verification.

## The defect

`DeterministicVerifier.verify` records the `source_scope` finding correctly when
a path lies outside the repository — and then carries on analysing that same
file:

```kotlin
sourcePaths.forEach { path ->
    findings += checkSourceScope(path)          // correctly reports out-of-scope
    if (path.extension == "kt" && Files.isRegularFile(path)) {
        ...
        findings += checkImportReconciliation(path)   // ← analyses it anyway
    }
}
```

`checkImportReconciliation` reaches `AstSymbolGraph.reconcileImports`, which
does `require(fileSymbols.isNotEmpty()) { "unknown Kotlin source: $path" }`. The
graph only indexes files under `repoRoot`, so an out-of-scope file is never in
it, and the requirement throws.

A verifier whose whole purpose is failure-first reporting therefore **crashes
instead of reporting** on exactly the input it just diagnosed. This is the
pre-existing failure recorded in Batch 10:

```
DeterministicVerifierTest > catches_out_of_scope_sources_before_model_review
java.lang.IllegalArgumentException: unknown Kotlin source: ../atropos-deterministic-outside-…/Out.kt
```

## The fix

A path that failed `source_scope` is not analysed further. Nothing can be
concluded about a file the symbol graph does not contain, and attempting it is
what turns a finding into an exception. The finding still stands, so the result
still fails — this removes a crash, not a refusal.

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/verification/DeterministicVerifier.kt
src/test/kotlin/atropos/core/verification/DeterministicVerifierTest.kt
```

## Acceptance criteria

1. `catches_out_of_scope_sources_before_model_review` passes: an out-of-scope
   source yields a `source_scope` finding and `passed == false`, with no throw.
2. A batch mixing in-scope and out-of-scope paths reports findings for both —
   one bad path does not abort verification of the rest.
3. In-scope files are still analysed exactly as before; no check is skipped for
   a path that is in scope.
4. Skipping analysis never makes a result pass — the `source_scope` finding
   remains an error.

## No stub

This removes an exception path, it does not add a permissive one. Criterion 4
exists so "skip the checks" cannot become "the file is fine".

## Territory amendment (declared mid-batch)

`AuditorService.kt` and `VerifiedCompletionGate.kt` were added to territory.

Fixing the crash surfaced a latent misconfiguration it had been hiding:
`AuditorService.auditDeterministic` constructed `DeterministicVerifier()` with
the **default** root — the process working directory — rather than the root of
the files it was auditing. Every audited file outside the cwd was therefore out
of scope. Previously that produced a throw, caught and downgraded to a
`WARNING "unable to verify"`, so the misconfiguration was invisible. With the
crash removed it correctly surfaced as a blocking `source_scope` failure, which
is how two Batch 10 tests caught it.

`AuditorService` now takes a `repoRoot` and passes it to the verifier;
`VerifiedCompletionGate` supplies its own. Fixing the symptom in the tests would
have re-buried a real bug.

## Auditor result — PASS

Compile: one end-of-batch compile, clean.

| Class | Tests | Failures |
|---|---|---|
| `atropos.core.verification.DeterministicVerifierTest` | 5 | 0 |
| `atropos.core.verification.VerifiedCompletionGateTest` | 10 | 0 |
| **total** | **15** | **0** |

| # | Criterion | Evidence |
|---|---|---|
| 1 | pre-existing failure fixed | `catches_out_of_scope_sources_before_model_review` passes; it failed on baseline in Batch 10 |
| 2 | one bad path does not abort the rest | `one_out_of_scope_path_does_not_abort_verification_of_the_rest` |
| 3 | in-scope files still analysed | same test asserts a non-`source_scope` finding from the in-scope file; the two pre-existing verifier tests are unchanged and pass |
| 4 | skipping never grants a pass | `skipping_analysis_never_makes_an_out_of_scope_result_pass` |

`git diff --check`: clean.

### No stub

An exception path was removed, not a refusal. Criterion 4 exists so "skip the
analysis" cannot read as "the file is fine", and the amendment above replaced a
swallowed warning with a real verdict.
