/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.help

import atropos.cli.ui.TerminalText
import atropos.cli.ui.TerminalTheme
import atropos.cli.ui.design.Role

/**
 * ATOM-HELP-018 / ATOM 6.4: Command Documentation Leaf & Progressive Disclosure Registry.
 *
 * Implements Tier 4 progressive disclosure for all commands:
 * Collapsed: Brief one-liner + key hint.
 * Expanded (Arrow Right): Full syntax usage, practical examples matching Android app details,
 * side-effects, related commands, security/risk classification, evidence impact, cost class,
 * and required security capabilities.
 *
 * Research Directive: Terminal Markdown layout wrapping algorithms that dynamically preserve
 * code block margins and table alignment on narrow screen terminals utilize column-width-aware
 * tokenizers that break only at whitespace boundaries outside fenced verbatim blocks.
 */
data class CommandLeafDocumentation(
    val command: String,
    val brief: String,
    val syntax: String,
    val examples: List<String> = emptyList(),
    val sideEffects: String = "None (read-only query)",
    val related: List<String> = emptyList(),
    val securityRisk: String = "L0 (Standard user operation)",
    val evidenceImpact: String = "Tamper-evident verification receipt emitted to cas_evidence.jsonl",
    val costClass: String = "FREE_LOCAL",
    val requiredCapabilities: List<String> = listOf("CAP_READ")
)

object CommandLeafRegistry {
    private val leaves = mutableMapOf<String, CommandLeafDocumentation>()

    fun cardRenderer(theme: TerminalTheme): CommandSpecificationCardRenderer =
        CommandSpecificationCardRenderer(theme)

    init {
        bootstrapLeaves()
    }

    fun register(leaf: CommandLeafDocumentation) {
        leaves[leaf.command.trim().lowercase()] = leaf
    }

    fun get(command: String): CommandLeafDocumentation? =
        leaves[command.trim().lowercase()]

    fun getOrDefault(command: String, defaultBrief: String = "No description"): CommandLeafDocumentation {
        val clean = command.trim().lowercase()
        return leaves[clean] ?: CommandLeafDocumentation(
            command = command,
            brief = defaultBrief,
            syntax = command
        )
    }

    fun render(
        leaf: CommandLeafDocumentation,
        isExpanded: Boolean,
        theme: TerminalTheme,
        width: Int
    ): List<String> {
        val safeWidth = width.coerceAtLeast(40)
        val lines = mutableListOf<String>()

        val title = theme.paint(Role.ACCENT_FOCUS, leaf.command) + " — " + theme.paint(Role.TEXT_PRIMARY, leaf.brief)
        lines.add(TerminalText.ellipsize("  $title", safeWidth))

        if (!isExpanded) {
            val hint = theme.paint(Role.TEXT_MUTED, "  [Press Arrow Right to expand full documentation]")
            lines.add(hint)
            return lines
        }

        val indent = "    "
        lines.add(theme.paint(Role.TEXT_MUTED, "  " + "─".repeat((safeWidth - 4).coerceAtLeast(10))))
        lines.add(theme.paint(Role.BRAND, "  SYNTAX:") + " " + theme.paint(Role.CODE, leaf.syntax))

        if (leaf.examples.isNotEmpty()) {
            lines.add(theme.paint(Role.BRAND, "  EXAMPLES:"))
            leaf.examples.forEach { ex ->
                lines.add("$indent${theme.paint(Role.TEXT_PRIMARY, ex)}")
            }
        }

        lines.add(theme.paint(Role.BRAND, "  SIDE-EFFECTS:") + " " + theme.paint(Role.TEXT_PRIMARY, leaf.sideEffects))

        if (leaf.related.isNotEmpty()) {
            val rel = leaf.related.joinToString(", ") { theme.paint(Role.ACCENT_FOCUS, it) }
            lines.add(theme.paint(Role.BRAND, "  RELATED:") + " $rel")
        }

        lines.add(theme.paint(Role.BRAND, "  SECURITY RISK:") + " " + theme.paint(Role.TEXT_PRIMARY, leaf.securityRisk))
        lines.add(theme.paint(Role.BRAND, "  EVIDENCE IMPACT:") + " " + theme.paint(Role.TEXT_PRIMARY, leaf.evidenceImpact))
        lines.add(theme.paint(Role.BRAND, "  COST CLASS:") + " " + theme.paint(Role.TEXT_PRIMARY, leaf.costClass))
        lines.add(theme.paint(Role.BRAND, "  REQUIRED CAPABILITIES:") + " " + theme.paint(Role.TEXT_PRIMARY, leaf.requiredCapabilities.joinToString(", ")))
        lines.add(theme.paint(Role.TEXT_MUTED, "  " + "─".repeat((safeWidth - 4).coerceAtLeast(10))))

        return lines
    }

    private fun bootstrapLeaves() {
        register(
            CommandLeafDocumentation(
                command = "/help",
                brief = "Display command catalog and interactive cheat sheet",
                syntax = "/help [category|command]",
                examples = listOf("/help", "/help agent", "/help /self-host"),
                sideEffects = "None (read-only render)",
                related = listOf("/shortcuts", "/doctor", "/status"),
                securityRisk = "L0 (Safe)",
                costClass = "FREE_LOCAL"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/status",
                brief = "Inspect real-time engine, workspace, and model cascade status",
                syntax = "/status [adapters|assets|endpoints|failures|quota|route]",
                examples = listOf("/status", "/status route --full", "/status quota"),
                sideEffects = "None (read-only query)",
                related = listOf("/doctor", "/providers", "/health"),
                securityRisk = "L0 (Safe)",
                costClass = "FREE_LOCAL"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/flash",
                brief = "Switch active neural cascade to fast low-latency models",
                syntax = "/flash [status]",
                examples = listOf("/flash", "/flash status"),
                sideEffects = "Switches provider routing priority to low-latency models",
                related = listOf("/model", "/providers", "/status route"),
                securityRisk = "L1 (Configuration change)",
                costClass = "FREE_OR_TIERED"
            )
        )
        register(
            CommandLeafDocumentation(
                command = "/self-host",
                brief = "Execute autonomous Phase 11 self-build and verification loop",
                syntax = "/self-host [run|start|status|verify|promote] [goal]",
                examples = listOf("/self-host status", "/self-host run fix compilation", "/self-host verify"),
                sideEffects = "Mutates isolated project worktrees, runs verification, and promotes clean builds",
                related = listOf("/agent", "/verify", "/repo"),
                securityRisk = "L3 (Gated mutation and promotion)",
                costClass = "VARIABLE",
                requiredCapabilities = listOf("CAP_WRITE", "CAP_PROCESS_SPAWN")
            )
        )
    }
}
