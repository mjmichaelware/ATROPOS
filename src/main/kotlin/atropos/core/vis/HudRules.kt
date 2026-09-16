/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.vis

import atropos.cli.ui.SessionPresentationState
import atropos.cli.ui.Sparkline
import atropos.cli.ui.TerminalText
import atropos.cli.ui.chrome.CheckpointAge
import atropos.cli.ui.design.ThemePalette

/**
 * F-VIS-012: HUD Rules
 *
 * Status/checkpoint peripheral; never steals center from work.
 *
 * Rules:
 * 1. Status bar lives at bottom, never covers content
 * 2. Checkpoint chip is primary action in footer, never a modal
 * 3. Provider/mode/tab pills shed right-to-left (directory never first lost)
 * 4. Checkpoint age shown as [🏁 4m] chip in footer
 * 5. Recovery ribbon appears above content, dismissible
 * 6. Engine status banner at top, never pushes content down
 * 7. Toast/notification peripheral, never modal unless critical
 * 8. Checkpoint resume panel replaces content, not a dialog
 *
 * Depends on: S-001 (six answers), S-007 (checkpoint age)
 */
class HudRules(
    private val theme: atropos.cli.ui.design.ThemePalette
) {

    private fun checkpointChip(age: atropos.cli.ui.chrome.CheckpointAge, width: Int): String {
        val label = when (age) {
            is atropos.cli.ui.chrome.CheckpointAge.Known -> age.label()
            atropos.cli.ui.chrome.CheckpointAge.Unknown -> "unknown"
            atropos.cli.ui.chrome.CheckpointAge.Skewed -> "skew"
        }
        return theme.subdued("[🏁 $label]")
    }

    /** HUD element positions - compile-time truth */
    enum class Position {
        TOP_BANNER,      // Engine status banner - fixed top
        RECOVERY_RIBBON, // Recovery ribbon - below top banner
        CONTENT,         // Main content area (hero)
        FOOTER,          // Footer with pills + checkpoint chip
        TOAST_PERIPHERAL // Toast/notifications - peripheral
    }

    /** HUD element types */
    enum class Element(
        val position: Position,
        val isPeripheral: Boolean,
        val maxHeight: Int = Int.MAX_VALUE
    ) {
        ENGINE_STATUS_BANNER(Position.TOP_BANNER, false, 1),
        RECOVERY_RIBBON(Position.RECOVERY_RIBBON, false, 3),
        CHECKPOINT_CHIP(Position.FOOTER, false, 1),
        PROVIDER_PILLS(Position.FOOTER, false, 1),
        MODE_PILL(Position.FOOTER, false, 1),
        TAB_PILL(Position.FOOTER, false, 1),
        BRANCH_PILL(Position.FOOTER, false, 1),
        TOKEN_PILL(Position.FOOTER, false, 1),
        COST_SPARKLINE(Position.FOOTER, false, 1),
        PATCH_PILL(Position.FOOTER, false, 1),
        RECOVERY_INDICATOR(Position.FOOTER, false, 1),
        HELP_AFFORDANCE(Position.FOOTER, false, 1),
        RECOVERY_RIBBON_ELEMENT(Position.RECOVERY_RIBBON, false, 3),
        TOAST(Position.TOAST_PERIPHERAL, true, 4),
        NOTIFICATION(Position.TOAST_PERIPHERAL, true, 4);
    }

    /** The HUD layout contract - compile-time truth */
    val layout: List<Element> = listOf(
        Element.ENGINE_STATUS_BANNER,
        Element.RECOVERY_RIBBON_ELEMENT,
        Element.PROVIDER_PILLS,
        Element.MODE_PILL,
        Element.TAB_PILL,
        Element.BRANCH_PILL,
        Element.TOKEN_PILL,
        Element.COST_SPARKLINE,
        Element.PATCH_PILL,
        Element.RECOVERY_INDICATOR,
        Element.HELP_AFFORDANCE,
        Element.CHECKPOINT_CHIP
    )

    /** Render HUD footer with proper pill shedding */
    fun renderFooter(
        state: SessionPresentationState,
        width: Int,
        checkpointAge: CheckpointAge
    ): String {
        val safeWidth = width.coerceAtLeast(20)

        val directory = TerminalText.compactPath(state.workspace)
        val tab = "${TerminalText.sanitize(state.activeTab)}:${TerminalText.sanitize(state.activeScreen)}"

        // Right-hand pills in priority order (least important last = shed first)
        val pills = buildList {
            // Provider - highest priority (never shed unless absolutely necessary)
            add(theme.metadata("◆ ") + theme.strong(state.provider.lowercase()))
            // Mode
            add(theme.metadata("▸ ") + theme.strong(state.mode.lowercase()))
            // Tab
            add(theme.metadata("▤ ") + theme.strong(tab))
            // Branch (if repo available)
            if (state.repository.available) {
                state.repository.branch?.let { branch ->
                    val dirty = state.repository.clean == false
                    val marker = if (dirty) "!" else "✓"
                    add(theme.metadata("$marker ") + theme.strong(TerminalText.ellipsize(branch, 20)))
                }
            }
            // Tokens
            state.tokens.text().takeIf { it != "--" }?.let {
                add(theme.metadata("⋯ ") + theme.strong("$it tok"))
            }
            // Cost sparkline (6 cells)
            Sparkline.render(state.costHistory, 6)
                .takeIf(String::isNotEmpty)
                ?.let { add(theme.metadata("$ ") + theme.strong(it)) }
            // Active patch
            state.activePatchId?.takeIf { it.isNotBlank() }?.let {
                add(theme.metadata("⊙ ") + theme.strong(TerminalText.ellipsize(it, 18)))
            }
            // Active operation / recovery
            state.activeOperation
                ?.let(TerminalText::sanitize)
                ?.takeIf(String::isNotBlank)
                ?.let {
                    if (it.contains("recovery", ignoreCase = true)) {
                        // Recovery ribbon reference
                    } else {
                        add(theme.warning("△ ") + theme.strong(it))
                    }
                }
            // Checkpoint chip - PRIMARY ACTION
            add(checkpointChip(CheckpointAge.Unknown, safeWidth))
            // Help affordance - always last
            add(theme.subdued("/help"))
        }

        // Directory (left) - never shed
        val left = theme.subdued(directory)

        // Shed pills from LEFT of right-hand group (least important first)
        var kept = pills
        var right = kept.joinToString("  ")

        fun fits() = TerminalText.cellWidth(left) + 2 + TerminalText.cellWidth(right) <= safeWidth

        while (kept.size > 1 && !fits()) {
            kept = kept.drop(1)
            right = kept.joinToString("  ")
        }

        val gap = (safeWidth - TerminalText.cellWidth(left) - TerminalText.cellWidth(right)).coerceAtLeast(1)
        val line = if (fits()) left + " ".repeat(gap) + right else TerminalText.ellipsize(left, safeWidth)

        return TerminalText.padEnd(TerminalText.ellipsize(line, safeWidth), safeWidth)
    }

    /**
     * Check if HUD layout is valid - footer never overlaps content
     */
    fun validateLayout(contentHeight: Int, footerHeight: Int, totalHeight: Int): Boolean {
        return contentHeight + footerHeight <= totalHeight
    }

    /**
     * Get elements for a specific position
     */
    fun elementsAt(position: Position): List<Element> {
        return Element.values().filter { it.position == position }
    }

    /**
     * Get all peripheral elements (toasts, notifications)
     */
    val peripheralElements: List<Element> = Element.values().filter { it.isPeripheral }

    /**
     * Render the HUD rules as markdown for documentation
     */
    fun renderRulesMarkdown(): String {
        val sb = StringBuilder()
        sb.appendLine("# HUD Layout Rules (F-VIS-012)")
        sb.appendLine()
        sb.appendLine("## Element Positions")
        sb.appendLine()
        for (pos in Position.values()) {
            val elements = elementsAt(pos)
            sb.appendLine("### ${pos.name}")
            sb.appendLine()
            for (el in elements) {
                sb.appendLine("- ${el.name} (peripheral: ${el.isPeripheral})")
            }
            sb.appendLine()
        }
        sb.appendLine("## Rules")
        sb.appendLine()
        sb.appendLine("1. Status bar lives at bottom, never covers content")
        sb.appendLine("2. Checkpoint chip is primary action in footer, never a modal")
        sb.appendLine("3. Provider/mode/tab pills shed right-to-left (directory never first lost)")
        sb.appendLine("4. Checkpoint age shown as [🏁 4m] chip in footer")
        sb.appendLine("5. Recovery ribbon appears above content, dismissible")
        sb.appendLine("6. Engine status banner at top, never pushes content down")
        sb.appendLine("7. Toast/notification peripheral, never modal unless critical")
        sb.appendLine("8. Checkpoint resume panel replaces content, not a dialog")
        return sb.toString()
    }
}