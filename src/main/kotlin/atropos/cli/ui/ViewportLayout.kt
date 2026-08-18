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
    private val mentionPalette = MentionPaletteRenderer(theme)

    data class TabState(val id: String, val name: String, val isActive: Boolean, val trustLevel: TrustIndicator)
    enum class TrustIndicator { ATTESTED, UNATTESTED, UNKNOWN }

    private companion object {
        /** Columns of margin down each side of the screen. */
        const val GUTTER_CELLS = 1

        /** Below this, the margin costs more than the breathing room returns. */
        const val MINIMUM_WIDTH_FOR_GUTTER = 30
    }

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
        val outerWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(6)

        // A column of breathing room down each edge.
        //
        // Every line used to start at column 0 and end at the last cell, so
        // text sat flush against both sides of the screen and the interface
        // read as a wall. The gutter is dropped entirely below the width where
        // two columns would cost more than they return -- on a narrow phone
        // terminal the content matters more than the margin.
        val gutter = if (outerWidth >= MINIMUM_WIDTH_FOR_GUTTER) GUTTER_CELLS else 0
        val safeWidth = (outerWidth - gutter * 2).coerceAtLeast(1)
        val margin = " ".repeat(gutter)

        responsiveGrammar.layout(safeWidth)
        val frame = ScreenFrame(outerWidth, safeHeight)
        val operation = activity?.let(TerminalText::stripAnsi) ?: verificationState

        // Every row goes through here so the inset is applied once, in one
        // place, rather than being remembered at each of the nine call sites.
        fun place(row: Int, line: String) {
            frame.setLine(row, if (gutter == 0) line else margin + line)
        }

        // HOE-B01: sticky chrome, but only when there is no tab strip.
        //
        // The chrome printed the active tab's name and a `[n tabs]` count in
        // plain text directly above a tab bar that shows both, and the footer
        // showed them a third time. Two of those three were noise. The chrome
        // stays for surfaces that render no tabs at all, which is where it is
        // the only thing carrying that information.
        val chrome =
            if (tabs.isEmpty()) stickyHeader.render(activeTab, openTabCount, safeWidth, isDensity)
            else StickyHeader.Frame(emptyList())
        val chromeHeight = chrome.height
        chrome.lines.forEachIndexed { idx, line -> place(idx, line) }

        // HOE-B02: Session tab bar below chrome.
        //
        // Height is measured from what the bar actually produced rather than
        // assumed to be one row: the renderer can add a territory line, and a
        // hardcoded 1 put the transcript's first row on top of it.
        val tabLines = tabBar.render(tabs, safeWidth)
        val tabBarHeight = tabLines.size
        tabLines.forEachIndexed { idx, line -> place(chromeHeight + idx, line) }

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
        // `/` and `@` are mutually exclusive -- the command grammar stops at
        // the first space and the mention grammar starts at an `@` -- so the
        // two share one surface rather than competing for the same rows.
        val commandLines = palette.render(composer.commandQuery(), safeWidth, paletteRows)
        val paletteLines = commandLines.ifEmpty {
            mentionPalette.render(
                composer.mentionFragment(),
                composer.mentionOptions(),
                composer.mentionSelection(),
                safeWidth,
                paletteRows
            )
        }
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
                    place(solvedTranscriptStart + index, line)
                }
        } else {
            val reserve = if (activity == null) 0 else 1
            val visible = transcript.visibleLines(
                safeWidth,
                (solvedTranscriptHeight - reserve).coerceAtLeast(1)
            ).toMutableList()
            activity?.let(visible::add)
            visible.takeLast(solvedTranscriptHeight).forEachIndexed { index, line ->
                place(solvedTranscriptStart + index, line)
            }
        }

        paletteLines.forEachIndexed { index, line ->
            val row = solvedPaletteStart + index
            if (row in contentStart until solvedComposerStart) place(row, line)
        }

        composerSnapshot.lines.forEachIndexed { index, line ->
            place(solvedComposerStart + index, line)
        }
        metaLines.forEachIndexed { index, line ->
            place(solvedComposerStart + composerSnapshot.lines.size + index, line)
        }

        place(safeHeight - 1, statusBar.footer(state, safeWidth))
        // The caret is placed in screen coordinates, so it carries the inset
        // that every rendered line carries.
        frame.cursorX = composerSnapshot.cursorColumn + gutter
        // +1 because the canvas emits `ESC[row;colH`, which counts from one,
        // while every row index in this layout counts from zero. Without it the
        // caret was drawn one row above the composer — sitting on the box's own
        // top border instead of on the line being typed.
        frame.cursorY = solvedComposerStart + composerSnapshot.cursorRow + 1
        frame.showCursor = true
        return frame
    }
}
