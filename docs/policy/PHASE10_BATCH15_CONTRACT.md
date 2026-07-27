# Batch 15 — exact source authority becomes real runtime law

**Priority completed:** #6 — exact source authority (DLOI + HIG=0) as real runtime law.

## The gap (from PHASE10_PRIORITY6_HARDSTOP.md)

> `DloiService.loadDocuments()` reads
> `.atropos/context-cache/source-index/v1/extracted`. **Nothing in the tree
> writes it** — that path appears exactly once in `src/main/kotlin`, at the
> read site.

The source authority documents were committed to `docs/source/` but the
runtime had no mechanism to index them into the shape `DloiService` reads.
Every DLOI resolution in a fresh checkout was a miss.

## What changed

### SourceAuthorityIndexer (new)

The missing writer. Walks `docs/source/`, computes SHA-256 of each authority
file, detects section headings (Phase N:, numbered items, markdown #), and
produces the exact JSON index shape `DloiService.loadIndexedDocument` already
parses. No hashes fabricated — every `source_id` is the real first-16-hex
of SHA-256 of the file bytes.

### SourceAuthorityLaw (new)

The runtime law enforcement. Provides:
1. **Hash-pinned verification**: checks that every indexed document's
   `source_id` matches the SHA-256 prefix of the actual file in `docs/source/`.
2. **Index freshness**: detects unindexed authority files.
3. **Guarded lookup**: composes with HigZeroGuard to refuse lookups against
   unverified sources.

### DloiService.loadDocuments() fallback

When the extracted index cache doesn't exist but `docs/source/` does,
`loadDocuments()` now invokes `SourceAuthorityIndexer` to build the index
from the real authority files. This is the exact gap the hardstop identified.

## Hash verification result

```
sha256sum docs/source/ATROPOS_CODEX_CLI_BUILD_BLUEPRINT_OVER_TIME.txt | cut -c1-16
97cff09c0f362337
```

The test assertion `assertEquals("97cff09c0f362337", result.document.sourceId)`
is a **truthful exact match** against the real file's SHA-256.

## Territory (files changed)

```
src/main/kotlin/atropos/dloi/SourceAuthorityIndexer.kt   (new)
src/main/kotlin/atropos/dloi/SourceAuthorityLaw.kt        (new)
src/main/kotlin/atropos/dloi/DloiService.kt               (loadDocuments fallback)
src/test/kotlin/atropos/dloi/SourceAuthorityIndexerTest.kt (new)
src/test/kotlin/atropos/dloi/SourceAuthorityLawTest.kt     (new)
```

## Protected priorities (not touched)

| # | Priority | Status |
|---|---|---|
| 1 | Preventive territory at dispatch + Director drift | Protected — no touch |
| 2 | Context attestation + typed drift failures | Protected — no touch |
| 3 | Externally bounded agency | Protected — no touch |
| 4 | Independent Auditor | Protected — no touch |
| 5 | Deterministic failure-first verification | Protected — no touch |
| 7 | Free-first route law + paid-provider prohibition | Protected — no touch |
| 8 | Automatic restart continuity | Protected — no touch |

## No SpecGraph touch

None.

## Acceptance criteria

1. `SourceAuthorityIndexer` produces index JSON from real `docs/source/` files
2. `SourceAuthorityLaw.verify()` confirms hash-pinned integrity
3. `DloiService.loadDocuments()` falls back to indexer when cache is empty
4. Existing DLOI tests pass with real source-id `97cff09c0f362337`
5. No fabricated hashes — all IDs derived from real SHA-256
6. HIG=0 maintained: unverifiable sources produce typed NoMatch
