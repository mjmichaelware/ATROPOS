/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.chrome.CheckpointAge
import atropos.cli.ui.design.AgentInspector
import atropos.cli.ui.design.ThemeCatalog
import atropos.core.AtroposConfig
import atropos.core.observability.BackgroundProcessPanel
import atropos.core.observability.BoundedRenderingController
import atropos.core.recovery.RestartCoordinator
import atropos.core.security.RedactionFilter

/**
 * Coordinates dashboard, status matrix, and execution event rendering for [AnsiTerminalEngine].
 */
class AnsiStatusPresenter(
    private val theme: TerminalTheme,
    private val canvas: TerminalCanvas,
    private val plainOutput: PlainTerminalOutput,
    private val transcriptBuffer: TranscriptBuffer,
    private val transcript: TranscriptRenderer,
    private val welcome: WelcomePanel,
    private val statusBar: StatusBarRenderer,
    private val rendering: TerminalRenderingFacade,
    private val redactionFilter: RedactionFilter
) {
    private val dashboardWorkspaceInspector = CachingGitWorkspaceInspector()
    private val restartCoordinator = RestartCoordinator()
    private val quotaFuelCellRenderer = QuotaFuelCellRenderer(theme)
    private val backgroundProcesses = BackgroundProcessPanel()
    private val dagReactorRenderer = DagReactorRenderer(theme)
    private val toastRenderer = ToastRenderer(theme)
    private val palette = ThemeCatalog.byId(theme.themeId)
    private val intentDetector = IntentConflictDetector(IntentConflictBanner(redactionFilter), redactionFilter)
    private val intentBannerRenderer = IntentConflictBannerRenderer(palette)
    private val uncertaintyRenderer = UncertaintyCalibratedRenderer(palette)
    private val bottomStatusBar = BottomStatusBar(theme)

    fun renderDashboard(
        state: TerminalStateSnapshot,
        activeProvider: String,
        activeTab: String,
        activeScreen: String,
        openTabCount: Int,
        onRequestFrame: () -> Unit
    ) {
        state.provider = activeProvider
        state.activeTab = activeTab
        state.activeScreen = activeScreen
        state.openTabCount = openTabCount
        transcriptBuffer.append(transcript.notice(AgentInspector.inspectAgent(activeTab, activeScreen)))
        if (state.reactive) {
            val presentationState = SessionPresentationState(
                provider = state.provider,
                mode = state.mode,
                workspace = state.workspace,
                commands = listOf("/agent status", "/tabs", "/status", "/verify"),
                tokens = state.tracker.estimatedTokens.takeIf { it > 0 }?.let { MetricValue.Known(it.toString()) } ?: MetricValue.Unknown,
                cost = state.tracker.estimatedCostUsd().takeIf { it > 0.0 }?.let { MetricValue.Known("$" + String.format("%.4f", it)) } ?: MetricValue.Unknown,
                activeOperation = null,
                repository = dashboardWorkspaceInspector.inspect(state.workspace),
                activeScreen = activeScreen,
                activeTab = activeTab,
                openTabCount = openTabCount,
                activePatchId = state.activePatchId
            )
            val openFrame = atropos.cli.ui.vis.CliOpenFrame(theme, HeaderRenderer(theme), statusBar)
            openFrame.render(presentationState, canvas.width, canvas.height)
            val bar = bottomStatusBar.render(
                workspacePath = state.workspace,
                branch = presentationState.repository.branch.orEmpty(),
                provider = state.provider,
                progressPercent = 0,
                costUsd = state.tracker.estimatedCostUsd(),
                width = canvas.width
            )
            welcome.render(presentationState, canvas.width, (canvas.height * 2 / 3).coerceAtLeast(14)).forEach(transcriptBuffer::append)
            transcriptBuffer.append(bar)
            onRequestFrame()
        } else {
            rendering.renderDashboardPlain(
                activeTab, activeScreen, state.provider, state.workspace, state.mode,
                listOf("/agent status", "/tabs", "/status", "/verify")
            )
        }
    }

    fun renderStatusMatrix(state: TerminalStateSnapshot, activeProvider: String, onRequestFrame: () -> Unit) {
        state.provider = activeProvider
        if (state.reactive) {
            val checkpointAge = restartCoordinator.latestSnapshot()?.let {
                CheckpointAge.ofEpochMillis(it.capturedAt.toEpochMilli(), java.time.Clock.systemUTC())
            } ?: CheckpointAge.Unknown
            transcriptBuffer.append(statusBar.footer(state.provider, state.mode, state.workspace, state.tracker, state.verificationState, canvas.width, checkpointAge))
            onRequestFrame()
        } else {
            rendering.renderStatusPlain(state.provider, state.workspace, state.mode, state.tracker)
        }
    }

    fun renderStatus(
        state: TerminalStateSnapshot,
        activeProvider: String,
        tracker: QuotaSessionTracker?,
        onRequestFrame: () -> Unit
    ) {
        state.provider = activeProvider
        tracker?.let { state.tracker = it }
        val checkpointAge = restartCoordinator.latestSnapshot()?.let {
            CheckpointAge.ofEpochMillis(it.capturedAt.toEpochMilli(), java.time.Clock.systemUTC())
        } ?: CheckpointAge.Unknown
        transcriptBuffer.append(statusBar.footer(state.provider, state.mode, state.workspace, state.tracker, state.verificationState, canvas.width, checkpointAge))
        transcriptBuffer.append(theme.subdued("checkpoint ${checkpointAge.label()} · ${quotaFuelCellRenderer.render(QuotaFuelCellRenderer.QuotaState(0.0, 0.0), canvas.width.coerceAtMost(24))}"))
        onRequestFrame()
    }

    fun renderExecutionEvent(
        state: TerminalStateSnapshot,
        stage: String,
        detail: String?,
        onNotice: (String) -> Unit,
        onBlock: (List<String>) -> Unit,
        onSignalAttention: () -> Unit
    ) {
        backgroundProcesses.registerProcess(stage)

        atropos.core.thinking.Thinking.stream.emit(
            atropos.core.thinking.ThinkingDepth.L3,
            if (detail.isNullOrBlank()) stage else "$stage — ${detail.trim()}"
        )
        val summary = "execution: $stage"
        if (state.verboseExecution) {
            if (detail.isNullOrBlank()) {
                onNotice(summary)
            } else {
                onNotice("$summary\n  ${detail.trim()}")
            }
            val status = when {
                stage.contains("fail", ignoreCase = true) -> "failed"
                stage.contains("complete", ignoreCase = true) -> "completed"
                else -> "working"
            }
            val conflict = intentDetector.detect(stage)
            if (conflict != null) {
                onBlock(listOf(intentBannerRenderer.render(conflict)))
            }
            onBlock(dagReactorRenderer.render(listOf(DagReactorRenderer.ReactorNode(stage, status, detail)), canvas.width))
            onBlock(toastRenderer.render(Toast(null, summary), canvas.width))
            if (status == "completed" || status == "failed") onSignalAttention()
        }
    }

    private val tipBuffer = atropos.cli.tips.RotatingTipBuffer()

    fun renderWelcome(config: AtroposConfig, activeProvider: String): String {
        val tip = tipBuffer.nextTip()
        val welcomeNotice = "Welcome to ATROPOS (Active Provider: $activeProvider)\nTip: $tip"
        transcriptBuffer.append(welcomeNotice)
        return welcomeNotice
    }
}
