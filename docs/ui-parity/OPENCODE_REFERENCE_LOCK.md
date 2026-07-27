# OpenCode Reference Lock

This pins the **single** parity oracle. No parity claim in this repository may
cite any OpenCode revision other than the one below.

| Field | Value |
|---|---|
| Repository | https://github.com/sst/opencode |
| Commit | `7534d23551f665e65080809975b4ca5c7d63807b` |
| Commit date | 2026-07-25T08:21:12+00:00 |
| License | MIT (Copyright (c) 2025 opencode) |
| Fetched | 2026-07-27T01:25:34.640399+00:00 |
| Fetch method | `git clone --depth 1` |
| ATROPOS HEAD at lock time | `b4c0f5b31b37735eccd9b6418ec95689b1b23a68` |

## Terms

- OpenCode is **read-only** here. It is never modified, vendored, redistributed,
  or shipped inside ATROPOS.
- ATROPOS keeps its own branding, architecture, commands, providers, DAG,
  hierarchy, source authority, verification, security policy, and autonomy model.
  This is a **design-quality and interaction-quality** reference, not a fork.
- No ATROPOS surface may imply affiliation with or endorsement by OpenCode.
- Parity is never claimed from colors or screenshots alone; see the lifecycle
  gates in the surface matrix.

## Reference client inventory at the pinned commit

| Package | Source files | ATROPOS counterpart |
|---|---:|---|
| `packages/tui` | 204 | **ATROPOS CLI/TUI — the one applicable client** |
| `packages/app` | 531 | none (no ATROPOS desktop/web app) |
| `packages/core` | 477 | n/a (backend, out of UI scope) |
| `packages/console` | 235 | none |
| `packages/ui` | 199 | partial — token/component source only |
| `packages/session-ui` | 94 | none |
| `packages/desktop` | 85 | none |
| `packages/server` | 29 | none (ATROPOS has no HTTP server) |
| `packages/web` | 19 | none |

**31 packages, ~3,000 source files total at the pinned commit.**

## Scope consequence

ATROPOS at `b4c0f5b31b37735eccd9b6418ec95689b1b23a68` is **114 Kotlin files, JVM-only, terminal-only**. It has no
`package.json`, no TS/TSX, no HTML/CSS, no Android manifest, no HTTP server, and
no desktop/web/mobile client of any kind. Therefore:

- **Applicable** parity surface = the terminal client only (161 rows).
- **Not applicable** = desktop, web, mobile, console, session-ui, server
  (6 rows, recorded `BLOCKED_NO_TARGET_SURFACE`). Reaching parity on these means
  *building three new client applications plus a server/API layer from zero* —
  that is new product construction, not UI/UX parity work, and it is recorded as
  a blocker rather than silently attempted or silently dropped.
