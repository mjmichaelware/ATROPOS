/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.input.CommandRegistry
import atropos.cli.session.QuotaSessionTracker

/**
 * HOE-B01: Sticky chrome persists project/status bar during resize.
 * HOE-B02: Session tab bar with density support.
 * HOE-B04: Trust indicators (attest/health) per project.
 */
class ViewportLayout(
    private val theme: TerminalTheme,
    private val welcomePanel: WelcomePanel,
    private val statusBar: StatusBarRenderer,
    private val workspaceInspector: WorkspaceInspector = CachingGitWorkspaceInspector(),
    private val chromeRenderer: StickyChromeRenderer = StickyChromeRenderer(theme),
    private val tabBar: SessionTabBar = SessionTabBar(theme)
) {
    private val palette = CommandPaletteRenderer(theme)

    data class TabState(val id: String, val name: String, val isActive: Boolean, val trustLevel: TrustIndicator)
    enum class TrustIndicator { ATTESTED, UNATTESTED, UNKNOWN }

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
        activeTab: String = "tab 1",
        openTabCount: Int = 1,
        activePatchId: String? = null,
        tabs: List<TabState> = emptyList(),
        isDensity: Boolean = false
    ): ScreenFrame {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(6)
        val frame = ScreenFrame(safeWidth, safeHeight)
        val operation = activity?.let(TerminalText::stripAnsi) ?: verificationState

        // HOE-B02: Sticky chrome bar at top (project/status)
        val chromeHeight = if (isDensity) 1 else 2
        val chromeLine = chromeRenderer.render(activeTab, openTabCount, safeWidth, isDensity)
        chromeLine.forEachIndexed { idx, line -> frame.setLine(idx, line) }

        // HOE-B02: Session tab bar below chrome
        val tabBarHeight = if (tabs.isEmpty()) 0 else 1
        val tabLines = tabBar.render(tabs, safeWidth)
        tabLines.forEachIndexed { idx, line -> frame.setLine(chromeHeight + idx, line) }

        val contentStart = chromeHeight + tabBarHeight

        val state = SessionPresentationState(
            provider = provider,
            mode = composer.mode(),
            workspace = workspace,
            commands = CommandRegistry.quickAccessCommands(),
            tokens = tracker.estimatedTokens.takeIf { it > 0 }
                ?.let { MetricValue.Known(it.toString()) } ?: MetricValue.Unknown,
            cost = tracker.estimatedCostUsd().takeIf { it > 0.0 }
                ?.let { MetricValue.Known("$" + String.format("%.4f", it)) }
                ?: MetricValue.Unknown,
            activeOperation = operation,
            repository = workspaceInspector.inspect(workspace),
            activeScreen = activeScreen,
            activeTab = activeTab,
            openTabCount = openTabCount,
            activePatchId = activePatchId
        )

        val footerRow = safeHeight - 1
        val composerSnapshot = composer.renderMultiline(safeWidth, (safeHeight / 3).coerceIn(1, 4))
        val metaLines = composer.metaRow(provider, safeWidth)
        val paletteLines = palette.render(composer.commandQuery(), safeWidth, 5)
        val composerHeight = composerSnapshot.lines.size + metaLines.size
        val paletteHeight = paletteLines.size
        val composerStart = footerRow - composerHeight
        val paletteStart = composerStart - paletteHeight
        val separatorRow = (paletteStart - 1).coerceAtLeast(contentStart + 2)
        val transcriptStart = contentStart
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

        paletteLines.forEachIndexed { index, line ->
            val row = paletteStart + index
            if (row in contentStart until composerStart) frame.setLine(row, line)
        }

        composerSnapshot.lines.forEachIndexed { index, line ->
            frame.setLine(composerStart + index, line)
        }
        metaLines.forEachIndexed { index, line ->
            frame.setLine(composerStart + composerSnapshot.lines.size + index, line)
        }

        frame.setLine(footerRow, statusBar.footer(state, safeWidth))
        frame.cursorX = composerSnapshot.cursorColumn
        frame.cursorY = composerStart + composerSnapshot.cursorRow
        frame.showCursor = true
        return frame
    }
}
