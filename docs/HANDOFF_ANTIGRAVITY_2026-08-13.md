# ATROPOS — handoff to Antigravity

**Repo:** `mjmichaelware/ATROPOS` · **Branch:** `main` at `85ece6d` (clean, pushed)
**Predecessor:** Claude (claude-opus-5), 7 batches, 2026-08-13

---

## Read these three files first, in this order. Nothing else.

1. `AGENTS.md` — §0 hard rules, §3 open priorities, §4 update protocol. 817 ledger rows; the last 7 are mine. **Append one row per batch. Do not rewrite §1 baselines.**
2. `docs/ATROPOS_UNIMPLEMENTED_LIST.md` — the numbered work list. 280 items, statuses `ABSENT` / `PARTIAL` / `ORPHAN` / `UNENFORCED` / `DEFECT` / `DONE`. §0 lists 17 **superseded** items — do not implement those.
3. `docs/completion/ATROPOS_CODE_OBLIGATION_REGISTRY.json` — 1020 binary obligations. Audit items are `AUD<nnn>`, matching the list numbers. **Flip an obligation to `WRITTEN` only when its named symbol exists and has a production caller and a test.**

Do **not** re-ingest the source documents. Everything actionable from them is already atomized into the list. Opening them again costs a large fraction of your context and produces nothing new.

---

## State

| | |
|---|---:|
| Kotlin (`src/main` + `app/src/main`) | 81,696 |
| Python (`apps/`) | 80,592 |
| TypeScript (`apps/`) | 35,532 |
| Obligations WRITTEN | 813 / 1020 = **79.71%** |
| Tests | 1,758 passing |
| Open audit items | **201** |

**Targets:** Kotlin 100,000 · Python 50,000 (already exceeded) · TypeScript 50,000.
Kotlin needs **+18,304**. TypeScript needs **+14,468**.

### Six environmental test failures are expected

`AppProjectGeneratorTest` — 6 failures, all `kotlinc is required for generated-test verification`. They fail because `kotlinc` is not on PATH in a container. **They are not yours and were failing before this session.** On a Termux device with `kotlinc` installed they pass. Do not "fix" them by weakening the generator.

---

## What I closed (do not redo)

| Items | Subsystem | Commit |
|---|---|---|
| 1 | `JsonStringField` — StackOverflow in 9 JSON parsers | `877230d` |
| 90–93 | Execution provenance: `ExecutionEvent`, `ProvenanceStream`, `EventPublisher`, `EventSubscriber` | `1c14714` |
| 94–97 | Copyable cards + full-run export: `OutputCard`, `CardRenderer`, `RunExport`, `MarkdownExporter`, `JsonExporter` | `1cc96bc` |
| 98–100 | Searchable history: `HistoryQuery`, `HistoryIndex`, `ExecutionHistoryStore` | `dc203a6` |
| 64–89 | Metric catalogue, `EvidenceStore`, five-way release classification, benchmarks, dashboard | `9dcc88d`, `76daeda` |
| 173, L01–L15, H01–H06, NS02–NS05 | Phase 20 self-improvement loop assembled | `85ece6d` |

**Source Doc 3 §4 (evaluation) and §5 (observability) are both closed.**

### Reusable seams you should compose over, not duplicate

- `atropos.core.observability.EventPublisher` — the only way an execution event enters the system. Writes journal first, stream second. Redaction runs here once.
- `atropos.core.observability.ExecutionHistoryStore` — byte-offset-indexed query over run journals. Do not walk journals yourself.
- `atropos.core.evaluation.EvidenceStore` — content-addressed, immutable, redacts before hashing. **This is your CAS substrate for `P20-LH01…LH07`.**
- `atropos.core.evaluation.MetricNormalizer` — the single owner of what "better" means. Direction-aware. Never re-derive.
- `atropos.core.phase20.SelfImprovementLoop` — returns a hash-pinned contract; **never** calls an executor. Phase 11 is the only component permitted to mutate ATROPOS source (law 20.10).

---

## Work order — highest lines-of-code per hour first

### 1. `P20-LH01 … LH07` — lakehouse ledgers (items 211–217, 7 obligations)

The biggest remaining Kotlin block with a substrate already built. Evidence, memory, proposal and amendment ledgers as CAS objects with structural manifests.

`EvidenceStore` already gives you content addressing. What is missing is the **structural manifest** discipline from `P20-LH05`: every object carries region-type + byte-offset + parent-hash. Region types are fixed: `header | subheader | prose | code | list | table | warning | example | anti-pattern`.

Suggested files, one responsibility each:
`StructuralManifest.kt` · `ManifestRegion.kt` · `ManifestBuilder.kt` · `EvidenceLedger.kt` · `MemoryLedger.kt` · `ProposalStore.kt` · `AmendmentRegistry.kt` · `LakehouseRetrieve.kt`

**Hard rule from `P20-LH04`:** the amendment registry is append-only and must never overwrite an original Source Doc hash.

### 2. `P20-G01 … G09` — governance candidates (items 218–226, 9 obligations)

Each is a detector that turns a runtime condition into a `RuntimeObservation`. The type already exists; you are writing the nine detectors and wiring them to `SelfImprovementLoop.advance`.

