/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.session.QuotaSessionTracker
import atropos.core.AtroposConfig
import atropos.core.verification.VerificationResult
import atropos.core.output.OutputMode
import atropos.core.output.OutputModeDetector
import atropos.cli.ui.chrome.CheckpointAge
import atropos.cli.ui.design.HoeStatusVocabulary
import atropos.cli.ui.design.AgentInspector
import atropos.core.observability.BackgroundProcessPanel
import atropos.core.observability.BoundedRenderingController
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AnsiTerminalEngine(
    private val capabilities: ConfigurationManager = ConfigurationManager(),
    private val plainOutput: PlainTerminalOutput = PlainTerminalOutput(),
    geometryProvider: TerminalGeometryProvider = SttyTerminalGeometryProvider()
) {
    private val canvas = TerminalCanvas(capabilities, plainOutput.out, plainOutput.lock, geometryProvider)
    private val theme = TerminalTheme(capabilities)
    private val transcript = TranscriptRenderer(theme)
    private val markdown = MarkdownRenderer(capabilities.isColorEnabled)
    private val welcome = WelcomePanel(theme)
    private val dashboardWorkspaceInspector = CachingGitWorkspaceInspector()
    private val transcriptBuffer = TranscriptBuffer()
    private val composer = ComposerViewport(theme)
    private val statusBar = StatusBarRenderer(theme)
    private val layout = ViewportLayout(theme, welcome, statusBar)
    private val verification = VerificationRenderer(theme)
    private val help = CommandHelpRenderer(theme)
    private val errors = ErrorRenderer(theme)
    private val toasts = ToastRenderer(theme)
    private val errorRenderer = ErrorRenderer(theme)
    private val toastRenderer = ToastRenderer(theme)
    private val dialogRenderer = DialogRenderer(theme)
    private val dagReactorRenderer = DagReactorRenderer(theme)
    private val backgroundProcesses = BackgroundProcessPanel()
    private val boundedRendering = BoundedRenderingController
    private val quotaFuelCellRenderer = QuotaFuelCellRenderer(theme)
    private val rendering = TerminalRenderingFacade(
        plainOutput, canvas, theme, transcript, markdown, welcome, statusBar, verification, help, transcriptBuffer
    )
    private val spinner = SpinnerEngine { frame ->
        synchronized(this) {
            state.activity = frame?.let(transcript::activity)
            requestFrameLocked()
        }
    }
    private val resizePoller = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "atropos-viewport").apply { isDaemon = true }
    }
    private val state = TerminalStateSnapshot(workspace = capabilities.homePath())

    @Synchronized
    fun initializeReactive(useAlternateScreen: Boolean = true) {
        state.reactive = capabilities.isInteractiveTerminal &&
            OutputModeDetector.detect() != OutputMode.HEADLESS
        if (!state.reactive) return
        canvas.initialize(useAlternateScreen = useAlternateScreen)
        resizePoller.scheduleAtFixedRate({
            synchronized(this) {
                if (state.reactive && canvas.refreshGeometry()) requestFrameLocked()
            }
        }, 250, 250, TimeUnit.MILLISECONDS)
        requestFrameLocked()
    }

    @Synchronized
    fun renderWelcome(config: AtroposConfig, activeProvider: String) {
        state.provider = activeProvider
        if (state.reactive) requestFrameLocked() else rendering.renderWelcomePlain(state.provider, state.workspace)
    }

    @Synchronized
    fun redrawPrompt(
        buffer: String, cursor: Int, suggestion: String, inputMode: String, provider: String,
        tracker: QuotaSessionTracker, paletteSelection: Int = 0, activeScreen: String = "Dashboard",
        activeTab: String = "tab 1", openTabCount: Int = 1,
        paletteLevel: atropos.cli.input.CommandPaletteLevel = atropos.cli.input.CommandPaletteLevel.COMMANDS,
        paletteGroup: String? = null, paletteCommand: String? = null
    ) {
        state.mode = inputMode
        state.provider = provider
        state.tracker = tracker
        state.activeScreen = activeScreen
        state.activeTab = activeTab
        state.openTabCount = openTabCount
        composer.update(buffer = buffer, suggestion = suggestion, cursor = cursor, mode = inputMode, paletteSelection = paletteSelection, paletteLevel = paletteLevel, paletteGroup = paletteGroup, paletteCommand = paletteCommand)
        requestFrameLocked()
    }

    fun redrawPrompt(buffer: String, cursor: Int, provider: String, tracker: QuotaSessionTracker) =
        redrawPrompt(buffer, cursor, "", state.mode, provider, tracker, 0, state.activeScreen, state.activeTab, state.openTabCount)

    @Synchronized
    fun updateAgentPatchState(patchId: String?) {
        state.activePatchId = patchId?.takeIf { it.isNotBlank() }
        requestFrameLocked()
    }

    @Synchronized
    fun commitPrompt(text: String, inputMode: String) {
        transcriptBuffer.append(transcript.user(inputMode, text))
        composer.update("", "", 0, inputMode)
        requestFrameLocked()
    }

    @Synchronized
    fun cancelPrompt() {
        transcriptBuffer.append(transcript.notice("^C"))
        composer.update("", "", 0, state.mode)
        requestFrameLocked()
    }

    @Synchronized
    fun startSpinner(message: String) {
        if (state.reactive) spinner.start(message) else rendering.renderSpinnerPlain(message)
    }

    fun updateSpinner(message: String) = spinner.update(message)
    fun stopSpinner() { if (state.reactive) spinner.stop() }
    fun printThinking() = startSpinner("Thinking")
    fun clearThinking() = stopSpinner()

    @Synchronized
    fun renderHeader() {
        if (state.reactive) requestFrameLocked() else rendering.renderHeaderPlain()
    }

    @Synchronized
    fun renderDashboard(activeProvider: String, activeTab: String, activeScreen: String, openTabCount: Int) {
        state.provider = activeProvider
        state.activeTab = activeTab
        state.activeScreen = activeScreen
        state.openTabCount = openTabCount
        transcriptBuffer.append(transcript.notice(AgentInspector.inspectAgent(activeTab, activeScreen)))
        if (state.reactive) {
            val presentationState = SessionPresentationState(
                provider = state.provider, mode = state.mode, workspace = state.workspace,
                commands = listOf("/agent status", "/tabs", "/status", "/verify"),
                tokens = state.tracker.estimatedTokens.takeIf { it > 0 }?.let { MetricValue.Known(it.toString()) } ?: MetricValue.Unknown,
                cost = state.tracker.estimatedCostUsd().takeIf { it > 0.0 }?.let { MetricValue.Known("$" + String.format("%.4f", it)) } ?: MetricValue.Unknown,
                activeOperation = null, repository = dashboardWorkspaceInspector.inspect(state.workspace),
                activeScreen = activeScreen, activeTab = activeTab, openTabCount = openTabCount, activePatchId = state.activePatchId
            )
            welcome.render(presentationState, canvas.width, (canvas.height * 2 / 3).coerceAtLeast(14)).forEach(transcriptBuffer::append)
            requestFrameLocked()
        } else {
            rendering.renderDashboardPlain(
                activeTab, activeScreen, state.provider, state.workspace, state.mode,
                listOf("/agent status", "/tabs", "/status", "/verify")
            )
        }
    }

    @Synchronized
    fun renderStatusMatrix(config: AtroposConfig, activeProvider: String) {
        state.provider = activeProvider
        if (state.reactive) {
            transcriptBuffer.append(statusBar.footer(state.provider, state.mode, state.workspace, state.tracker, state.verificationState, canvas.width))
            requestFrameLocked()
        } else {
            rendering.renderStatusPlain(state.provider, state.workspace, state.mode, state.tracker)
        }
    }

    @Synchronized
    fun renderStatus(activeProvider: String, tracker: QuotaSessionTracker?) {
        state.provider = activeProvider
        tracker?.let { state.tracker = it }
        transcriptBuffer.append(statusBar.footer(state.provider, state.mode, state.workspace, state.tracker, state.verificationState, canvas.width))
        transcriptBuffer.append(theme.subdued("checkpoint ${CheckpointAge.Unknown.label()} · ${quotaFuelCellRenderer.render(QuotaFuelCellRenderer.QuotaState(0.0, 0.0), canvas.width.coerceAtMost(24))}"))
        requestFrameLocked()
    }

    @Synchronized
    fun renderPrompt() { requestFrameLocked() }

    @Synchronized
    fun renderAssistant(provider: String, response: String) {
        stopSpinner()
        state.provider = provider
        val rendered = markdown.render(response)
        if (!state.reactive) {
            rendering.renderAssistantPlain(provider, rendered)
        } else {
            rendering.renderAssistantReactive(provider, rendered)
            requestFrameLocked()
        }
    }

    @Synchronized
    fun renderMarkdown(text: String) {
        stopSpinner()
        rendering.renderMarkdownReactive(markdown.render(text))
        requestFrameLocked()
    }

    @Synchronized
    fun renderVerificationResult(result: VerificationResult) {
        stopSpinner()
        state.verificationState = null
        rendering.renderVerificationReactive(result)
        requestFrameLocked()
    }

    val viewportWidth: Int get() = canvas.width

    @Synchronized
    fun setProvider(activeProvider: String) {
        state.provider = activeProvider
        requestFrameLocked()
    }

    @Synchronized
    fun toggleVerboseExecution(): Boolean {
        state.verboseExecution = !state.verboseExecution
        renderNotice("verbose execution: ${if (state.verboseExecution) "on" else "off"} (transcript is expandable)")
        return state.verboseExecution
    }

    fun isVerboseExecution(): Boolean = state.verboseExecution

    @Synchronized
    fun renderExecutionEvent(stage: String, detail: String? = null) {
        backgroundProcesses.registerProcess(stage)
        val summary = "execution: $stage"
        if (state.verboseExecution && !detail.isNullOrBlank()) {
            renderNotice("$summary\n  ${detail.trim()}")
        } else {
            renderNotice(summary)
        }
        val status = when {
            stage.contains("fail", ignoreCase = true) -> HoeStatusVocabulary.FAILED
            stage.contains("complete", ignoreCase = true) -> HoeStatusVocabulary.COMPLETED
            else -> HoeStatusVocabulary.WORKING
        }
        renderBlock(dagReactorRenderer.render(listOf(DagReactorRenderer.ReactorNode(stage, status, detail)), canvas.width))
        renderBlock(toastRenderer.render(Toast(null, summary), canvas.width))
    }

    @Synchronized
    fun renderBlock(lines: List<String>) {
        if (!state.reactive) rendering.renderBlockPlain(lines) else rendering.renderBlockReactive(lines)
        if (state.reactive) requestFrameLocked()
    }

    @Synchronized
    fun renderNotice(message: String) {
        if (!state.reactive) rendering.renderNoticePlain(message) else rendering.renderNoticeReactive(message)
        if (state.reactive) requestFrameLocked()
    }

    @Synchronized
    fun renderHelp(query: String = "") {
        val lines = help.lines(query, canvas.width)
        if (!state.reactive) rendering.renderHelpPlain(lines) else rendering.renderHelpReactive(lines)
        if (state.reactive) requestFrameLocked()
    }

    /**
     * A failure with its recovery path, rather than one line of text.
     *
     * [renderError] takes a string and can only ever show what went wrong.
     * Section E asks for the suggestion and the copyable detail alongside it,
     * and [ErrorRenderer] produces all three — including the redaction pass
     * over the copy block, which matters because that block exists to be
     * pasted into a bug report.
     */
    @Synchronized
    fun renderErrorDetail(error: ErrorRenderer.ErrorInfo, critical: Boolean = false) {
        stopSpinner()
        val lines =
            if (critical) errors.renderCritical(error, canvas.width)
            else errors.render(error, canvas.width)
        renderBlock(lines)
    }

    /**
     * A transient notice, in the pinned reference's toast chrome.
     *
     * Rendered into the transcript rather than overlaid: ATROPOS has no
     * absolute-positioning compositor, and a toast that scrolls away with the
     * rest of the output is honest about that, where one drawn at a fixed
     * offset over a scrolling canvas would smear.
     */
    @Synchronized
    fun renderToast(toast: Toast) {
        val lines = toasts.render(toast, canvas.width)
        if (lines.isNotEmpty()) renderBlock(lines)
    }

    @Synchronized
    fun renderError(message: String) {
        stopSpinner()
        if (message.contains("approval", ignoreCase = true) || message.contains("confirm", ignoreCase = true)) {
            val dialog = dialogRenderer.renderConfirm(
                title = "Operator confirmation required",
                body = message,
                confirmLabel = "approve",
                cancelLabel = "cancel",
                confirmSelected = false,
                terminalWidth = canvas.width
            )
            if (state.reactive) rendering.renderBlockReactive(dialog) else rendering.renderBlockPlain(dialog)
            if (state.reactive) requestFrameLocked()
            return
        }
        val lines = errorRenderer.render(
            ErrorRenderer.ErrorInfo(title = "Command failed", message = message),
            canvas.width
        )
        if (state.reactive) {
            rendering.renderBlockReactive(lines)
            requestFrameLocked()
        } else {
            rendering.renderBlockPlain(lines)
        }
    }

    fun printLine(message: String, isError: Boolean = false) {
        if (isError) renderError(message) else renderNotice(message)
    }

    fun clearScreen() { if (state.reactive) canvas.clearScreen() }

    @Synchronized
    fun cleanup() {
        if (!state.reactive) {
            spinner.close()
            resizePoller.shutdownNow()
            return
        }
        state.reactive = false
        resizePoller.shutdownNow()
        spinner.close()
        canvas.close()
    }

    private fun requestFrameLocked() {
        if (!state.reactive) return
        val frame = layout.build(
            width = canvas.width, height = canvas.height, transcript = transcriptBuffer, composer = composer,
            provider = state.provider, workspace = state.workspace, tracker = state.tracker, activity = state.activity,
            verificationState = state.verificationState, activeScreen = state.activeScreen, activeTab = state.activeTab,
            openTabCount = state.openTabCount, activePatchId = state.activePatchId
        )
        boundedRendering.controlBackgroundUpdate(state.reactive) {
            canvas.render(frame)
        }
    }
}
