# Priority #6 cannot be completed in-repo — HARD STOP on ingestion

Batch 12 made every DLOI failure typed and explained. Finishing #6 means making
resolution actually *succeed*, which requires the source index. That is blocked,
and not by anything a batch can build.

## Evidence

`DloiService.loadDocuments()` reads
`.atropos/context-cache/source-index/v1/extracted`. **Nothing in the tree writes
it** — that path appears exactly once in `src/main/kotlin`, at the read site.
`DocumentIngestionService` is a different pipeline and does not produce this
format. DLOI has a reader with no writer.

Building the writer is bounded and I was ready to. The blocker is what it would
have to index.

`DloiServiceTest` pins an exact identity:

```kotlin
assertEquals("97cff09c0f362337", result.document.sourceId)
assertEquals("S0003", result.coordinate.sectionId)   // "authority#phase_1"
```

That id is not derivable from the tracked authority document. Checked against
`docs/ATROPOS_CANONICAL_PHASES_1_11_AUTHORITY.md`:

| scheme | first 16 hex |
|---|---|
| sha256(bytes) | `454657a2d73323f2` |
| sha1(bytes) | `625433e61c817a59` |
| md5(bytes) | `c0903da636325a96` |
| sha256(filename) | `e756f81042b90f0e` |
| sha256(stem) | `d5fa201017642747` |
| **expected** | **`97cff09c0f362337`** |

None match. The alias table explains why:

```kotlin
normalized.contains("canonical_phases_1_11_authority") ||
normalized.contains("codex_cli_build_blueprint_over_time") -> listOf("authority", …)
```

`authority` also aliases **`codex_cli_build_blueprint_over_time`** — a source
document that is not in this repository. `DloiTaskResolverTest` likewise expects
a `closure_source` document. The index those tests were written against was
built from the original source documents (SD1/SD2), which are untracked.

## Why I stopped rather than proceeding

Two paths were available and both are barred:

1. **Fabricate an index** from the tracked markdown. It would produce a
   different `source_id`, so the tests would still fail — and a synthesised
   index presented as the authority is precisely the fabricated source content
   HIG=0 exists to prevent.
2. **Change the assertions** to match whatever I generated. Those assertions
   *are* the source-authority contract. Rewriting them to fit my output would
   turn a red suite green while making source authority meaningless.

## What is needed

The authoritative source documents themselves — the SD1/SD2 originals the index
was built from — committed or made reachable. With those present, the indexer is
a small batch: walk the sources, emit the JSON shape `loadIndexedDocument`
already parses, and the five red `atropos.dloi` tests go green on their existing
assertions.

Until then source authority is **inert but fail-closed**: every lookup is a
typed, explained miss, and nothing is invented to fill the gap.
