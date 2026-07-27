# ATROPOS Design System Spec

Batch **B**. Reference: `sst/opencode` @ `7534d235` (design *grammar* only — no
OpenCode code, branding, or assets are used, vendored, or implied).

Source: `src/main/kotlin/atropos/cli/ui/design/`

## Why this layer exists

Before Batch B, 28 of ATROPOS's 40 renderers had **no design language at all** —
they emitted raw uncoloured strings with hard-coded `padEnd(18)` gutters and
inline box glyphs. The 12 that did use the theme each re-derived their own
layout. There was no way to add a renderer and have it look like ATROPOS.

The fix is two layers, not one. Tokens alone would only make new renderers
inherit *colour*; they would still hand-roll structure. So this system ships
**tokens + primitives**, which is what makes inheritance real.

## Layer 1 — Tokens (`DesignTokens.kt`)

### Roles

Renderers name a **role**, never a colour. 22 semantic roles:

| Group | Roles |
|---|---|
| Identity | `BRAND`, `BRAND_MUTED` |
| Text | `TEXT_PRIMARY`, `TEXT_SECONDARY`, `TEXT_MUTED`, `TEXT_INVERSE` |
| Status | `STATUS_VERIFIED`, `STATUS_PENDING`, `STATUS_ERROR`, `STATUS_UNKNOWN` |
| Surface | `SURFACE_HEADER`, `SURFACE_FOOTER` |
| Border | `BORDER_SUBTLE`, `BORDER_STRONG` |
| Accent | `ACCENT_SELECTION`, `ACCENT_FOCUS` |
| Content | `CODE`, `PATH` |
| Diff | `DIFF_ADD`, `DIFF_REMOVE`, `DIFF_CONTEXT`, `DIFF_HUNK` |

`STATUS_UNKNOWN` exists so a renderer with no data has something honest to emit.
It must never be rendered as verified. There is deliberately no "probably fine".

### Scale

`Spacing` (label gutters, card width, indents) and `Breakpoint`
(`COMPACT` 0 / `MEDIUM` 60 / `WIDE` 100 / `ULTRA` 140 columns) are tokens too. A
magic number in a renderer is as much a defect as a magic colour — a
phone-width check in one renderer now means the same thing as in every other.

`Glyphs` carries box-drawing characters plus ASCII fallbacks
(`ATROPOS_ASCII=1`).

## Layer 2 — Palettes (`ThemePalette.kt`)

The **only** place raw SGR parameters may appear.

### Capability tiers

`ColorTier.detect()` reads `NO_COLOR`, `TERM`, `COLORTERM` once and resolves to
`NONE` / `BASIC` (16) / `INDEXED` (256) / `TRUECOLOR` (24-bit). Never guessed
per-call, never assumed best-case. `NO_COLOR` and `TERM=dumb` both hard-force
`NONE`, which emits zero SGR bytes.

### Exhaustiveness is enforced at construction

`ThemePalette`'s `init` block requires a style for every `Role`. Adding a role
**fails the build** until every theme defines it, rather than silently rendering
unstyled in some themes.

### Built-in themes

`atropos-dark` (default — ATROPOS cyan identity) and `atropos-light`. Selected
via `ATROPOS_THEME`. The light theme exists to prove the token layer is genuinely
theme-independent rather than one palette with indirection bolted on.

## Layer 3 — Primitives (`Surface.kt`)

Composition, so new renderers inherit **structure**:

`rule` · `sectionHeading` · `row` · `statusRow` · `badge` · `card` · `columns` ·
`columnsFor` · `table` · `hint` · `emptyState` · `joinMeta`

Two invariants:

- **Nothing returned ever exceeds the requested width.** Every primitive clips
  before it pads. Fixing a clipping bug here fixes it on every surface at once.
- **`table` drops columns right-to-left** when too narrow rather than squeezing
  them illegibly; at `COMPACT` callers stack instead.

`Health` (`VERIFIED`/`PENDING`/`ERROR`/`UNKNOWN`) carries truthful status, with
`Health.ofNullable` mapping `null → UNKNOWN` — never to `false`.

## Integration

`TerminalTheme` is now a thin resolver over roles. **Its whole historical API is
preserved** (`brand`, `success`, `error`, `warning`, `metadata`, `subdued`,
`strong`, `path`, `code`, `headerBrand`, `headerText`, `footer`, `selection`),
so all 12 existing consumers pick up themes and capability tiers with **zero
changes**. New renderers should prefer `theme.surface` and `theme.paint(role, …)`.

## Verification

Token-layer harness results:

| Check | Result |
|---|---|
| Role exhaustiveness (2 themes × 22 roles × 4 tiers) | 176 combinations, pass |
| `NONE` tier emits zero ANSI (all 22 roles) | pass |
| Tier detection incl. `NO_COLOR` precedence | pass |
| Breakpoint boundaries at 40/80/120/160 | pass |
| **Primitive width safety** (2 themes × 4 tiers × 5 widths) | **752 rendered lines, zero overflow** |
| `Health` truthfulness (`null → UNKNOWN`) | pass |

Regression: all 20 committed PTY baselines re-captured after the refactor and are
**byte-identical** apart from two live file counts the dashboard correctly
re-probed (114→117 `.kt`). The theme re-backing is behaviour-preserving.

## Migration status

| Renderer | State |
|---|---|
| `TerminalTheme` | re-backed onto tokens, API preserved |
| `StatusAdapterRenderer` | **fully migrated** — reference implementation |
| 12 theme-consuming renderers | inherit tokens automatically, not yet on primitives |
| 27 remaining plain-text `Status*Renderer`s | not yet migrated |

`StatusAdapterRenderer` is the pattern to copy: semantic roles, `Health` for
status, `Breakpoint` for layout choice, primitives for all composition, and a
truthful `emptyState` instead of a blank region.

Migrating it also surfaced and fixed a real alignment defect — two labels
exceeded the `LABEL_WIDTH` token and pushed their values out of column. That
class of bug is now structurally visible rather than invisible.
