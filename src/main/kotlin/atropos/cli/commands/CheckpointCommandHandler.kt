/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.cli.RouterOutcome
import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.recovery.RestartCoordinator
import atropos.core.recovery.RestartCoordinatorResult

/**
 * Checkpoint resume command handler.
 *
 * F-CLI-004: Checkpoint chip + resume panel.
 * One primary Continue action from the checkpoint chip.
 */
class CheckpointCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val restartCoordinator: RestartCoordinator = RestartCoordinator()
) {
    fun execute(tokens: List<String>): RouterOutcome {
        val argument = tokens.getOrNull(1)?.lowercase()

        when {
            argument == null || argument == "status" -> renderStatus()
            argument == "resume" || argument == "continue" -> resume()
            argument == "snapshot" -> snapshot()
            else -> uiEngine.renderError(
                "usage: /checkpoint [resume|snapshot|status]"
            )
        }
        return RouterOutcome.CONTINUE
    }

    private fun resume() {
        uiEngine.renderNotice("Resuming from checkpoint...")
        val result = restartCoordinator.recoverAndSnapshot()
        if (result.ok) {
            uiEngine.renderNotice("Restart recovery completed: ${result.message}")
        } else {
            uiEngine.renderError("Restart recovery failed: ${result.message}")
        }
    }

    private fun snapshot() {
        uiEngine.renderNotice("Creating checkpoint snapshot...")
        val snapshot = RestartCoordinator().snapshot()
        uiEngine.renderNotice("Checkpoint snapshot created: ${snapshot.id}")
    }

    private fun renderStatus() {
        uiEngine.renderNotice("Checkpoint status: available via /checkpoint resume")
    }
}