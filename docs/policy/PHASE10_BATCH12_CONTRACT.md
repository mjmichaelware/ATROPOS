# Batch 12 — exact source authority: HIG=0 becomes the only DLOI path

**Priority advanced:** #6 — exact source authority (DLOI + HIG=0).

## The gap

`HigZeroGuard` exists to guarantee that every exact-address lookup either
succeeds with the authoritative excerpt or fails with a typed
`DloiLookupResult.NoMatch` carrying a reason — "no blind cosine/RAG semantic
fallback, no nearest-neighbour guess, no fabricated source content".

**It has zero callers.** Every real DLOI site does its own ad-hoc failure
handling instead:

| Site | Today | Problem |
|---|---|---|
| `AgentRunService.kt:526` | `runCatching { … }.getOrNull()` | reason discarded; reports bare `"unresolved"` |
| `CommandRouter.kt:501` | `runCatching { … }` → `"dloi error: ${it.message}"` | raw exception text as UI |
| `CommandRouter.kt:510` | same | same |
| `DeterministicVerifier.kt:289` | `try/catch` → finding | correct outcome, reached by exception |
| `ProviderFailoverService.kt:14` | holds a `DloiService` | **never used** — dead field |

So the guard's contract is real, and nothing is bound by it. An unresolved
source is currently indistinguishable from a source that was never needed,
because the *reason* is thrown away at every site.

## What changes

`HigZeroGuard` becomes the single way DLOI resolution is reached. No caller
touches `DloiService.lookup`/`resolveTask` directly, so no caller can catch an
exception in its own way or drop the reason.

`AgentRunService` stops collapsing resolution to `String?`. A new atomic
`SourceEvidence` type carries either the provenance or the reason it could not
be resolved, so the final report says *why* rather than just `unresolved`.

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/agent/SourceEvidence.kt        (new, atomic)
src/main/kotlin/atropos/core/agent/AgentRunService.kt       (resolution + report)
src/main/kotlin/atropos/cli/CommandRouter.kt                (the two /dloi sites)
src/main/kotlin/atropos/core/verification/DeterministicVerifier.kt  (checkDloiAddress)
src/main/kotlin/atropos/core/autonomous/ProviderFailoverService.kt  (dead field)
src/test/kotlin/atropos/dloi/
```

## Acceptance criteria

1. No production file calls `DloiService.lookup` or `resolveTask` directly;
   every path goes through `HigZeroGuard`.
2. A failed lookup yields `NoMatch` with a non-blank reason — never an escaped
   exception.
3. A successful lookup still yields `Resolved` with the authoritative
   resolution; the guard adds no fallback or guess.
4. `AgentRunService` reports the reason a source was unresolved, not just that
   it was.
5. `DeterministicVerifier.checkDloiAddress` still produces its `dloi_address`
   finding for a bad address, now without relying on a thrown exception.
6. The unused `DloiService` in `ProviderFailoverService` is gone.

## No stub

`HigZeroGuard` already contained real logic; this batch gives it its first
callers and deletes the ad-hoc handling it was written to replace. Criterion 3
exists so routing through a guard cannot quietly become a place to invent a
fallback.

## Out of scope

Size debt (deferred by instruction) · priorities #4 and #2, which follow in that
order · SpecGraph.

## Auditor result — PASS (with a pre-existing failure reported)

Compile: one end-of-batch compile, clean.

| Class | Tests | Failures |
|---|---|---|
| `atropos.dloi.HigZeroGuardContractTest` (new) | 7 | 0 |

| # | Criterion | Evidence |
|---|---|---|
| 1 | no direct DLOI calls | grep for `.lookup(`/`.resolveTask(` outside `atropos/dloi` returns only `higZeroGuard.*` (plus unrelated `AstSymbolGraph.lookup` and `SecretSource.lookup`) |
| 2 | failures typed, never thrown | `an_unresolvable_address_is_a_typed_miss_not_an_exception`, `a_malformed_address_is_a_typed_miss_too`, `the_guard_never_throws_for_any_input` (6 hostile inputs × both entry points) |
| 3 | no fallback invented | `an_unresolvable_task_is_a_typed_miss_and_offers_no_substitute`; `renderDloi` states plainly that no nearest-match substitute is offered |
| 4 | reason reported, not just absence | `unresolved_source_evidence_carries_its_reason`, `resolved_source_evidence_reports_its_provenance_and_redacts` |
| 5 | `dloi_address` finding without a throw | `checkDloiAddress` now matches on `NoMatch` and uses `result.reason` as evidence |
| 6 | dead field gone | no `DloiService` reference in `ProviderFailoverService` |

`git diff --check`: clean.

## Root-cause finding: the source index is never built

The whole `atropos.dloi` test package fails **on baseline** — confirmed by
stashing this batch and re-running. Five distinct failures, one cause:

```
DloiService.loadDocuments() reads
  .atropos/context-cache/source-index/v1/extracted
if (!extractedRoot.exists()) return emptyList()
```

That is a **generated cache** under the gitignored `.atropos/` tree. On a fresh
clone it does not exist, so `loadDocuments()` returns empty and every lookup
fails with `unknown DLOI document`. `docs/ATROPOS_CANONICAL_PHASES_1_11_AUTHORITY.md`
is present and tracked — it has simply never been **ingested**.

So priority #6's deeper gap is not the guard: it is that **exact source
authority has no source registered**, and nothing in the default path builds the
index. Every DLOI resolution in a fresh checkout is a miss.

That is fail-closed, which is correct, and this batch makes those misses typed
and explained rather than thrown or swallowed. But it means source authority is
presently inert. Ingestion belongs to `DocumentIngestionService` and is a
distinct batch — recorded here rather than half-built.

The failing tests are pre-existing and not covered by the out-of-territory
repair protocol (they do not block test compilation), so they are reported, not
patched.
