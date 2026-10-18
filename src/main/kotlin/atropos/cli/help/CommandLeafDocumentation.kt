/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.help

import atropos.cli.ui.TerminalText
import atropos.cli.ui.TerminalTheme
import atropos.cli.ui.design.Role

/**
 * ATOM-HELP-018: Command Documentation Leaf & Progressive Disclosure Registry.
 *
 * Implements Tier 4 progressive disclosure for all commands:
 * Collapsed: Brief one-liner + key hint.
 * Expanded (Arrow Right): Syntax, examples, side-effects, related,
 * security/risk notes, evidence impact, cost class, required capabilities.
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
                brief = "Show comprehensive command catalog and guide",
                syntax = "/help [command|domain]",
                examples = listOf("/help", "/help /repo", "/help agent"),
                sideEffects = "None",
                related = listOf("/usage", "/?", "/commands"),
                securityRisk = "L0",
                evidenceImpact = "None",
                costClass = "FREE_LOCAL",
                requiredCapabilities = listOf("CAP_READ")
            )
        )

        register(
            CommandLeafDocumentation(
                command = "/status",
                brief = "Display engine, git worktree, and quota state",
                syntax = "/status [adapters|assets|endpoints|failures|quota|route]",
                examples = listOf("/status", "/status quota", "/status route"),
                sideEffects = "None",
                related = listOf("/doctor", "/providers"),
                securityRisk = "L0",
                evidenceImpact = "Audit receipt recorded",
                costClass = "FREE_LOCAL",
                requiredCapabilities = listOf("CAP_READ")
            )
        )

        register(
            CommandLeafDocumentation(
                command = "/diff",
                brief = "Show syntax-colored diff against CAS root",
                syntax = "/diff [--staged] [<path>]",
                examples = listOf("/diff", "/diff --staged", "/diff src/main/kotlin/App.kt"),
                sideEffects = "None",
                related = listOf("/repo diff", "/history"),
                securityRisk = "L0",
                evidenceImpact = "Merkle diff hash verified",
                costClass = "FREE_LOCAL",
                requiredCapabilities = listOf("CAP_READ")
            )
        )

        register(
            CommandLeafDocumentation(
                command = "/plan",
                brief = "Activate read-only plan mode without file mutations",
                syntax = "/plan",
                examples = listOf("/plan"),
                sideEffects = "Sets Agent_Mode to plan-only in atropos.config.json",
                related = listOf("/architect", "/goal"),
                securityRisk = "L0",
                evidenceImpact = "Config mutation receipt emitted",
                costClass = "FREE_LOCAL",
                requiredCapabilities = listOf("CAP_READ")
            )
        )

        register(
            CommandLeafDocumentation(
                command = "/architect",
                brief = "Activate 2-model planner gate: plan vs verify",
                syntax = "/architect",
                examples = listOf("/architect"),
                sideEffects = "Enables dual-model verification gate",
                related = listOf("/plan", "/verify"),
                securityRisk = "L1",
                evidenceImpact = "Planner attestation token created",
                costClass = "TIER1_PROVIDER",
                requiredCapabilities = listOf("CAP_READ", "CAP_DISPATCH")
            )
        )

        register(
            CommandLeafDocumentation(
                command = "/goal",
                brief = "Set long-horizon target and autonomous goal bounds",
                syntax = "/goal <objective>",
                examples = listOf("/goal implement dynamic model discovery"),
                sideEffects = "Registers active goal in project memory",
                related = listOf("/autonomous", "/plan"),
                securityRisk = "L1",
                evidenceImpact = "Goal objective hashed into CAS state",
                costClass = "FREE_LOCAL",
                requiredCapabilities = listOf("CAP_WRITE_MEMORY")
            )
        )

        register(
            CommandLeafDocumentation(
                command = "/compact",
                brief = "Execute LLM context reduction and context window pruning",
                syntax = "/compact",
                examples = listOf("/compact"),
                sideEffects = "Summarizes context history into working memory",
                related = listOf("/session compact", "/memory compact"),
                securityRisk = "L0",
                evidenceImpact = "Context compaction vector stored",
                costClass = "TIER1_PROVIDER",
                requiredCapabilities = listOf("CAP_READ", "CAP_WRITE_MEMORY")
            )
        )

        register(
            CommandLeafDocumentation(
                command = "/batch",
                brief = "Schedule parallel wave lanes with disjoint locks",
                syntax = "/batch <task-spec>",
                examples = listOf("/batch run-all"),
                sideEffects = "Fans out worker lanes under Director authority",
                related = listOf("/autonomous", "/agent run"),
                securityRisk = "L2",
                evidenceImpact = "Wave lock certificates signed",
                costClass = "PARALLEL_QUOTA",
                requiredCapabilities = listOf("CAP_DISPATCH", "CAP_WRITE_VFS")
            )
        )

        register(
            CommandLeafDocumentation(
                command = "/simplify",
                brief = "Refactor AST for complexity reduction",
                syntax = "/simplify [<target-path>]",
                examples = listOf("/simplify src/main/kotlin/Engine.kt"),
                sideEffects = "Generates AST simplification patch candidate",
                related = listOf("/verify", "/diff"),
                securityRisk = "L1",
                evidenceImpact = "AST mutation candidate hashed",
                costClass = "TIER1_PROVIDER",
                requiredCapabilities = listOf("CAP_READ", "CAP_WRITE_VFS")
            )
        )

        register(
            CommandLeafDocumentation(
                command = "/map",
                brief = "Semantic repository map across AST symbol lattices",
                syntax = "/map",
                examples = listOf("/map"),
                sideEffects = "None",
                related = listOf("/ast", "/repo"),
                securityRisk = "L0",
                evidenceImpact = "Symbol lattice snapshot recorded",
                costClass = "FREE_LOCAL",
                requiredCapabilities = listOf("CAP_READ")
            )
        )

        register(
            CommandLeafDocumentation(
                command = "/lint",
                brief = "Execute syntax and AST linters emitting diagnostic vectors",
                syntax = "/lint",
                examples = listOf("/lint"),
                sideEffects = "None",
                related = listOf("/verify", "/tests"),
                securityRisk = "L0",
                evidenceImpact = "Diagnostic report signed",
                costClass = "FREE_LOCAL",
                requiredCapabilities = listOf("CAP_READ")
            )
        )
    }
}
