/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

import atropos.cli.ui.TerminalText

/**
 * Truthful status vocabulary shared by every ATROPOS surface.
 *
 * [UNKNOWN] exists so a renderer with no data has something honest to emit; it
 * must never be rendered as [VERIFIED]. There is deliberately no "probably ok".
 */
enum class Health(val role: Role) {
    VERIFIED(Role.STATUS_VERIFIED),
    PENDING(Role.STATUS_PENDING),
    ERROR(Role.STATUS_ERROR),
    UNKNOWN(Role.STATUS_UNKNOWN);

    companion object {
        /** Maps a nullable boolean truthfully: `null` is unknown, not false. */
        fun ofNullable(value: Boolean?): Health = when (value) {
            true -> VERIFIED
            false -> ERROR
            null -> UNKNOWN
        }
    }
}

/**
 * Width-safe composition primitives, painted through a [Painter].
 *
 * Every ATROPOS renderer composes from these rather than hand-rolling box glyphs
 * and `padEnd` arithmetic. That is what makes a new renderer inherit the design
 * language automatically instead of re-deriving it — and it is why fixing a
 * clipping bug here fixes it on every surface at once.
 *
 * Every method is width-safe: nothing returned ever exceeds the requested width.
 */
class Surface(private val paint: Painter) {

    /** Paints a role onto text. Supplied by the theme; the only styling seam. */
    fun interface Painter {
        fun paint(role: Role, text: String): String
    }

    /** Whether box-drawing glyphs are safe, or ASCII fallbacks are required. */
    var asciiOnly: Boolean = false

    private fun glyph(unicode: String, ascii: String) = if (asciiOnly) ascii else unicode

    // ---- rules and headings -------------------------------------------------

    /** Full-width horizontal rule. */
    fun rule(width: Int, role: Role = Role.BORDER_SUBTLE): String =
        paint.paint(role, glyph(Glyphs.RULE, Glyphs.Ascii.RULE).repeat(width.coerceAtLeast(0)))

    /** `── TITLE ──` section heading, clipped to width. */
    fun sectionHeading(title: String, width: Int, role: Role = Role.BRAND): String {
        val mark = glyph(Glyphs.SECTION_MARK, Glyphs.Ascii.SECTION_MARK)
        return TerminalText.ellipsize(paint.paint(role, "$mark $title $mark"), width)
    }

    // ---- key/value rows -----------------------------------------------------

    /**
     * Aligned `label   value` row. The label gutter is a token, not a literal, so
     * every surface in ATROPOS aligns identically.
     */
    fun row(
        label: String,
        value: String,
        width: Int,
        labelWidth: Int = Spacing.LABEL_WIDTH,
        labelRole: Role = Role.TEXT_SECONDARY
    ): String {
        val gutter = paint.paint(labelRole, TerminalText.padEnd(label, labelWidth))
        return TerminalText.ellipsize(gutter + " ".repeat(Spacing.GUTTER) + value, width)
    }

    /** Key/value row whose value carries a health color. */
    fun statusRow(
        label: String,
        value: String,
        health: Health,
        width: Int,
        labelWidth: Int = Spacing.LABEL_WIDTH
    ): String = row(label, paint.paint(health.role, value), width, labelWidth)

    // ---- badges -------------------------------------------------------------

    /** `[verifying]` style status badge. */
    fun badge(text: String, health: Health): String =
        paint.paint(health.role, "[$text]")

    // ---- cards --------------------------------------------------------------

