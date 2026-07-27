/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

class LandingRenderer(
    private val theme: TerminalTheme
) {
    private val truthProbe = WorkbenchTruthProbe()
    private val agentProbe = AgentWorkbenchProbe()

    fun render(state: SessionPresentationState, terminalWidth: Int): List<String> =
        render(state, terminalWidth, 32)

    /**
     * Home view in the pinned reference's shape: the wordmark, then a single
     * rotating tip line, then breathing room. The reference's home is
     * deliberately minimal — it is a session-first screen, not a status grid,
     * so live status lives behind `/status`, `/providers` and friends rather
     * than competing with the prompt.
     */
    fun render(state: SessionPresentationState, terminalWidth: Int, terminalHeight: Int): List<String> {
        val width = terminalWidth.coerceAtLeast(20)
        val targetHeight = terminalHeight.coerceAtLeast(6)

        val out = mutableListOf<String>()
        out += ""
        out += logo(width)
        out += ""
        out += tipLine(state, width)

        return out.take(targetHeight).map { TerminalText.ellipsize(it, width) }
    }

    /**
     * `● Tip <text>` — the reference renders a warning-toned bullet, then the
     * tip with highlighted spans in full-contrast ink and the remainder muted.
     * Tips rotate deterministically so the home view is never static but never
     * animates either.
     */
    private fun tipLine(state: SessionPresentationState, width: Int): String {
        val tips = listOf(
            "Type {/} to open the command palette",
            "Start a message with {!} to run a shell command (e.g. {!ls -la})",
            "Use {/agent patch <task>} to have a provider draft a diff ATROPOS applies",
            "Use {/agent apply --check latest} to validate a patch before applying it",
            "Use {/tabs} to list open tabs and {/tab new <name>} to open one",
            "Press {ctrl+t} for a new tab, {ctrl+tab} to cycle between them",
            "Use {/home} from anywhere to return to this screen",
            "Use {/providers} to see which providers are configured and free",
            "Use {/verify narrow} for a fast toolchain check before you commit",
            "Paid providers stay locked; {/paid status} shows the gate"
        )
        val tip = tips[(state.workspace.hashCode().toLong().mod(tips.size.toLong())).toInt()]

        val body = buildString {
            var rest = tip
            while (true) {
                val open = rest.indexOf('{')
                val close = rest.indexOf('}', startIndex = open + 1)
                if (open < 0 || close < 0) {
                    append(theme.subdued(rest))
                    break
                }
                append(theme.subdued(rest.substring(0, open)))
                append(theme.strong(rest.substring(open + 1, close)))
                rest = rest.substring(close + 1)
            }
        }
        return TerminalText.ellipsize(theme.warning("● Tip ") + body, width)
    }

    /**
     * ATROPOS wordmark in the pinned reference's logo language: block letters
     * split into a muted left half and a bright bold right half, with the
     * reference's mark characters standing in for shaded cells —
     * `_` a shadow cell, `^` an upper half-block, `~` a shadowed upper half,
     * `,` a shadowed lower half.
     *
     * ATROPOS keeps its own wordmark and cyan identity; only the rendering
     * technique is shared. Degrades by height so the logo is never clipped on a
     * phone (requirement 28: the logo must never be cut off).
     */
    private fun logo(width: Int): List<String> {
        val full = listOf(
            "█▀▀█ ▀▀█▀▀ █▀▀█ █▀▀█ █▀▀█ █▀▀█ █▀▀▀",
            "█▄▄█ __█__ █▄▄▀ █__█ █▄▄█ █__█ ▀▀▀█",
            "▀__▀ ~~▀~~ ▀~~▀ ▀▀▀▀ ▀~~~ ▀▀▀▀ ▀▀▀▀"
        )
        val medium = listOf(
            "█▀▀█ ▀▀█▀▀ █▀▀█",
            "█▄▄█ __█__ █▄▄▀",
            "▀__▀ ~~▀~~ ▀~~▀"
        )

        val lines = when {
            width >= 44 -> full
            width >= 24 -> medium
            else -> return listOf(theme.paint(Role.BRAND, "ATROPOS"))
        }

        // Reference technique: left portion muted, right portion bright+bold.
        val splitAt = lines.first().length / 2
        return lines.map { line ->
            val left = renderMarks(line.take(splitAt), Role.BRAND_MUTED)
            val right = renderMarks(line.drop(splitAt), Role.BRAND)
            TerminalText.ellipsize(left + right, width)
        }
    }

    /** Expands the reference's `_^~,` mark characters into shaded cells. */
    private fun renderMarks(segment: String, role: Role): String =
        segment.map { ch ->
            when (ch) {
                '_' -> theme.paint(Role.TEXT_MUTED, " ")
                '^' -> theme.paint(role, "▀")
                '~' -> theme.paint(Role.TEXT_MUTED, "▀")
                ',' -> theme.paint(Role.TEXT_MUTED, "▄")
                ' ' -> " "
                else -> theme.paint(role, ch.toString())
            }
        }.joinToString("")
}
