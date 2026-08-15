/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * HOE-E08: JAR promote physical handoff affordance.
 * Previous JAR remains a recoverable shadow; new JAR seats only after green VerifiedCompletionGate.
 */
class JarPromoteRenderer(private val theme: TerminalTheme) {
    fun render(previousJarHash: String?, newJarHash: String, isVerified: Boolean): List<String> {
        val lines = mutableListOf<String>()
        lines.add(theme.format("┌── JAR PROMOTION HANDOFF ──┐", Role.MUTED))
        
        if (previousJarHash != null) {
            lines.add("│ Previous: ${previousJarHash.take(8)} (Shadow)  │")
        }
        
        if (isVerified) {
            lines.add("│ New:      ${newJarHash.take(8)}          │ " + theme.format("SEATED", Role.STATUS_VERIFIED))
        } else {
            lines.add("│ New:      ${newJarHash.take(8)}          │ " + theme.format("BLOCKED (Unverified)", Role.STATUS_ERROR))
        }
        
        lines.add(theme.format("└───────────────────────────┘", Role.MUTED))
        return lines
    }
}
