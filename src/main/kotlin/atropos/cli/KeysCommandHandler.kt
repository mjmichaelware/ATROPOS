/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.security.KeyDoctorService

class KeysCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val service: KeyDoctorService = KeyDoctorService.create()
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            null, "status" -> uiEngine.renderNotice(service.renderStatus())
            "setup" -> uiEngine.renderNotice(service.renderSetup())
            "doctor" -> uiEngine.renderNotice(service.renderDoctor())
            else -> uiEngine.renderError("usage: /keys [status|setup|doctor]")
        }
        return RouterOutcome.CONTINUE
    }
}