**`P20-G01` is the named first canonical amendment: nonzero compile/test exit forbids `VERIFIED`.** Do that one first.

### 3. Items 252–256 — wire the five orphans

`LivePreviewService` (0 callers, `hotReload()` never invoked) · `VisualComparison` (0 callers, 0 tests) · `EvidenceCollector` (0 callers) · `McpTerritoryBridge` (no server registers it) · `CapabilityEnforcer` (1 caller, **0 tests**).

Cheap, already written, and converts five obligations from false-green to true. **Verify a production caller exists outside the defining file before flipping the obligation.**

### 4. Item 163 — bridge endpoints, then TypeScript

`POST /session` · `GET /session` · `GET /session/:id` · `POST /message` · `GET /events` (SSE) · `POST /approve` · `POST /reject` · `GET /evidence/:id` · `GET /files`

`src/main/kotlin/atropos/bridge/` already has handlers for most of these shapes. **This unblocks both Web and Android**, which is the only route to the TypeScript target.

Note item **171**: `apps/atropos-web/` **does not exist**. Every `HOE-C` atom points at a directory not in the tree. The live app is `apps/web/`. Resolve that ownership boundary before writing web code, or ask.

Note item **167**: `POST /cli` currently takes unrestricted argv. That is an RCE surface. Fix it when you touch the bridge.

### 5. Items 101–107 — intent layer

`ActionRegistry` · `AliasResolver` · `CommandConsolidator` · `CommandMetadata` · the 13 canonical verbs as a contract surface. Compose over the existing `CommandRegistry` (313 L) and `CanonicalVerb.kt`.

### Do not start yet

Items **28–36** (KMP, Docker, GraalVM, Ktor, Compose Desktop, iOS) are real but are a build-system project, not a code project. They will consume days and add few lines. Leave them until the engine work is done.

---

## Rules that cost me time to learn

**Every defect I hit had the same shape: it succeeded loudly and was wrong quietly.** Four in seven batches:

1. A regex `(?:\\.|[^"\\])*` recursed once per character → `StackOverflowError` on long fields → **both memory channels dead, reported as soft-fail, `confidence=100` still printed.**
2. Unit/record separators as sentinels → `EventJournalService` compacts payloads, compaction ends in `trim()`, and **Java's `Character.isWhitespace` is true for U+001C–U+001F** → every event silently decoded as legacy.
3. `HistoryIndex.load()` used `mapNotNull` → a corrupt index returned an **empty history** instead of rebuilding.
4. `BenchmarkRunner.decode()` used `trim()` → ate the trailing tab of an empty final field → **every recorded benchmark discarded.**

So: **test with inputs large enough and empty enough to break you.** A test using short strings and populated fields passes against all four of those.

**Assert on cost, not just on result.** The §5.3 clause "without loading the entire trace into memory" is trivially faked by reading everything and filtering. `ExecutionHistoryStoreTest` asserts *400 index entries scanned, 100 journal lines read*. Do the same for any requirement phrased as a limit.

**An absent measurement is not zero.** A subsystem that never ran and one that ran and failed everything produce the same number under naive counting. `AtroposMetric.unmeasured` exists for that. Never report `0.0` for something you did not measure.

**Force colour on in UI tests.** A test JVM has no console, so `ConfigurationManager.isColorEnabled` is `false` and every colour assertion passes against uncoloured text. Construct with `hasConsole = true`.

---

## Per-batch protocol (AGENTS.md §4)

1. One coherent batch, 3–8 tightly related files.
2. `./gradlew compileKotlin` then `./gradlew test --tests '*YourTest*'`.
3. Full suite before commit: `./gradlew test` — expect exactly 6 `AppProjectGeneratorTest` failures, nothing else.
4. Append an `AGENTS.md` ledger row: paths + line deltas, atom IDs, **which predicate moved from false to true**, % delta, justification.
5. Update `ATROPOS_CODE_OBLIGATION_REGISTRY.json` for the obligations you closed.
6. Commit with `git commit -F -` and a heredoc — **not** `-m`, because quoted phrases inside a `-m` string break the shell.
7. `git push -u origin claude/<your-branch>`, then fast-forward `main` and push.
8. **Immediately start the next atom.** Do not stop for confirmation (§0.3).

**Never report a phase complete unless its Blueprint acceptance gate is met and the evidence is in the repo** (§0.6, §4.6). A nonzero compile or test exit may never be reported as `VERIFIED`.

---

## Two open questions I did not guess at

Marked `CONFIRM` at the bottom of the list file:

1. **Items 17–19** — does the Source Doc 1 delegation topology (Grok=Director, Groq=Worker, Ollama=Validator) survive Phase 16's Manager/Specialist/Worker hierarchy, or is it superseded with `S16`? The *local validator before cloud spend* is worth keeping either way.
2. **Items 6–7** — `ast_symbol_graph` keeps its DDL, but its address column should hold `document#section@Lstart-end`, not Source Doc 1's 9-digit coordinate. The live `atropos.dloi.DloiCoordinate` is `documentId/sectionId/lineStart/lineEnd`; the numeric `atropos.data.lakehouse.DloiCoordinate` has **zero callers** and should be deleted per `S17`.

Ask the human rather than picking.
