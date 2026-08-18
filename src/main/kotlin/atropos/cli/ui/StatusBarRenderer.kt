/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.session.QuotaSessionTracker

class StatusBarRenderer(
    private val theme: TerminalTheme
) {
    private val headerRenderer = HeaderRenderer(theme)
    private val recoveryRibbon = RecoveryTectonicRibbon()

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

    /**
     * Footer in the pinned reference's shape: working directory on the left in
     * muted ink, compact status pills on the right, each prefixed by its own
     * symbol so the signal is not colour-only.
     *
     * The reference renders e.g. `△ 2 Permissions`, `• 3 LSP`, `⊙ 1 MCP` and a
     * trailing muted `/status`. ATROPOS substitutes its own truthful pills —
     * provider, mode, tab, tokens and patch state — and keeps the trailing
     * affordance. Pills drop right-to-left as width shrinks so the directory is
     * never the first thing lost.
     */
    fun footer(state: SessionPresentationState, width: Int): String {
        val safeWidth = width.coerceAtLeast(1)

        val directory = TerminalText.compactPath(state.workspace)
        val tab = "${TerminalText.sanitize(state.activeTab)}:${TerminalText.sanitize(state.activeScreen)}"

        // Right-hand pills, least important last so they shed first.
        val pills = buildList {
            // The provider is deliberately absent. It is written into the
            // composer's bottom border, where it labels the thing it applies
            // to; a second copy down here said the same word twice on one
            // screen and neither one told you which was authoritative.
            add(theme.metadata("▸ ") + theme.strong(state.mode.lowercase()))
            add(theme.metadata("▤ ") + theme.strong(tab))
            state.tokens.text().takeIf { it != "--" }?.let {
                add(theme.metadata("⋯ ") + theme.strong("$it tok"))
            }
            state.activePatchId?.takeIf { it.isNotBlank() }?.let {
                add(theme.metadata("⊙ ") + theme.strong(TerminalText.ellipsize(it, 18)))
            }
            state.activeOperation
                ?.let(TerminalText::sanitize)
                ?.takeIf(String::isNotBlank)
                ?.let {
                    if (it.contains("recovery", ignoreCase = true)) {
                        add(theme.warning("△ ") + theme.strong(recoveryRibbon.render(
                            RecoveryTectonicRibbon.State("active", "unknown", "required"), safeWidth
                        )))
                    } else {
                        add(theme.warning("△ ") + theme.strong(it))
                    }
                }
            add(theme.subdued("/help"))
        }

        // Shed pills from the left of the right-hand group until it fits.
        var kept = pills
        var right = kept.joinToString("  ")
        val left = theme.subdued(directory)
        fun fits() = TerminalText.cellWidth(left) + 2 + TerminalText.cellWidth(right) <= safeWidth
        while (kept.size > 1 && !fits()) {
            kept = kept.drop(1)
            right = kept.joinToString("  ")
        }

        val gap = (safeWidth - TerminalText.cellWidth(left) - TerminalText.cellWidth(right))
            .coerceAtLeast(1)
        val line = if (fits()) left + " ".repeat(gap) + right else TerminalText.ellipsize(left, safeWidth)

        return TerminalText.padEnd(TerminalText.ellipsize(line, safeWidth), safeWidth)
    }
}
