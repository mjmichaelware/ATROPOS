/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.session.QuotaSessionTracker

class StatusBarRenderer(
    private val theme: TerminalTheme
) {
    private val headerRenderer = HeaderRenderer(theme)

    fun header(width: Int): String = headerRenderer.render(
        SessionPresentationState(
            provider = "--",
            mode = "ASK",
            workspace = "--",
            commands = emptyList(),
            tokens = MetricValue.Unknown,
            cost = MetricValue.Unknown,
            activeOperation = null
        ),
        width
    )

    fun header(state: SessionPresentationState, width: Int): String =
        headerRenderer.render(state, width)

    fun footer(
        provider: String,
        mode: String,
        workspace: String,
        tracker: QuotaSessionTracker,
        verificationState: String?,
        width: Int
    ): String = footer(
        SessionPresentationState(
            provider = provider,
            mode = mode,
            workspace = workspace,
            commands = emptyList(),
            tokens = tracker.estimatedTokens.takeIf { it > 0 }
                ?.let { MetricValue.Known(it.toString()) } ?: MetricValue.Unknown,
            cost = tracker.estimatedCostUsd().takeIf { it > 0.0 }
                ?.let { MetricValue.Known("$" + String.format("%.4f", it)) }
                ?: MetricValue.Unknown,
            activeOperation = verificationState
        ),
        width
    )

    fun footer(state: SessionPresentationState, width: Int): String {
        val safeWidth = width.coerceAtLeast(1)
        val provider = state.provider.lowercase()
        val mode = state.mode.lowercase()
        val tokens = state.tokens.text()
        val cost = state.cost.text()
        val workspace = TerminalText.compactPath(state.workspace)
        val tab = TerminalText.sanitize(state.activeTab)
        val screen = TerminalText.sanitize(state.activeScreen)
        val tabScreen = "$tab:$screen"
        val operation = state.activeOperation
            ?.let(TerminalText::sanitize)
            ?.takeIf(String::isNotBlank)
            ?.let { "op:$it" }

        val selected = listOf(
            listOfNotNull("ATROPOS", provider, mode, tabScreen, "$tokens tok", cost, workspace, operation),
            listOfNotNull(provider, mode, tabScreen, "$tokens tok", workspace, operation),
            listOfNotNull(provider, mode, tabScreen, "$tokens tok"),
            listOf(provider, mode, tabScreen),
            listOf(provider, mode)
        ).map { it.joinToString(" · ") }
            .firstOrNull { TerminalText.cellWidth(" $it") <= safeWidth }
            ?: "$provider · $mode"

        return theme.footer(
            TerminalText.padEnd(
                TerminalText.ellipsize(" $selected", safeWidth),
                safeWidth
            )
        )
    }
}
