# Universal Codex Override

- Preserve the user’s current worktree; never discard unrelated tracked or untracked changes.
- Prefer exact paths and bounded slices over broad repo-wide edits.
- Verify before mutating when a local deterministic check can decide the issue.
- Keep prompts, outputs, and context packs narrow.
- Redact secrets, tokens, and credential material before persistence or display.
- Do not commit, push, or force-push unless explicitly requested.
- Do not use destructive git operations unless explicitly approved.
- Use atomic writes for generated files and caches.
- Treat cache keys as part of correctness; include every real invalidation input.
- If a repository defines local operating rules, follow them first.
