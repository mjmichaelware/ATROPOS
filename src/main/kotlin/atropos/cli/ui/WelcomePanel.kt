/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class WelcomePanel(
    private val landing: LandingRenderer
) {
    constructor(theme: TerminalTheme) : this(LandingRenderer(theme))

    fun render(state: SessionPresentationState, terminalWidth: Int): List<String> =
        landing.render(state, terminalWidth)

    fun render(state: SessionPresentationState, terminalWidth: Int, terminalHeight: Int): List<String> =
        landing.render(state, terminalWidth, terminalHeight)

    fun render(provider: String, workspace: String, mode: String, terminalWidth: Int): List<String> =
        render(
            SessionPresentationState(
                provider,
                mode,
                workspace,
                listOf("/help", "/status", "/use", "/verify", "/exit"),
                MetricValue.Unknown,
                MetricValue.Unknown,
                null
            ),
            terminalWidth
        )
}