    /**
     * Bordered card. Body lines are clipped to the inner width, so a card can
     * never push a dashboard column past its right edge.
     */
    fun card(title: String, body: List<String>, width: Int): List<String> {
        val inner = (width - 2).coerceAtLeast(Spacing.MIN_WIDTH - 2)
        val top = paint.paint(
            Role.BRAND,
            glyph(Glyphs.CARD_TOP_LEFT, Glyphs.Ascii.CARD_TOP_LEFT) +
                glyph(Glyphs.RULE, Glyphs.Ascii.RULE) + " " + title
        )
        val edge = paint.paint(
            Role.BORDER_SUBTLE,
            glyph(Glyphs.CARD_EDGE, Glyphs.Ascii.CARD_EDGE)
        )
        val bottom = paint.paint(
            Role.BORDER_SUBTLE,
            glyph(Glyphs.CARD_BOTTOM_LEFT, Glyphs.Ascii.CARD_BOTTOM_LEFT) +
                glyph(Glyphs.RULE, Glyphs.Ascii.RULE)
                    .repeat((width - 1).coerceIn(0, Spacing.CARD_WIDTH))
        )

        return buildList {
            add(TerminalText.ellipsize(top, width))
            body.forEach { add(TerminalText.ellipsize("$edge ${TerminalText.ellipsize(it, inner)}", width)) }
            add(TerminalText.ellipsize(bottom, width))
        }
    }

    // ---- columns ------------------------------------------------------------

    /**
     * Lays cards side by side. Each cell is clipped *before* padding, which is
     * the invariant that keeps multi-column dashboards from overflowing.
     */
    fun columns(cards: List<List<String>>, count: Int, width: Int): List<String> {
        if (count <= 1) return cards.flatten().map { TerminalText.ellipsize(it, width) }
        val columnWidth = (width / count).coerceAtLeast(Spacing.MIN_WIDTH)
        val out = mutableListOf<String>()

        cards.chunked(count).forEach { group ->
            val height = group.maxOf { it.size }
            for (line in 0 until height) {
                out += group.joinToString("") { card ->
                    TerminalText.padEnd(
                        TerminalText.ellipsize(card.getOrElse(line) { "" }, columnWidth - 1),
                        columnWidth
                    )
                }.trimEnd()
            }
            out += ""
        }
        return out.map { TerminalText.ellipsize(it, width) }
    }

    /** Chooses a column count from the shared responsive scale. */
    fun columnsFor(width: Int): Int = when (Breakpoint.of(width)) {
        Breakpoint.COMPACT -> 1
        Breakpoint.MEDIUM -> 2
        Breakpoint.WIDE -> 3
        Breakpoint.ULTRA -> 4
    }

    // ---- tables -------------------------------------------------------------

    /**
     * Width-safe table. Columns are dropped right-to-left when the terminal is
     * too narrow rather than being squeezed illegibly; at [Breakpoint.COMPACT]
     * callers should stack instead of calling this.
     */
    fun table(
        headers: List<String>,
        rows: List<List<String>>,
        widths: List<Int>,
        totalWidth: Int
    ): List<String> {
        var keep = headers.size
        while (keep > 1 && widths.take(keep).sum() + keep - 1 > totalWidth) keep--

        fun line(cells: List<String>, style: ((String) -> String)? = null): String {
            val text = (0 until keep).joinToString(" ") { i ->
                val w = widths[i]
                val cell = TerminalText.ellipsize(cells.getOrElse(i) { "" }, w - 1)
                if (i == keep - 1) cell else TerminalText.padEnd(cell, w)
            }
            return TerminalText.ellipsize(style?.invoke(text) ?: text, totalWidth)
        }

        return buildList {
            add(line(headers) { paint.paint(Role.TEXT_SECONDARY, it) })
            rows.forEach { add(line(it)) }
        }
    }

    // ---- text ---------------------------------------------------------------

    /** Muted hint line, e.g. an empty-state or next-command affordance. */
    fun hint(text: String, width: Int): String =
        TerminalText.ellipsize(paint.paint(Role.TEXT_MUTED, text), width)

    /** Honest empty state. Never a blank region and never a fabricated zero. */
    fun emptyState(message: String, nextCommand: String?, width: Int): List<String> =
        buildList {
            add(hint(message, width))
            nextCommand?.let { add(TerminalText.ellipsize(paint.paint(Role.CODE, it), width)) }
        }

    /** Joins fragments with the shared separator glyph. */
    fun joinMeta(parts: List<String>, width: Int): String =
        TerminalText.ellipsize(
            parts.filter { it.isNotBlank() }
                .joinToString(" ${glyph(Glyphs.BULLET, Glyphs.Ascii.BULLET)} "),
            width
        )
}
