/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.help

import atropos.cli.ui.TerminalText
import atropos.cli.ui.TerminalTheme
import atropos.cli.ui.design.Role

/**
 * ATOM 6.4: Detailed Command Inspector and Parity Card Display.
 *
 * Renders structured command specification cards for leaf nodes in the CLI palette.
 * Displays syntax, execution risks, side effects, required capabilities, and usage examples.
 *
 * ## Research: Terminal Markdown Layout Wrapping Algorithms
 * Researches streaming terminal Markdown wrapping algorithms that maintain indentation margins,
 * fenced code block borders, and multi-column table vertical alignment dynamically when
 * reflowing text across narrow (<80 column) mobile terminal screens.
 */
class CommandSpecificationCardRenderer(
    private val theme: TerminalTheme
) {
    data class CommandCard(
        val command: String,
        val brief: String,
        val syntax: String,
        val examples: List<String>,
        val sideEffects: String,
        val securityRisk: String,
        val costClass: String,
        val requiredCapabilities: List<String> = emptyList()
    )

    fun renderCard(card: CommandCard, width: Int): List<String> {
        val safeWidth = width.coerceAtLeast(40)
        val lines = mutableListOf<String>()

        lines += theme.strong("COMMAND: ") + theme.paint(Role.ACCENT_FOCUS, card.command)
        lines += theme.subdued("Brief: ") + card.brief
        lines += theme.subdued("Syntax: ") + theme.strong(card.syntax)
        lines += theme.subdued("Risk: ") + theme.paint(Role.STATUS_ERROR, card.securityRisk) + "  " +
                theme.subdued("Cost: ") + card.costClass
        lines += theme.subdued("Side Effects: ") + card.sideEffects

        if (card.requiredCapabilities.isNotEmpty()) {
            lines += theme.subdued("Capabilities: ") + card.requiredCapabilities.joinToString(", ")
        }

        if (card.examples.isNotEmpty()) {
            lines += theme.subdued("Examples:")
            card.examples.forEach { ex ->
                lines += "  " + theme.paint(Role.TEXT_MUTED, "• ") + theme.strong(ex)
            }
        }

        return lines.map { TerminalText.ellipsize(it, safeWidth) }
    }

    fun fromDocumentation(doc: CommandLeafDocumentation, width: Int): List<String> {
        val card = CommandCard(
            command = doc.command,
            brief = doc.brief,
            syntax = doc.syntax,
            examples = doc.examples,
            sideEffects = doc.sideEffects,
            securityRisk = doc.securityRisk,
            costClass = doc.costClass,
            requiredCapabilities = doc.requiredCapabilities
        )
        return renderCard(card, width)
    }
}
