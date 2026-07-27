/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Glyphs
import atropos.cli.ui.design.Role

/** Toast severity. Maps to the shared status roles, never to ad-hoc colour. */
enum class ToastVariant(val role: Role) {
    INFO(Role.INFO),
    SUCCESS(Role.STATUS_COMPLETE),
    WARNING(Role.STATUS_WAITING),
    ERROR(Role.STATUS_FAILED)
}

data class Toast(
    val title: String?,
    val message: String,
    val variant: ToastVariant = ToastVariant.INFO,
    /** Wall-clock ms the toast was raised; used for expiry. */
    val raisedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * Transient notification pinned to the top-right, in the pinned reference's
 * shape.
 *
 * The reference renders a toast as an absolutely-positioned box at `top=2
 * right=2`, capped at 60 columns, with `paddingLeft/Right=2` and
 * `paddingTop/Bottom=1`, on the raised panel surface, bordered `["left","right"]`
 * with the split border characters and tinted by the toast variant.
 *
 * ATROPOS has no absolute-positioning compositor, so this renders the same
 * chrome as a block the caller overlays onto a [ScreenFrame] at the same
 * offsets. Composition stays with the layout; this file only produces the box.
 */
class ToastRenderer(
    private val theme: TerminalTheme
) {
    /** Whether a toast is still within its display window. */
    fun isVisible(toast: Toast, nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        nowEpochMs - toast.raisedAtEpochMs < VISIBLE_MS

    /**
     * Renders the toast box. Returns an empty list once expired, so a caller
     * can drop it without special-casing.
     */
    fun render(
        toast: Toast,
        terminalWidth: Int,
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<String> {
        if (!isVisible(toast, nowEpochMs)) return emptyList()

        val boxWidth = minOf(MAX_WIDTH, terminalWidth - MARGIN_RIGHT * 2).coerceAtLeast(MIN_WIDTH)
        val inner = boxWidth - 2 - PADDING_X * 2
        if (inner < 4) return emptyList()

        val edgeGlyph = if (asciiOnly()) Glyphs.Ascii.RAIL else Glyphs.RAIL
        val edge = theme.paint(toast.variant.role, edgeGlyph)
        val pad = " ".repeat(PADDING_X)

        fun row(content: String): String =
            edge + pad + TerminalText.padEnd(TerminalText.ellipsize(content, inner), inner) + pad + edge

        return buildList {
            add(row(""))
            toast.title?.takeIf { it.isNotBlank() }?.let {
                add(row(theme.strong(it)))
                add(row(""))
            }
            wrap(TerminalText.sanitize(toast.message), inner).forEach { add(row(theme.metadata(it))) }
            add(row(""))
        }
    }

    /** Left column the toast should be drawn at, right-aligned per the reference. */
    fun leftColumn(terminalWidth: Int): Int {
        val boxWidth = minOf(MAX_WIDTH, terminalWidth - MARGIN_RIGHT * 2).coerceAtLeast(MIN_WIDTH)
        return (terminalWidth - boxWidth - MARGIN_RIGHT).coerceAtLeast(0)
    }

    /** Row the toast should start at. The reference pins to `top=2`. */
    fun topRow(): Int = MARGIN_TOP

    private fun wrap(text: String, width: Int): List<String> {
        if (text.length <= width) return listOf(text)
        val out = mutableListOf<String>()
        val current = StringBuilder()
        text.split(" ").forEach { word ->
            if (current.isNotEmpty() && current.length + 1 + word.length > width) {
                out += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out.take(MAX_LINES)
    }

    private fun asciiOnly(): Boolean = !System.getenv("ATROPOS_ASCII").isNullOrBlank()

    private companion object {
        /** Reference caps the toast at 60 columns. */
        const val MAX_WIDTH = 60
        const val MIN_WIDTH = 24
        const val MARGIN_RIGHT = 2
        const val MARGIN_TOP = 2
        const val PADDING_X = 2
        const val MAX_LINES = 4
        const val VISIBLE_MS = 6_000L
    }
}
