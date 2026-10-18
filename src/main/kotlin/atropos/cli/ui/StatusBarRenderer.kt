/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.chrome.CheckpointAge

class StatusBarRenderer(
    private val theme: TerminalTheme
) {
    private val headerRenderer = HeaderRenderer(theme)
    private val recoveryRibbon = RecoveryTectonicRibbon()
    private val checkpointChip = CheckpointChipRenderer(theme)
    private fun safeActiveProvider(): String =
        runCatching { atropos.core.provider.ActiveProvider.get() }.getOrDefault("atropos")

    fun header(width: Int): String = headerRenderer.render(
        SessionPresentationState(
            provider = safeActiveProvider(),
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
        provider: String = safeActiveProvider(),
        mode: String,
        workspace: String,
        tracker: QuotaSessionTracker,
        verificationState: String?,
        width: Int,
        checkpointAge: CheckpointAge = CheckpointAge.Unknown
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
            activeOperation = null
        ),
        width,
        checkpointAge
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
    fun footer(state: SessionPresentationState, width: Int, checkpointAge: CheckpointAge = CheckpointAge.Unknown): String {
        val safeWidth = width.coerceAtLeast(1)

        val directory = TerminalText.compactPath(state.workspace)
        val tab = "${TerminalText.sanitize(state.activeTab)}:${TerminalText.sanitize(state.activeScreen)}"

        // Right-hand pills, least important last so they shed first.
        val pills = buildList {
            val engine = (state.provider.takeIf { it.isNotBlank() && it != "--" && it != "unknown" }
                ?: safeActiveProvider()).lowercase()
            val mode = state.mode.lowercase()
            add(runCatching { theme.metadata("◆ ") + theme.strong("$mode - $engine") }.getOrDefault("◆ $mode - $engine"))
            add(runCatching { theme.metadata("▤ ") + theme.strong(tab) }.getOrDefault("▤ $tab"))
            if (state.repository.available) {
                state.repository.branch?.let { branch ->
                    val dirty = state.repository.clean == false
                    val marker = if (dirty) "!" else "✓"
                    add(runCatching { theme.metadata("$marker ") + theme.strong(TerminalText.ellipsize(branch, 20)) }.getOrDefault("$marker ${TerminalText.ellipsize(branch, 20)}"))
                }
            }
            state.tokens.text().takeIf { it != "--" }?.let {
                add(runCatching { theme.metadata("⋯ ") + theme.strong("$it tok") }.getOrDefault("⋯ $it tok"))
            }
            // Cost as a shape, not only as a number.
            Sparkline.render(state.costHistory, COST_SPARK_CELLS)
                .takeIf(String::isNotEmpty)
                ?.let { add(runCatching { theme.metadata("$ ") + theme.strong(it) }.getOrDefault("$ $it")) }
            state.activePatchId?.takeIf { it.isNotBlank() }?.let {
                add(runCatching { theme.metadata("⊙ ") + theme.strong(TerminalText.ellipsize(it, 18)) }.getOrDefault("⊙ ${TerminalText.ellipsize(it, 18)}"))
            }
            state.activeOperation
                ?.let(TerminalText::sanitize)
                ?.takeIf(String::isNotBlank)
                ?.let {
                    if (it.contains("recovery", ignoreCase = true)) {
                        add(runCatching { theme.warning("△ ") + theme.strong(recoveryRibbon.render(
                            RecoveryTectonicRibbon.State("active", "unknown", "required"), safeWidth
                        )) }.getOrDefault("△ recovery"))
                    } else {
                        add(runCatching { theme.warning("△ ") + theme.strong(it) }.getOrDefault("△ $it"))
                    }
                }
            // Checkpoint chip - primary Continue action
            add(runCatching { checkpointChip.renderChip(checkpointAge, safeWidth) }.getOrDefault(""))
            add(runCatching { theme.subdued("/help") }.getOrDefault("/help"))
        }

        // Shed pills from the left of the right-hand group until it fits.
        var kept = pills
        var right = kept.joinToString("  ")
        val left = runCatching { theme.subdued(directory) }.getOrDefault(directory)
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

    private companion object {
        /** Enough cells to show a trend, few enough to shed cheaply. */
        const val COST_SPARK_CELLS = 6
    }
}
