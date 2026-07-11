# Resume Reference

## Commands

```bash
bash scripts/codex/resume-handoff.sh
bash scripts/codex/instruction-audit.sh
python3 scripts/codex/source-query.py phase 11 --limit 3
python3 scripts/codex/source-query.py phase 19 --limit 3
```

## Handoff contents

- branch and HEAD
- dirty tracked files
- dirty untracked files
- `git diff --stat`
- `git diff --name-only`
- resume timestamp and path

## Recovery rule

Resume from the persisted index, handoff, and audit report. Do not rebuild source authority unless the stored hashes are missing or stale.
