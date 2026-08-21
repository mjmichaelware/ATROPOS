/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.security.KeyDoctorService

class KeysCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val service: KeyDoctorService = KeyDoctorService.create(),
    private val renderer: StatusSecurityRenderer = StatusSecurityRenderer()
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            null, "status" -> uiEngine.renderBlock(renderer.renderKeysStatus(uiEngine.viewportWidth))
            "setup" -> uiEngine.renderBlock(renderer.renderKeysSetup(uiEngine.viewportWidth))
            "doctor" -> uiEngine.renderBlock(renderer.renderKeysDoctor(service, uiEngine.viewportWidth))
            else -> uiEngine.renderError("usage: /keys [status|setup|doctor]")
        }
        return RouterOutcome.CONTINUE
    }
}
