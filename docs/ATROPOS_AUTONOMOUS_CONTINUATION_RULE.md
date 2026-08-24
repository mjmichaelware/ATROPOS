# ATROPOS autonomous continuation rule

This is a durable checkpoint rule for external agents.

1. Never end a turn merely because one wave is partial, a command timed out, or a test lane is unavailable.
2. Before reporting status, read `STATUS-BACKEND.md`, identify the first unfinished atom, and continue it in the same turn when safe.
3. After every coherent batch, append evidence to `AGENTS.md` and update `STATUS-BACKEND.md` before moving to the next atom.
4. A failed or interrupted verification command blocks only the predicate it tests. Use narrower deterministic checks and continue safe independent work.
5. Preserve `partial` and `blocked` labels. Never convert missing external proof into `done` or `VERIFIED`.
6. Stop only for the existing human-mandatory list: secrets or paid spending, protected destructive git operations, and external GitHub/device/install actions requiring operator authority.
7. On the next turn, resume from the first unfinished row, not from a fresh architecture scan or a completed wave.
