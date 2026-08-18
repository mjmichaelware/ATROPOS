/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

class LandingRenderer(
    private val theme: TerminalTheme
) {

    /** Below this the wordmark and one tip are all that fit honestly. */
    private val MINIMUM_ROWS_FOR_DETAIL = 20
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

        // The home screen used to end here, leaving two thirds of the display
        // black. Minimal is right while you are working -- this panel is
        // rendered only when the transcript is empty, so the moment a message
        // arrives it is gone -- but an empty screen on first launch teaches an
        // operator nothing and impresses nobody. What fills it has to earn the
        // rows: where you are, what is configured, and what to press.
        if (targetHeight >= MINIMUM_ROWS_FOR_DETAIL) {
            out += ""
            out += sectionRule("SESSION", width)
            out += facts(state, width)
            out += ""
            out += sectionRule("START HERE", width)
            out += starters(width)
            out += ""
            out += KeyboardLegend.line(theme, KeyboardLegend.Surface.COMPOSER, width)
        }

        return out.take(targetHeight).map { TerminalText.ellipsize(it, width) }
    }

    /** `── SECTION ──────`, so the block reads as one region and not a list. */
    private fun sectionRule(label: String, width: Int): String {
        val head = " " + label + " "
        val fill = (width - TerminalText.cellWidth(head) - 2).coerceAtLeast(0)
        return theme.subdued("─") + theme.paint(Role.BRAND, head) + theme.subdued("─".repeat(fill))
    }

    /**
     * What this session is, read live.
     *
     * Facts only. A home screen that announced a health state nothing had
     * checked would be a fake attestation on the first screen an operator ever
     * sees, which is the worst possible place for one.
     */
    private fun facts(state: SessionPresentationState, width: Int): List<String> {
        val rows = buildList {
            add("workspace" to TerminalText.compactPath(state.workspace))
            add("provider" to state.provider.lowercase())
            add("mode" to state.mode.lowercase())
            // Only when git actually answered. "branch: unknown" is noise
            // dressed as information, and this panel is the first screen an
            // operator reads.
            state.repository.takeIf { it.isRepository }?.let { repository ->
                val branch = repository.branch ?: "detached"
                val changes = repository.changedFiles
                    ?.let { count -> if (count == 0) "clean" else "$count changed" }
                    ?: "status unknown"
                add("repository" to "$branch · $changes")
            }
            add("tabs" to "${state.openTabCount} open · ${state.activeTab}")
        }
        val column = rows.maxOf { it.first.length }
        return rows.map { (label, value) ->
            "  " + theme.subdued(TerminalText.padEnd(label, column)) + "  " + theme.strong(value)
        }
    }

    /** Three things worth typing first, with what each one is for. */
    private fun starters(width: Int): List<String> {
        val rows = listOf(
            "/factory run <prompt>" to "turn a document or an idea into a DAG",
            "@path/to/file" to "attach a document — txt, md, docx, pdf",
            "/status" to "providers, quota, and what is configured",
            "/shortcuts" to "every keyboard shortcut"
        )
        val column = rows.maxOf { it.first.length }
        return rows.map { (command, purpose) ->
            "  " + theme.paint(Role.ACCENT_FOCUS, TerminalText.padEnd(command, column)) +
                "  " + theme.subdued(purpose)
        }
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

    private fun logo(width: Int): List<String> {
        // Clean 3-row block letterforms. `_` marks a letter counter (the
        // enclosed hole) and renders as a shadow-filled cell, not a gap — the
        // reference's technique. Drawing it blank is what made the wordmark
        // read as disconnected chunks.
        val full = listOf(
            "█▀█ ▀█▀ █▀█ █▀█ █▀█ █▀█ █▀▀",
            "█▀█  █  █▀▄ █_█ █▀▀ █_█ ▀▀█",
            "▀ ▀  ▀  ▀ ▀ ▀▀▀ ▀   ▀▀▀ ▀▀▀"
        )
        val compact = listOf(
            "█▀█ ▀█▀ █▀█",
            "█▀█  █  █▀▄",
            "▀ ▀  ▀  ▀ ▀"
        )

        val lines = when {
            width >= 30 -> full
            width >= 14 -> compact
            else -> return listOf(theme.paint(Role.BRAND, "ATROPOS"))
        }

        // Reference technique: muted left half, bright right half.
        val split = lines.first().length / 2
        return lines.map { line ->
            TerminalText.ellipsize(
                renderMarks(line.take(split), Role.BRAND_MUTED) +
                    renderMarks(line.drop(split), Role.BRAND),
                width
            )
        }
    }

    /**
     * Expands mark characters into shaded cells.
     *
     * `_` is a letter counter: a filled shadow cell, never whitespace. The
     * reference draws it as a space over a shadow background; with a
     * foreground-only palette the equivalent is a dim block, which keeps the
     * letterform closed instead of punching a hole through it.
     */
    private fun renderMarks(segment: String, role: Role): String =
        segment.map { ch ->
            when (ch) {
                '_' -> theme.paint(Role.TEXT_MUTED, "█")
                '^' -> theme.paint(role, "▀")
                '~' -> theme.paint(Role.TEXT_MUTED, "▀")
                ',' -> theme.paint(Role.TEXT_MUTED, "▄")
                ' ' -> " "
                else -> theme.paint(role, ch.toString())
            }
        }.joinToString("")
}
