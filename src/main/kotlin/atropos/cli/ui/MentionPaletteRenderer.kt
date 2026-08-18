/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Glyphs
import atropos.cli.ui.design.Role

/**
 * The files an `@` could mean, shown while it is being typed.
 *
 * `/` opened a list of every matching command; `@` opened nothing. So the two
 * halves of the same idea behaved differently -- one taught you what was
 * available and the other required you to already know the path -- and an
 * operator attaching a document had no way to tell, before pressing Enter,
 * whether the engine could even see the file they were naming.
 *
 * Candidates arrive from [atropos.cli.input.CommandCompleter], which is
 * already the one thing that walks the granted roots. Nothing here touches the
 * filesystem: a second walk would be a second answer to "which files exist",
 * and the two would disagree the moment the territory rules changed.
 */
class MentionPaletteRenderer(private val theme: TerminalTheme) {

    fun render(fragment: String?, options: List<String>, selected: Int, width: Int, maximumRows: Int): List<String> {
        if (fragment == null || maximumRows <= 0) return emptyList()

        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val safeWidth = width.coerceAtLeast(1)

        if (options.isEmpty()) {
            // Said out loud rather than left blank. An empty panel and no panel
            // at all look identical, and they mean opposite things: one is "no
            // file matches", the other is "nothing is looking".
            return listOf(
                TerminalText.padEnd(
                    theme.paint(Role.BRAND, pad + "Files") + theme.subdued("  no match for '$fragment'"),
                    safeWidth
                ),
                KeyboardLegend.line(theme, KeyboardLegend.Surface.MENTION, safeWidth)
            )
        }

        val rowBudget = (maximumRows - 2).coerceAtLeast(1)
        val index = selected.coerceIn(0, options.lastIndex)
        val start = if (options.size <= rowBudget) 0 else (index - rowBudget / 2).coerceIn(0, options.size - rowBudget)

        return buildList {
            add(
                TerminalText.padEnd(
                    theme.paint(Role.BRAND, pad + "Files") + theme.subdued("  ${options.size}"),
                    safeWidth
                )
            )
            options.drop(start).take(rowBudget).forEachIndexed { offset, name ->
                add(row(name, start + offset == index, safeWidth))
            }
            add(KeyboardLegend.line(theme, KeyboardLegend.Surface.MENTION, safeWidth))
        }
    }

    private fun row(name: String, selected: Boolean, width: Int): String {
        // A trailing slash is how the completer marks a directory, and it is
        // the one distinction that changes what pressing Tab does next.
        val isDirectory = name.endsWith("/")
        val icon = if (isDirectory) "▸" else "·"
        val body = "  $icon " + TerminalText.ellipsize(name, (width - 4).coerceAtLeast(1))

        return if (selected) {
            theme.paint(Role.ACCENT_SELECTION, TerminalText.padEnd(body, width))
        } else {
            TerminalText.padEnd(
                if (isDirectory) theme.metadata(body) else theme.strong(body),
                width
            )
        }
    }
}
