# SpecGraph Foundry — Compiler Redesign Reconciliation Manifest

**Generated**: 2026-07-27T07:33Z
**Phase**: 0 — REPOSITORY RECONCILIATION
**Status**: AWAITING APPROVAL BEFORE ANY FILE OPERATIONS

---

## 1. Git Roots Identified

### Root A — CANONICAL (contains all redesign work)
| Property | Value |
|---|---|
| **Absolute Path** | `/data/data/com.termux/files/home/Workspaces/Applications/specgraph-foundry` |
| **Git Type** | Top-level repository (not a worktree) |
| **Remote** | `https://github.com/mjmichaelware/specgraph-foundry.git` |
| **Branch** | `phase-3-production-application` |
| **HEAD** | `7c909b1ee7931d093266a7800fb94c7e90b5edd1` |
| **Ahead/Behind** | `0/0` (up to date with remote) |
| **Dirty State** | **YES** — 5 modified tracked files, 4 untracked items |
| **Has `src/specgraph_foundry/compiler/`** | **YES** — 21 `.py` files, **all UNTRACKED** |
| **Has `tests/test_compiler.py`** | **YES** — **UNTRACKED** |
| **Has `supabase/migrations/20260727*.sql`** | **YES** — **UNTRACKED** |
| **Has `docs/compiler_redesign_results.md`** | **YES** — **TRACKED** (committed in `7c909b1`) |

### Root B — INNER CLONE (no redesign work, clean)
| Property | Value |
|---|---|
| **Absolute Path** | `/data/data/com.termux/files/home/Workspaces/Applications/specgraph-foundry/specgraph-foundry` |
| **Git Type** | Independent clone inside Root A (appears as untracked `specgraph-foundry/` directory) |
| **Remote** | `https://github.com/mjmichaelware/specgraph-foundry.git` (same remote) |
| **Branch** | `main` |
| **HEAD** | `bedac88b921f5a0eecd6d76d9a5efee00f4cc01d` |
| **Ahead/Behind** | `0/0` |
| **Dirty State** | **CLEAN** — zero modifications |
| **Has compiler redesign files** | **NO** — none in any branch |

**CRITICAL**: Commit `96d11bc` in Root B titled `"feat: implement SpecGraph permanent compiler redesign"` only changed `.gitignore` (+2 lines). It contains **zero compiler files**. This commit is misleadingly titled and is NOT the compiler redesign.

### Other Locations
| Path | Status |
|---|---|
| `/data/data/com.termux/files/home/specgraph-boolean-sql-backup-20260712_084755/` | Old backup (2026-07-12). Not a Git repo. No `compiler/` directory. No redesign files. Pre-dates this work. |

---

## 2. Redesign File Inventory

### A. NEW COMPILER FILES (all untracked in Root A)

