---
name: atropos-resume
description: Use when resuming an interrupted ATROPOS bootstrap, recovering a dirty worktree, or reconstructing the last known checkpoint from handoff and audit artifacts.
---

# Atropos Resume

## Procedure

1. Read `references/resume.md`.
2. Run `bash scripts/codex/resume-handoff.sh` to capture the current dirty state.
3. Re-open the latest handoff and `python3 scripts/codex/instruction-audit.sh` output before editing.
4. Resume the smallest unfinished slice from persisted state; do not restart the corpus audit unless the index is missing or stale.

## Guardrails

- Preserve all tracked and untracked work.
- Use the handoff only as a recovery anchor; do not rewrite it to hide current dirt.
- If the last step was source inspection, re-use the persisted index instead of re-reading the corpus.
