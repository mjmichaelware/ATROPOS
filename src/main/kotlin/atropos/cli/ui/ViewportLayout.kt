/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.input.CommandRegistry
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.chrome.StickyRegionSolver

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
    private val stickyHeader: StickyHeader = StickyHeader(theme),
    private val tabBar: SessionTabBar = SessionTabBar(theme)
) {
    private val responsiveGrammar = ResponsiveNativeGrammar()
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
        responsiveGrammar.layout(safeWidth)
        val frame = ScreenFrame(safeWidth, safeHeight)
        val operation = activity?.let(TerminalText::stripAnsi) ?: verificationState

        // HOE-B02: Sticky chrome bar at top (project/status)
        val chrome = stickyHeader.render(activeTab, openTabCount, safeWidth, isDensity)
        val chromeHeight = chrome.height
        chrome.lines.forEachIndexed { idx, line -> frame.setLine(idx, line) }

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
        // Reserve a bounded, height-aware palette surface. A fixed five-row
        // cap made the command list appear truncated on desktop terminals;
        // the renderer still windows the list for compact screens.
        val paletteRows = (safeHeight / 2).coerceIn(5, 18)
        val paletteLines = palette.render(composer.commandQuery(), safeWidth, paletteRows)
        val composerHeight = composerSnapshot.lines.size + metaLines.size
        val paletteHeight = paletteLines.size
        val composerStart = footerRow - composerHeight
        val paletteStart = composerStart - paletteHeight
        val separatorRow = (paletteStart - 1).coerceAtLeast(contentStart + 2)
        val transcriptStart = contentStart
        val transcriptHeight = (separatorRow - transcriptStart).coerceAtLeast(1)

        // Run the canonical sticky-region solver at the same boundary where
        // the frame reserves its chrome. The existing arithmetic remains the
        // deliberate fallback for a terminal too short to hold the full plan.
        val stickyPlan = StickyRegionSolver.solve(
            totalRows = safeHeight,
            columns = safeWidth,
            headerRows = contentStart,
            inputRows = paletteHeight + composerHeight + 1
        )
        val stickyRegions = stickyPlan.regionsOrNull()
        val solvedTranscriptStart = stickyRegions?.transcript?.start ?: transcriptStart
        val solvedTranscriptHeight = stickyRegions?.transcript?.rows?.coerceAtLeast(1) ?: transcriptHeight
        val solvedPaletteStart = stickyRegions?.input?.start?.let { it + 0 } ?: paletteStart
        val solvedComposerStart = stickyRegions?.input?.endExclusive?.minus(composerHeight + 1) ?: composerStart

        if (transcript.isEmpty) {
            welcomePanel.render(state, safeWidth, solvedTranscriptHeight)
                .take(solvedTranscriptHeight)
                .forEachIndexed { index, line ->
                    frame.setLine(solvedTranscriptStart + index, line)
                }
        } else {
            val reserve = if (activity == null) 0 else 1
            val visible = transcript.visibleLines(
                safeWidth,
                (solvedTranscriptHeight - reserve).coerceAtLeast(1)
            ).toMutableList()
            activity?.let(visible::add)
            visible.takeLast(solvedTranscriptHeight).forEachIndexed { index, line ->
                frame.setLine(solvedTranscriptStart + index, line)
            }
        }

        paletteLines.forEachIndexed { index, line ->
            val row = solvedPaletteStart + index
            if (row in contentStart until solvedComposerStart) frame.setLine(row, line)
        }

        composerSnapshot.lines.forEachIndexed { index, line ->
            frame.setLine(solvedComposerStart + index, line)
        }
        metaLines.forEachIndexed { index, line ->
            frame.setLine(solvedComposerStart + composerSnapshot.lines.size + index, line)
        }

        frame.setLine(safeHeight - 1, statusBar.footer(state, safeWidth))
        frame.cursorX = composerSnapshot.cursorColumn
        frame.cursorY = solvedComposerStart + composerSnapshot.cursorRow
        frame.showCursor = true
        return frame
    }
}
