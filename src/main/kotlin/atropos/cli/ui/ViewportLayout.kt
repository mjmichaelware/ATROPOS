/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.session.QuotaSessionTracker

class ViewportLayout(
    private val theme: TerminalTheme,
    private val welcomePanel: WelcomePanel,
    private val statusBar: StatusBarRenderer,
    private val workspaceInspector: WorkspaceInspector =
        CachingGitWorkspaceInspector()
) {
    private val palette = CommandPaletteRenderer(theme)

    fun build(
        width: Int,
        height: Int,
        transcript: TranscriptBuffer,
        composer: ComposerViewport,
        provider: String,
        workspace: String,
        tracker: QuotaSessionTracker,
        activity: String?,
        verificationState: String?,
        activeScreen: String = "Dashboard",
        activeTab: String = "tab 1"
    ): ScreenFrame {
        val safeWidth = width.coerceIn(36, 160)
        val safeHeight = height.coerceAtLeast(12)
        val frame = ScreenFrame(safeWidth, safeHeight)
        val operation = activity?.let(TerminalText::stripAnsi) ?: verificationState
        val state = SessionPresentationState(
            provider = provider,
            mode = composer.mode(),
            workspace = workspace,
            commands = listOf("/help", "/status", "/providers", "/route", "/use", "/verify", "/exit"),
            tokens = tracker.estimatedTokens.takeIf { it > 0 }
                ?.let { MetricValue.Known(it.toString()) } ?: MetricValue.Unknown,
            cost = tracker.estimatedCostUsd().takeIf { it > 0.0 }
                ?.let { MetricValue.Known("$" + String.format("%.4f", it)) }
                ?: MetricValue.Unknown,
            activeOperation = operation,
            repository = workspaceInspector.inspect(workspace),
            activeScreen = activeScreen,
            activeTab = activeTab
        )

        frame.setLine(0, statusBar.header(state, safeWidth))

        val footerRow = safeHeight - 1
        val composerSnapshot = composer.renderMultiline(safeWidth, (safeHeight / 3).coerceIn(1, 4))
        val paletteLines = palette.render(composer.commandQuery(), safeWidth, 5)
        val composerHeight = composerSnapshot.lines.size
        val paletteHeight = paletteLines.size
        val composerStart = footerRow - composerHeight
        val paletteStart = composerStart - paletteHeight
        val separatorRow = (paletteStart - 1).coerceAtLeast(2)
        val transcriptStart = 1
        val transcriptHeight = (separatorRow - transcriptStart).coerceAtLeast(1)

        if (transcript.isEmpty) {
            welcomePanel.render(state, safeWidth, transcriptHeight)
                .take(transcriptHeight)
                .forEachIndexed { index, line ->
                    frame.setLine(transcriptStart + index, line)
                }
        } else {
            val reserve = if (activity == null) 0 else 1
            val visible = transcript.visibleLines(
                safeWidth,
                (transcriptHeight - reserve).coerceAtLeast(1)
            ).toMutableList()
            activity?.let(visible::add)
            visible.takeLast(transcriptHeight).forEachIndexed { index, line ->
                frame.setLine(transcriptStart + index, line)
            }
        }

        frame.setLine(separatorRow, theme.subdued("─".repeat(safeWidth)))

        paletteLines.forEachIndexed { index, line ->
            val row = paletteStart + index
            if (row in 1 until composerStart) frame.setLine(row, line)
        }

        composerSnapshot.lines.forEachIndexed { index, line ->
            frame.setLine(composerStart + index, line)
        }

        frame.setLine(footerRow, statusBar.footer(state, safeWidth))
        frame.cursorX = composerSnapshot.cursorColumn
        frame.cursorY = composerStart + composerSnapshot.cursorRow + 1
        frame.showCursor = true
        return frame
    }
}
