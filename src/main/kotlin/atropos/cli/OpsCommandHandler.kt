/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.StatusOpsRenderer

class OpsCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val renderer: StatusOpsRenderer = StatusOpsRenderer()
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            null, "status" -> uiEngine.renderNotice(renderer.render())
            "export" -> uiEngine.renderNotice(renderer.export())
            "verify" -> uiEngine.renderNotice(renderer.verify())
            "quota-backup" -> uiEngine.renderNotice(renderer.quotaBackup())
            "quota-restore" -> restore(tokens)
            else -> uiEngine.renderError("usage: /ops [status|export|verify|quota-backup|quota-restore <file>]")
        }
        return RouterOutcome.CONTINUE
    }

    private fun restore(tokens: List<String>) {
        val path = tokens.getOrNull(2)
        if (path == null) {
            uiEngine.renderError("usage: /ops quota-restore <backup-file>")
        } else {
            uiEngine.renderNotice(renderer.quotaRestore(path))
        }
    }
}