| File (relative) | Size | SHA-256 | Modified | Tracked | In Any Commit |
|---|---|---|---|---|---|
| `src/specgraph_foundry/compiler/__init__.py` | 10,781 | `04a04e89403ebcd221da3bbbc0ebdebce11bb7baf6bc10945c57ca381d8a98f3` | 05:44 | NO | NO |
| `src/specgraph_foundry/compiler/artifact_contracts.py` | 2,173 | `04835dd6716d53b84fe4af36985073c71d30c8dc56488c916b2e2e71c54c4a53` | 04:50 | NO | NO |
| `src/specgraph_foundry/compiler/atomic_decomposition.py` | 4,480 | `ff050c86fadd42e604243bc5f6831aca411d0d11928f592b80106adda0575806` | 04:35 | NO | NO |
| `src/specgraph_foundry/compiler/compiler_evaluation.py` | 2,536 | `3113d8107213036b84852a3e2b77fd6841ab32fb459fd1fd33fa07a05860ab61` | 04:36 | NO | NO |
| `src/specgraph_foundry/compiler/compiler_fingerprints.py` | 1,428 | `7b042a8f8e374e07bc9d9058d2d445e3b7348859af8b9ef587eb116900003cb8` | 04:36 | NO | NO |
| `src/specgraph_foundry/compiler/compiler_replay.py` | 1,992 | `d1bf79d43ba06d8cc21fed918465a07c0f00bc9621ae157cc7fb8f82f9f050ae` | 04:36 | NO | NO |
| `src/specgraph_foundry/compiler/dependency_compiler.py` | 5,671 | `8db491cc701426d17550e8b66b13beee2536e59e33768322470f67fcee66b990` | 04:50 | NO | NO |
| `src/specgraph_foundry/compiler/discourse_roles.py` | 4,490 | `1d73558583cbafc687cdb0bc156ccf274fb5e31ecd8a55305028176940997e13` | 05:23 | NO | NO |
| `src/specgraph_foundry/compiler/document_ir.py` | 2,085 | `2d3fc0c935db037f91a0a13b12831c8842ac2468ef5c48fad82432cd8a0610b7` | 04:34 | NO | NO |
| `src/specgraph_foundry/compiler/format_adapters.py` | 7,543 | `6a2636b9558bc611ebde217e9b8632b8c9d260b98812ed2d4466a8d0eb1ae6e3` | 04:50 | NO | NO |
| `src/specgraph_foundry/compiler/graph_validation.py` | 5,025 | `6fc3a6d0245f29d8309e600618935eaa65f82ecb41036ba522036f0d97e8f483` | 04:35 | NO | NO |
| `src/specgraph_foundry/compiler/provenance.py` | 5,974 | `e1190d1dda7984e636d1a27ff461bb69524ce6c43a383fa943a5eef5f5ac2690` | 04:35 | NO | NO |
| `src/specgraph_foundry/compiler/requirement_candidates.py` | 4,659 | `dbd61ba08f61c3948e6bd7b4fce98887a1e443f5fdcb2dc175525a2c2db6157b` | 05:43 | NO | NO |
| `src/specgraph_foundry/compiler/requirement_ir.py` | 3,761 | `e8b7ae3017057a59ffcb667940c0596ef4d2d18cfd1eb7b39cacb1e632091758` | 04:35 | NO | NO |
| `src/specgraph_foundry/compiler/requirement_quality.py` | 2,465 | `bbbceff869aa4bdaf68b07359ad7f1b4bbbb09b97792d932b92ee08bdbbe417b` | 04:35 | NO | NO |
| `src/specgraph_foundry/compiler/semantic_proposals.py` | 2,843 | `6492d0686eb91e8ce2277bac26181f5cdb2d95de5ad579f247b15ea2e360573d` | 04:36 | NO | NO |
| `src/specgraph_foundry/compiler/semantic_relations.py` | 4,096 | `d86bbf20b0f11849aab71f9c876d84589db0a17553b2d55ef1b37e09392e0f55` | 04:35 | NO | NO |
| `src/specgraph_foundry/compiler/semantic_types.py` | 5,976 | `85e630f872f3aa6060958e39045a804c6c6052db4756f4c66e3566027a03fedc` | 05:23 | NO | NO |
| `src/specgraph_foundry/compiler/source_authority.py` | 2,722 | `b512b1e82ec9d10b11061cb3b5d147a831cf605092e1bb3a995eeb14f0dfa760` | 04:35 | NO | NO |
| `src/specgraph_foundry/compiler/source_coordinates.py` | 634 | `5ff38c182008d8f6a1f73f35e3a1fd4e9e31f36d7a30e7eb8b28d3b77e241af0` | 04:34 | NO | NO |
| `src/specgraph_foundry/compiler/statement_segmentation.py` | 10,012 | `afd4ecaeab8b4c3ea039a8209047e094a1c43e5aa192623d160f64b3470a3464` | 05:44 | NO | NO |
| `tests/test_compiler.py` | 6,321 | `a8db15f6bbdc6da09710860e865d3facf4c5808d79adb3fe450ace3a1e693673` | 05:45 | NO | NO |
| `supabase/migrations/20260727000000_compiler_redesign.sql` | 6,381 | `f678f148c3cdf63add3e3792a1243d9c88e44715ab130403bb696f014745e9d2` | 04:38 | NO | NO |

### B. MODIFIED TRACKED FILES (dirty in Root A working tree)

