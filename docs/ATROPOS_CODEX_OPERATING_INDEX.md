# ATROPOS Codex Operating Index

## Purpose

This index describes how Codex should load, verify, cache, and resume ATROPOS bootstrap work without flooding context with the entire corpus.

## Authority Precedence

Use source truth in this order:

1. ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME
2. ATROPOS Source Documents 1 and 2
3. Hierarchy and coding-agent research docs
4. Updated DLOI source-document map
5. Current repository contracts and proven tests
6. Dated implementation contexts and doctor reports
7. README claims only as historical or aspirational evidence

Exact query anchors:

- `python3 scripts/codex/source-query.py phase 11 --limit 3`
- `python3 scripts/codex/source-query.py search Director territory HR Router Auditor Custodian Manager Specialist Worker --source-id daa37f228230881b --limit 12`
- `python3 scripts/codex/source-query.py search free-first RoutePolicy paid locked paid emergency --source-id 4995ec5b478cae18 --source-id 04c393b5f0ec1017 --limit 12`

## Source Inventory

Persistent source authority:

- Original corpus: `.atropos/source-authority/original/`
- Manifest: `.atropos/source-authority/SOURCE_MANIFEST.tsv`
- Generated index root: `.atropos/context-cache/source-index/v1/`
- Inventory: `.atropos/context-cache/source-index/v1/inventory.tsv`
- Summary: `.atropos/context-cache/source-index/v1/reports/source-index-summary.json`

Current persisted index totals:

- Sources: 88
- Sections: 3927
- Total bytes: 73658452
- Manifest SHA-256: `2a6f47146575f9502101894f7945d8a46966bed5405551bf24e8f28976639726`
- Source fingerprint: `05618d121e69ead3fe0e5f859470eec22393d67909c9aeed125a3f1d1298acb1`
- Kind counts: 62 text, 9 markdown, 9 docx, 7 pdf, 1 tabular

Use the inventory file for per-source SHA-256, size, normalized path, and section count. Use the generated JSON index for section coordinates and supersession metadata.

## Canonical Phase Map

The canonical phases are fixed. Do not rename them.

| Phase | Name | Practical meaning |
| --- | --- | --- |
| 0 | Baseline Lock | Freeze current truth and preserve the checkpoint. |
| 1 | Provider Activation Doctor | Make provider states truthful. |
| 2 | Provider Transport Completion | Reach real provider responses. |
| 3 | Quota Ledger + Route Truth | Explain free-first routing and cooldowns. |
| 4 | Secret and Security Hardening | Redact secrets everywhere. |
| 5 | Provider Fixture Matrix | Verify provider behavior without live keys. |
| 6 | DLOI Source Router | Address exact source sections. |
| 7 | AST Symbol Graph | Map impacted symbols and files. |
| 8 | Deterministic Verifier | Catch structural failures locally. |
| 9 | Persistent Memory | Persist session and repair state. |
| 10 | Execution Policy Engine | Govern tool execution. |
| 11 | Self-Build Loop | Build bounded slices with `E(DELTA)=0`. |
| 12 | Director Advisory Mode | Add advisory drift monitoring. |
| 13 | Territory Enforcement | Enforce file and worktree boundaries. |
| 14 | HR Router | Control cross-boundary information flow. |
| 15 | Auditor and Custodian | Verify and clean deterministically. |
| 16 | Manager/Specialist/Worker Hierarchy | Scale execution through bounded roles. |
| 17 | Multimodal Runtime | Add visual and snapshot inspection. |
| 18 | Multiplatform Expansion | Extract portable runtime surfaces. |
| 19 | App Factory Completion | Deliver verified artifacts. |
| 20 | Full Autonomous ATROPOS | Self-improve inside policy and territory. |

Fixed phase anchors from the indexed blueprint:

- Phase 11: `97cff09c0f362337__ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt` `[S0013]` lines 46-48
- Phase 12: `97cff09c0f362337__ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt` `[S0014]` lines 49-51
- Phase 19: `97cff09c0f362337__ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt` `[S0021]` lines 70-72
- Phase 20: `97cff09c0f362337__ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt` `[S0022]` lines 73-84

## Cache Architecture

### Source Index Cache

The source index lives under `.atropos/context-cache/source-index/v1/`. It is built from the manifest and the original corpus, then written atomically.

Key inputs:

- Source SHA-256
- Manifest SHA-256
- Tool schema version
- Source fingerprint

### Context Cache

Use `.atropos/context-cache/` for deterministic prompt fragments, query results, and audit outputs. Cache keys must include:

- Source hash
- Tool schema version
- Query terms or target path
- Relevant source fingerprint
- Command or task identity
- Any other invalidation input required by the tool

### Gate Cache

The gate cache lives under `.atropos/gate-cache/`. A passed command may never satisfy a different command.

Key inputs:

- Full command and arguments
- Working directory
- Relevant input-file hashes
- Build configuration hashes
- Affecting environment variables, excluding secrets
- Tool version
- Expected output or invariant set

Never cache:

- Live-provider tests
- Time-sensitive checks
- Network checks
- Mutable daemon checks
- Secret checks
- Commands explicitly marked `no-cache`

### Handoff Cache

Use `.atropos/handoffs/` for resume notes that capture dirty tracked files, dirty untracked files, branch state, and the last known verification checkpoint.

## Compile Cadence

Follow this order:

1. Contract audit
2. Coherent edit slice
3. Focused deterministic test
4. `compileKotlin` after the coherent slice
5. Test or jar only at milestone acceptance
6. Installed smoke only at the final gate

Do not compile after every small edit. Do not run a production compile during bootstrap-only work.

## Interruption Recovery

When a terminal closes:

1. Read the handoff file in `.atropos/handoffs/`
2. Re-load the source index from `.atropos/context-cache/source-index/v1/index.json`
3. Check gate-cache hits only for the exact command being resumed
4. Re-run the smallest needed deterministic check
5. Continue the same slice

## Refresh Procedure

After source changes:

```bash
python3 scripts/codex/source-index.py --strict
python3 scripts/codex/instruction-audit.sh
```

Then refresh any context packs or handoff notes that depend on the changed source hashes.

## Proving Correct Instructions

Use a short proof chain instead of re-dumping the corpus:

```bash
python3 scripts/codex/source-query.py phase 11 --limit 3
python3 scripts/codex/source-query.py phase 19 --limit 3
python3 scripts/codex/source-query.py search Director territory HR Router --source-id daa37f228230881b --limit 8
python3 scripts/codex/instruction-audit.sh
```

The returned phase and hierarchy coordinates prove the operating index is loaded from the persisted source authority.

## Global Override

Install:

```bash
mkdir -p ~/.codex
cp ops/codex/AGENTS.override.md ~/.codex/AGENTS.override.md
```

Remove:

```bash
rm -f ~/.codex/AGENTS.override.md
```

## Why Preservation Is Not Dumping

Complete source preservation means the original corpus stays byte-for-byte intact in source authority storage, while prompts and packs only include the exact authority needed for the current slice. That keeps provenance, section coordinates, and deduplication intact without wasting context on unrelated text.

Use `scripts/codex/context-pack.py` to assemble bounded packs from selected authority, current contracts, changed files, impacted symbols, and bounded failure evidence only.
