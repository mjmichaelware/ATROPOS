/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Breakpoint
import atropos.cli.ui.design.Glyphs
import atropos.cli.ui.design.Role

/** One selectable dialog row. */
data class DialogOption(
    val label: String,
    val detail: String? = null,
    val enabled: Boolean = true
)

/**
 * Modal dialog in the pinned reference's shape.
 *
 * The reference renders a dialog as a centred panel on the raised panel
 * surface, opened a quarter of the way down the viewport (`paddingTop =
 * height / 4`) over a dimmed backdrop, with `paddingTop=1` inside and a title
 * above the option list. The selected row carries the accent selection fill.
 *
 * ATROPOS has no alpha compositor, so the backdrop dim is expressed by drawing
 * the panel opaque and leaving surrounding rows untouched — the same spatial
 * result without faking translucency. Source Doc 3 Section A requires exactly
 * this for confirm-destructive surfaces anyway: "Glass is not applied to modals
 * confirming destructive actions ... Those stay opaque, high-contrast, flat."
 */
class DialogRenderer(
    private val theme: TerminalTheme
) {
    /** Row the dialog should start at: a quarter down, per the reference. */
    fun topRow(terminalHeight: Int): Int = (terminalHeight / 4).coerceAtLeast(1)

    /** Left column for a centred panel. */
    fun leftColumn(terminalWidth: Int): Int =
        ((terminalWidth - panelWidth(terminalWidth)) / 2).coerceAtLeast(0)

    fun panelWidth(terminalWidth: Int): Int = when (Breakpoint.of(terminalWidth)) {
        // Phone: full bleed minus a one-column gutter, so nothing is clipped.
            Breakpoint.COMPACT -> (terminalWidth - 2).coerceAtLeast(1)
            Breakpoint.MEDIUM -> (terminalWidth * 3 / 4).coerceAtLeast(1)
            else -> minOf(MAX_WIDTH, terminalWidth * 2 / 3).coerceAtLeast(1)
    }

    /**
     * Renders the dialog panel.
     *
     * @param selectedIndex which option carries the selection fill.
     * @param footerHint key affordances, e.g. "enter select · esc cancel".
     */
    fun render(
        title: String,
        options: List<DialogOption>,
        selectedIndex: Int,
        terminalWidth: Int,
        maximumRows: Int = 10,
        footerHint: String? = "enter select · esc cancel"
    ): List<String> {
        val width = panelWidth(terminalWidth)
        val inner = width - PADDING_X * 2
        if (inner < 8) return emptyList()

        val pad = " ".repeat(PADDING_X)
        fun row(content: String): String =
            pad + TerminalText.padEnd(TerminalText.ellipsize(content, inner), inner) + pad

        val selected = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
        val visible = options.take(maximumRows)

        return buildList {
            add(row(""))
            add(row(theme.paint(Role.BRAND, title) + theme.subdued("  ${options.size}")))
            add(row(""))

            if (visible.isEmpty()) {
                add(row(theme.subdued("no matches")))
            } else {
                visible.forEachIndexed { index, option ->
                    add(row(optionLine(option, index == selected, inner)))
                }
                if (options.size > visible.size) {
                    add(row(theme.subdued("+${options.size - visible.size} more")))
                }
            }

            footerHint?.takeIf { it.isNotBlank() }?.let {
                add(row(""))
                add(row(theme.subdued(it)))
            }
            add(row(""))
        }
    }

    private fun optionLine(option: DialogOption, selected: Boolean, inner: Int): String {
        val label = option.label
        val detail = option.detail

        val body = when {
            detail.isNullOrBlank() -> label
            else -> {
                val room = (inner - label.length - 2).coerceAtLeast(0)
                if (room < 4) label else label + "  " + TerminalText.ellipsize(detail, room)
            }
        }

        return when {
            !option.enabled -> theme.subdued(body)
            selected -> theme.paint(Role.ACCENT_SELECTION, TerminalText.padEnd(body, inner))
            detail.isNullOrBlank() -> theme.strong(body)
            else -> theme.strong(label) + theme.subdued(body.removePrefix(label))
        }
    }

    /**
     * Confirmation dialog for a destructive action.
     *
     * Source Doc 3 Section F open decision 2 leaves the full confirm table
     * unresolved, so callers decide when to raise this; the renderer only
     * guarantees the surface is opaque and high-contrast, never glass.
     */
    fun renderConfirm(
        title: String,
        body: String,
        confirmLabel: String,
        cancelLabel: String,
        confirmSelected: Boolean,
        terminalWidth: Int
    ): List<String> = render(
        title = title,
        options = listOf(
            DialogOption(confirmLabel, body),
            DialogOption(cancelLabel)
        ),
        selectedIndex = if (confirmSelected) 0 else 1,
        terminalWidth = terminalWidth,
        footerHint = "enter confirm · esc cancel"
    )

    private companion object {
        const val MAX_WIDTH = 72
        const val PADDING_X = 2
    }
}