| File (relative) | Size | SHA-256 (working tree) | Diff Summary | Tracked |
|---|---|---|---|---|
| `src/specgraph_foundry/atoms.py` | 47,214 | `cfd5cc6c50450188713c7dacfacbe17e57aadfd9112c6e8fe9a72cf5446df0fd` | +427 / -66 | YES |
| `src/specgraph_foundry/database.py` | 34,902 | `3eca5cae993f720f0224cd65f328666fdeeb744aff1bdfcaf9e66935df80af9b` | +148 | YES |
| `src/specgraph_foundry/exports.py` | 59,920 | `e44b3bd96b44ae734c1130bbe4ccb5c33f3750167849049ddaa2e89bd4e9f7bb` | +3 | YES |
| `tests/test_research.py` | 5,564 | `e1166544b40d8056cda9134cf93794df6444b434e802a88dc583f32c0f0b301a` | +1 / -1 | YES |
| `.gitignore` | 354 | `08f81d2a493ab341edf4a2239f13a9d4f0da8167aefe58944593031548278722` | +2 | YES |

### C. COMMITTED REDESIGN FILE

| File (relative) | Committed In | Issue |
|---|---|---|
| `docs/compiler_redesign_results.md` | `7c909b1` | TRACKED — but content cites wrong commit `6ec33ce` |

---

## 3. Critical Findings

1. **ALL 23 new compiler files and the migration are UNTRACKED and exist ONLY as working-tree files in Root A.** They are not in any commit, any branch, or any remote. A `git clean -fd` or directory deletion would destroy them permanently. There is no backup.

2. **The committed `docs/compiler_redesign_results.md`** (in `7c909b1`) cites commit SHA `6ec33ce36d875fa7a57dc17a3af56259752112ac` as the compiler redesign commit. That commit is `"deploy: publish database URL from protected secret"` — it has nothing to do with the compiler redesign.

3. **Root B (inner clone)** is an independent clean clone sitting inside Root A's working tree as an untracked directory. It has zero redesign work. Commit `96d11bc` in Root B's `main` branch is titled `"feat: implement SpecGraph permanent compiler redesign"` but only adds 2 lines to `.gitignore`.

---

## 4. Reconciliation Plan

### Canonical Repository Selection

**Root A** (`/data/data/com.termux/files/home/Workspaces/Applications/specgraph-foundry`) is the canonical repository because:
- It is the only location containing redesign files
- It is tied to `mjmichaelware/specgraph-foundry` via `origin`
- It is on branch `phase-3-production-application`
- All 263 tests pass from this root

### Required Actions (in order)

1. **DO NOT** delete, clean, reset, stash, or modify Root B. It is harmless.
2. **`git add`** all 23 untracked redesign files in Root A so they are tracked and safe from accidental loss.
3. **Correct** `docs/compiler_redesign_results.md` to remove the false commit reference and reflect the true working-tree state.
4. **Do NOT commit or push** until the full conformance audit is complete.
5. Root B inner clone is already `.gitignore`d (it appears as `?? specgraph-foundry/`).

### Data Loss Assessment

| Category | Count | Risk |
|---|---|---|
| Unique redesign files only in working tree | 23 | HIGH — no backup exists |
| Modified tracked files with unsaved diffs | 5 | MEDIUM — git checkout would lose changes |
| Files in Root B not in Root A | 0 | None |
| Files in backup not in Root A | 0 | None |
| Conflicts between copies | 0 | None |

**KNOWN DATA LOSS: NONE** — all redesign work is present and intact in Root A's working tree.

---

## 5. Environment

| Property | Value |
|---|---|
| Python | 3.14.4 |
| Virtualenv | `/root/.venvs/specgraph-foundry` |
| Test Command | `PYTHONPATH=src /root/.venvs/specgraph-foundry/bin/python -m unittest discover -s tests` |
| OS | Linux (Termux) |

---

## CHECKPOINT

```
CHECKPOINT: REPOSITORY_RECONCILIATION_COMPLETE
CANONICAL_ROOT: /data/data/com.termux/files/home/Workspaces/Applications/specgraph-foundry
BRANCH: phase-3-production-application
HEAD: 7c909b1ee7931d093266a7800fb94c7e90b5edd1
FILES_RECOVERED: 0
FILES_MERGED: 0
CONFLICTS_RESOLVED: 0
UNTRACKED_REDESIGN_FILES: 23
KNOWN_DATA_LOSS: NONE
```
