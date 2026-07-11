# ATROPOS Codex Skills

## Selection Rule

Choose the smallest skill that matches the task. Load only the reference file(s) that the skill points to.

## Skill Map

| Skill | Trigger | Reference focus |
| --- | --- | --- |
| `atropos-source-authority` | Source corpus indexing, authority precedence, source queries, or exact coordinate lookup | Source inventory, source-index refresh, and query commands |
| `atropos-resume` | Interrupted bootstrap recovery or dirty worktree handoff | Resume notes, dirty-file capture, and restart sequence |
| `atropos-fast-batch` | Cache-aware fast gates, compile lanes, or deterministic verification cadence | Gate cache key shape and command matrix |
| `atropos-foundation-1-11` | Phases 0-11, provider activation, route truth, and self-build foundations | Canonical phase anchors 1, 11, 12, 19, 20 plus phase 0-11 workflow |
| `atropos-provider-grid` | Provider activation, doctoring, free-first routing, or paid-lock state | Provider route law, verified/ready distinctions, and doctor queries |
| `atropos-dloi-ast-verifier` | DLOI maps, exact-source addressing, or AST impact verification | Coordinate map, section lookup, and impacted-symbol discipline |
| `atropos-memory-policy-selfbuild` | Persistent memory, resume state, or self-build loop work | Memory invariants, compaction, and self-build gates |
| `atropos-hierarchy-12-16` | Director, territory, HR Router, Auditor, Custodian, Manager, Specialist, Worker | Hierarchy and territory anchors for phases 12-16 |
| `atropos-multimodal-platform-17-18` | Visual inspection, screenshots, Compose Desktop, Android shell, or multiplatform extraction | Phase 17-18 anchors and platform split guidance |
| `atropos-app-factory-19` | Artifact generation, install/run proof, or phase 19 acceptance | Prompt-to-artifact flow and final proof requirements |
| `atropos-autonomy-20` | Full autonomy, policy-bound self-improvement, or phase 20 roadmap work | Autonomous backlog, route optimization, and territory-governed self-build |
| `atropos-acceptance-release` | Final gate, release readiness, truthfulness checks, or E(DELTA)=0 acceptance | Final verification, clean handoff, and release proof |

## Shared Operating Notes

- Read the skill’s `references/` file before editing.
- Use the repo scripts in `scripts/codex/` instead of recreating index, cache, or gate logic by hand.
- Do not duplicate full source documents inside SKILL.md.
- Keep SKILL.md procedural and concise; keep source-derived detail in references.
- Use exact source coordinates, not paraphrases, when the skill depends on the corpus.

## Recommended Loading Order

1. `atropos-source-authority` for exact source state
2. The task-specific skill
3. `atropos-resume` when the terminal or session was interrupted
4. `atropos-fast-batch` when you need deterministic verification or compile lanes
