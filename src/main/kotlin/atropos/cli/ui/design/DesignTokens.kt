/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * Semantic design tokens for every ATROPOS terminal surface.
 *
 * Renderers name a *role* ([Role.STATUS_VERIFIED]), never a color. The active
 * [ThemePalette] resolves the role to SGR parameters at the terminal's actual
 * capability tier. This is the single place a color may be defined; a raw SGR
 * string anywhere else in `cli/ui` is a defect.
 *
 * Adding a renderer requires no palette work: it names existing roles and
 * inherits every theme and capability tier automatically.
 */
enum class Role {
    /** ATROPOS identity. Section headers, logo, brand marks. */
    BRAND,
    BRAND_MUTED,

    /** Body copy at full contrast. */
    TEXT_PRIMARY,
    /** Labels, keys, secondary copy. */
    TEXT_SECONDARY,
    /** De-emphasised hints, placeholders, disabled copy. */
    TEXT_MUTED,
    /** Copy drawn on an inverted/filled surface. */
    TEXT_INVERSE,

    /**
     * Status roles backing the Source Doc 3 Section A vocabulary. See [RunState],
     * which is the only supported way to render them — it forces a redundant
     * glyph and label alongside the colour.
     *
     * `STATUS_FAILED` maps to the `danger` role, which Section A reserves
     * **exclusively** for failure states so "a red glance always means
     * stop-and-look, not maybe-look". Never reuse it for warnings.
     */
    STATUS_IDLE,
    /** Cancelled: neutral ink plus SGR 9 crossed-out, per Section A. */
    STATUS_CANCELLED,
    STATUS_RUNNING,
    STATUS_WAITING,
    STATUS_FAILED,
    STATUS_COMPLETE,
    STATUS_UNKNOWN,

    /** Informational, non-status emphasis (Section F `info` role). */
    INFO,

    // Retained aliases for pre-spec call sites. STATUS_VERIFIED/PENDING/ERROR
    // predate the Section A vocabulary; they resolve to the same inks as
    // COMPLETE/WAITING/FAILED so no surface changes appearance, but new code
    // must use RunState so the colour-blind redundancy rule cannot be bypassed.
    /** @deprecated Use [RunState.COMPLETE]. */
    STATUS_VERIFIED,
    /** @deprecated Use [RunState.WAITING] or [RunState.BLOCKED]. */
    STATUS_PENDING,
    /** @deprecated Use [RunState.FAILED]. */
    STATUS_ERROR,

    /** Chrome backgrounds: header and footer bars. */
    SURFACE_HEADER,
    SURFACE_FOOTER,

    /** Card edges, rules, separators. */
    BORDER_SUBTLE,
    BORDER_STRONG,

    /** Active selection and keyboard focus. */
    ACCENT_SELECTION,
    ACCENT_FOCUS,

    /** Inline command/code spans and filesystem paths. */
    CODE,
    PATH,

    /** Unified/side-by-side diff rendering. */
    DIFF_ADD,
    DIFF_REMOVE,
    DIFF_CONTEXT,
    DIFF_HUNK
}

/**
 * Layout scale. Spacing, widths and rules are named here so a magic number in a
 * renderer is as much a defect as a magic color.
 */
object Spacing {
    /** Width of the label gutter in a key/value row. */
    const val LABEL_WIDTH = 11

    /** Narrow label gutter for dense detail views. */
    const val LABEL_WIDTH_DENSE = 9

    /** Gap between a label and its value. */
    const val GUTTER = 1

    /** Indent applied to wrapped continuation lines. */
    const val CONTINUATION_INDENT = 2

    /** Nominal card width used when composing multi-column dashboard layouts. */
    const val CARD_WIDTH = 28

    /** Minimum width any surface must still render legibly at. */
    const val MIN_WIDTH = 28
}

/**
 * Responsive breakpoints, in terminal columns. Every surface picks its layout
 * from these rather than inventing its own thresholds, so a phone-width check in
 * one renderer means the same thing as in every other renderer.
 */
enum class Breakpoint(val minColumns: Int) {
    /** Phone portrait. Single column, stacked, no tables. */
    COMPACT(0),
    /** Phone landscape / small split pane. Two columns, reduced tables. */
    MEDIUM(60),
    /** Laptop. Three columns, full tables. */
    WIDE(100),
    /** Desktop. Four columns. */
    ULTRA(140);

    companion object {
        fun of(columns: Int): Breakpoint =
            entries.last { columns >= it.minColumns }
    }
}

/**
 * Glyph vocabulary. Renderers never inline these.
 *
 * Layout follows the pinned reference's TUI, which draws **no boxes**: its
 * `EmptyBorder` is blank and the only structural glyph is a heavy vertical
 * rail (`SplitBorder`) on the left edge of a block, followed by two spaces of
 * padding, with blocks separated by one blank line. Enclosing card frames are
 * deliberately absent — hierarchy comes from the rail, indentation and colour,
 * not from drawn borders.
 */
object Glyphs {
    /** Left rail marking a content block. The reference's only border glyph. */
    const val RAIL = "┃"

    /** Padding between the rail and block content, in columns. */
    const val RAIL_PADDING = 2

    const val RULE = "─"
    const val SECTION_MARK = "──"
    const val ELLIPSIS = "…"
    const val BULLET = "·"

    /** ASCII fallbacks for terminals that cannot render box-drawing characters. */
    object Ascii {
        const val RAIL = "|"
        const val RULE = "-"
        const val SECTION_MARK = "--"
        const val ELLIPSIS = "..."
        const val BULLET = "*"
    }
}
