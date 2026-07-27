# ATROPOS Pass 11 Self-Build Loop

`/agent run` now supports an optional local smoke command:

- `/agent run --smoke "test -f docs/pass11-self-build-smoke.md" <task>`
- `/agent run <task> --smoke "test -f docs/pass11-self-build-smoke.md"`

The job record tracks:

- plan
- patch
- apply
- verification
- repair
- smoke command/result
- final report
- commit proposal
- next suggested command

The command remains deterministic:

- provider output does not auto-commit
- smoke commands are local-only and conservative
- verification remains the source of truth
- next-context export is written under `.atropos/agent/context/`
