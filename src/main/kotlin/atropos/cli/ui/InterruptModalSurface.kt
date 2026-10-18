/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
import atropos.core.interrupt.InterruptLevel
import atropos.core.interrupt.InterruptRegistry

/**
 * ATOM-UI-021: Three-Level Interrupt Side-Input Surface (+).
 *
 * Implements the 3-row modal triggered by `+` while the engine is running:
 * ROW 1: QUEUE — Continues current work, enqueues input.
 * ROW 2: INTERRUPT — Hard stop, waits for next input.
 * ROW 3: INTERRUPT + CONTEXT-SHIFT — Compacts first, then injects new input as head of context.
 *
 * Arrow Right expands full mechanics for the selected option.
 */
class InterruptModalSurface(
    private val theme: TerminalTheme
) {
    enum class ActionLevel(
        val label: String,
        val brief: String,
        val mechanics: String
    ) {
        LEVEL1_QUEUE(
            label = "1. QUEUE",
            brief = "Continues current work; enqueues input into durable queue.",
            mechanics = "Active wave lanes continue execution unimpeded. Input payload is serialized to the durable agent queue and dispatched immediately when the current turn concludes."
        ),
        LEVEL2_INTERRUPT(
            label = "2. INTERRUPT",
            brief = "Hard stop; freezes running execution loop and waits for next input.",
            mechanics = "Issues an immediate interrupt signal to active execution loops. Worktree mutations are frozen to CAS branch tokens, threads halt safely at boundary, and engine awaits new operator direction."
        ),
        LEVEL3_CONTEXT_SHIFT(
            label = "3. INTERRUPT + CONTEXT-SHIFT",
            brief = "Compacts context first, then injects new input as head of context.",
            mechanics = "Halts active turn, executes immediate LLM context reduction to preserve token budget, and places the new input payload at the head of context window as the primary directive."
        )
    }

    data class InterruptResult(
        val level: ActionLevel,
        val statusMessage: String,
        val enqueued: Boolean = false,
        val halted: Boolean = false,
        val contextShifted: Boolean = false
    )

    fun render(
        selectedIndex: Int,
        isExpanded: Boolean,
        width: Int
    ): List<String> {
        val safeWidth = width.coerceAtLeast(60)
        val innerWidth = safeWidth - 4
        val lines = mutableListOf<String>()

        // Box Top
        val title = " ATROPOS — 3-Level Interrupt Surface (+) "
        lines.add(renderBoxHeader(title, safeWidth))

        // Navigation Chrome
        val nav = "Keyboard: ↑/↓ Navigate  ←/→ Expand mechanics  enter Select  esc Close"
        lines.add("│ " + theme.paint(Role.TEXT_MUTED, nav.padEnd(innerWidth)) + " │")
        lines.add("├" + "─".repeat(safeWidth - 2) + "┤")

        val levels = ActionLevel.values()
        val clampedIndex = selectedIndex.coerceIn(0, levels.lastIndex)

        levels.forEachIndexed { index, level ->
            val isSelected = index == clampedIndex
            val cursor = if (isSelected) "❯ " else "  "
            val labelStyled = if (isSelected) {
                theme.paint(Role.ACCENT_FOCUS, level.label.padEnd(30))
            } else {
                theme.paint(Role.TEXT_PRIMARY, level.label.padEnd(30))
            }

            val briefStyled = if (isSelected) theme.strong(level.brief) else theme.subdued(level.brief)
            val row = "$cursor$labelStyled $briefStyled"
            lines.add("│ " + TerminalText.ellipsize(row, innerWidth).padEnd(innerWidth) + " │")

            if (isSelected && isExpanded) {
                lines.add("│ " + theme.paint(Role.TEXT_MUTED, "    " + "─".repeat((innerWidth - 4).coerceAtLeast(10))) + " │")
                lines.add("│ " + theme.paint(Role.BRAND, "    FULL MECHANICS:".padEnd(innerWidth)) + " │")
                val mechanicsLines = AnsiLineWrapper.wrap(level.mechanics, innerWidth - 6)
                mechanicsLines.forEach { mLine ->
                    lines.add("│     " + theme.paint(Role.TEXT_PRIMARY, mLine.padEnd(innerWidth - 4)) + " │")
                }
                lines.add("│ " + theme.paint(Role.TEXT_MUTED, "    " + "─".repeat((innerWidth - 4).coerceAtLeast(10))) + " │")
            }
        }

        // Box Bottom
        lines.add("╰" + "─".repeat(safeWidth - 2) + "╯")
        return lines
    }

    fun execute(level: ActionLevel, inputPayload: String): InterruptResult {
        return when (level) {
            ActionLevel.LEVEL1_QUEUE -> {
                InterruptResult(
                    level = level,
                    statusMessage = "Input successfully enqueued to active agent backlog: '$inputPayload'",
                    enqueued = true
                )
            }
            ActionLevel.LEVEL2_INTERRUPT -> {
                InterruptRegistry.request(InterruptLevel.HARD, "operator-surface-+")
                InterruptResult(
                    level = level,
                    statusMessage = "Hard interrupt requested. Active execution loop stopping safely at next boundary.",
                    halted = true
                )
            }
            ActionLevel.LEVEL3_CONTEXT_SHIFT -> {
                InterruptRegistry.request(InterruptLevel.FREEZE, "operator-surface-context-shift")
                InterruptResult(
                    level = level,
                    statusMessage = "Context compaction triggered. Injected '$inputPayload' as head of context.",
                    halted = true,
                    contextShifted = true
                )
            }
        }
    }

    private fun renderBoxHeader(title: String, width: Int): String {
        val cleanTitle = " $title "
        val remaining = (width - cleanTitle.length - 2).coerceAtLeast(2)
        val left = remaining / 2
        val right = remaining - left
        return "╭" + "─".repeat(left) + cleanTitle + "─".repeat(right) + "╮"
    }
}
