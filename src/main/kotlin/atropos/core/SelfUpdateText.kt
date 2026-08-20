/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

/**
 * What the updater says, kept apart from what it does.
 *
 * Separate so the outcomes can be asserted without matching on prose, and so
 * every refusal is forced to carry a remedy: an update that fails and offers
 * nothing leaves an operator with a device they cannot build on and no next
 * step.
 */
object SelfUpdateText {

    fun render(outcome: SelfUpdate.Outcome): String = when (outcome) {
        is SelfUpdate.Outcome.UpToDate ->
            "ATROPOS ${outcome.version} is current (${outcome.sha256.take(16)})."

        is SelfUpdate.Outcome.Installed -> buildString {
            appendLine("Installed. ${outcome.jar}")
            appendLine("  was ${outcome.from}")
            appendLine("  now ${outcome.toSha256.take(16)}")
            appendLine("  previous jar kept at ${outcome.backup.fileName}")
            // The running process is still the old code. Saying so is the
            // difference between an operator who restarts and one who reports
            // that the update did nothing.
            append("Restart ATROPOS to run it. `atropos --version` will confirm.")
        }

        is SelfUpdate.Outcome.Refused ->
            "Not installed: ${outcome.reason}\n  ${outcome.remedy}"
    }
}
