/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.session.QuotaSessionTracker
import atropos.core.AtroposConfig
import atropos.core.verification.VerificationResult
import atropos.core.output.OutputMode
import atropos.core.output.OutputModeDetector
import atropos.core.observability.BoundedRenderingController
import atropos.core.security.RedactionFilter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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
    private val transcriptBuffer = TranscriptBuffer()
    private val composer = ComposerViewport(theme)
    private val statusBar = StatusBarRenderer(theme)
    private val layout = ViewportLayout(theme, welcome, statusBar)
    private val verification = VerificationRenderer(theme)
    private val help = CommandHelpRenderer(theme)
    private val errors = ErrorRenderer(theme)
    private val toasts = ToastRenderer(theme)
    private val dialogRenderer = DialogRenderer(theme)
    private val redactionFilter = RedactionFilter()
    private val boundedRendering = BoundedRenderingController
    private val startupPlayer = AnsiStartupPlayer(theme, canvas)
    private val competitiveErrorLedger = CompetitiveErrorLedger(redactionFilter)

    private val rendering = TerminalRenderingFacade(
        plainOutput, canvas, theme, transcript, markdown, welcome, statusBar, verification, help, transcriptBuffer
    )

    private val statusPresenter = AnsiStatusPresenter(
        theme = theme,
        canvas = canvas,
        plainOutput = plainOutput,
        transcriptBuffer = transcriptBuffer,
        transcript = transcript,
        welcome = welcome,
        statusBar = statusBar,
        rendering = rendering,
        redactionFilter = redactionFilter
    )

    val stallSentinel = StallSentinel()
    private val threadProgress = ThreadProgress(theme)

    @Volatile
    private var runProgress: Triple<Int, Int, Boolean>? = null

    fun setRunProgress(done: Int, total: Int, cut: Boolean = false) {
        runProgress = if (total <= 0) null else Triple(done, total, cut)
    }

    fun clearRunProgress() {
        runProgress = null
    }

    private val spinner = SpinnerEngine(
        annotate = {
            listOf(
                runProgress?.let { (done, total, cut) ->
                    TerminalText.stripAnsi(threadProgress.render(done, total, PROGRESS_CELLS, cut))
                }.orEmpty(),
                stallSentinel.note()
            ).filter(String::isNotBlank).joinToString("  ·  ")
        }
    ) { frame ->
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
        val effectiveAltScreen = useAlternateScreen &&
            System.getenv("ATROPOS_NO_ALT_SCREEN").isNullOrBlank() &&
            System.getenv("ATROPOS_WINDOW_SCROLL").isNullOrBlank()
        canvas.initialize(useAlternateScreen = effectiveAltScreen)
        resizePoller.scheduleAtFixedRate({
            try {
                synchronized(this) {
                    if (state.reactive && !boundedRendering.isPasteSuspended() && canvas.refreshGeometry()) requestFrameLocked()
                }
            } catch (_: Throwable) {
                // Next tick will try again.
            }
        }, 250, 250, TimeUnit.MILLISECONDS)
        atropos.core.provider.ActiveProvider.addListener { newEngine ->
            synchronized(this) {
                state.provider = newEngine
                if (state.reactive && !boundedRendering.isPasteSuspended()) requestFrameLocked()
            }
        }
        requestFrameLocked()
    }

    @Synchronized
    fun renderWelcome(config: AtroposConfig, activeProvider: String) {
        state.provider = activeProvider
        if (state.reactive) {
            startupPlayer.play(config, activeProvider, state.workspace)
            requestFrameLocked()
        } else {
            rendering.renderWelcomePlain(state.provider, state.workspace)
        }
    }

    @Synchronized
    fun redrawPrompt(
        buffer: String, cursor: Int, suggestion: String, inputMode: String, provider: String,
        tracker: QuotaSessionTracker, paletteSelection: Int = 0, activeScreen: String = "Dashboard",
        activeTab: String = "tab 1", openTabCount: Int = 1,
        paletteLevel: atropos.cli.input.CommandPaletteLevel = atropos.cli.input.CommandPaletteLevel.COMMANDS,
        paletteGroup: String? = null, paletteCommand: String? = null,
        mentionOptions: List<String> = emptyList()
    ) {
        state.mode = inputMode
        state.provider = provider
        state.tracker = tracker
        state.activeScreen = activeScreen
        state.activeTab = activeTab
        state.openTabCount = openTabCount
        composer.update(buffer = buffer, suggestion = suggestion, cursor = cursor, mode = inputMode, paletteSelection = paletteSelection, paletteLevel = paletteLevel, paletteGroup = paletteGroup, paletteCommand = paletteCommand, mentionOptions = mentionOptions)
        requestFrameLocked()
    }

    fun redrawPrompt(buffer: String, cursor: Int, provider: String, tracker: QuotaSessionTracker) =
        redrawPrompt(buffer, cursor, "", state.mode, provider, tracker, 0, state.activeScreen, state.activeTab, state.openTabCount)

    @Synchronized
    fun setTabs(tabs: List<ViewportLayout.TabState>) {
        state.tabs = tabs
        state.openTabCount = tabs.size.coerceAtLeast(1)
        requestFrameLocked()
    }

    @Synchronized
    fun scrollTranscript(lines: Int) {
        when {
            lines < 0 -> transcriptBuffer.scrollUp(-lines)
            lines > 0 -> transcriptBuffer.scrollDown(lines)
            else -> transcriptBuffer.followTail()
        }
        requestFrameLocked()
    }

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
        stallSentinel.observedOutput()
        clearRunProgress()
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
        statusPresenter.renderDashboard(state, activeProvider, activeTab, activeScreen, openTabCount) {
            requestFrameLocked()
        }
    }

    @Synchronized
    fun renderStatusMatrix(config: AtroposConfig, activeProvider: String) {
        statusPresenter.renderStatusMatrix(state, activeProvider) {
            requestFrameLocked()
        }
    }

    @Synchronized
    fun renderStatus(activeProvider: String, tracker: QuotaSessionTracker?) {
        statusPresenter.renderStatus(state, activeProvider, tracker) {
            requestFrameLocked()
        }
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
        if (!result.passed) {
            competitiveErrorLedger.recordVerificationError(result.message, "Review gate test failures and evidence")
        }
        rendering.renderVerificationReactive(result)
        requestFrameLocked()
    }

    val viewportWidth: Int get() = canvas.width

    @Synchronized
    fun setProvider(activeProvider: String) {
        state.provider = activeProvider
        atropos.core.provider.ActiveProvider.set(activeProvider)
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
        statusPresenter.renderExecutionEvent(
            state = state,
            stage = stage,
            detail = detail,
            onNotice = ::renderNotice,
            onBlock = ::renderBlock,
            onSignalAttention = ::signalAttention
        )
    }

    @Synchronized
    fun renderBlock(lines: List<String>) {
        val safeLines = lines.map(redactionFilter::redact)
        if (!state.reactive) rendering.renderBlockPlain(safeLines) else rendering.renderBlockReactive(safeLines)
        if (state.reactive) requestFrameLocked()
    }

    @Synchronized
    fun renderNotice(message: String) {
        val safeMessage = redactionFilter.redact(message)
        if (!state.reactive) rendering.renderNoticePlain(safeMessage) else rendering.renderNoticeReactive(safeMessage)
        if (state.reactive) requestFrameLocked()
    }

    @Synchronized
    fun renderHelp(query: String = "") {
        val lines = help.lines(query, canvas.width)
        if (!state.reactive) rendering.renderHelpPlain(lines) else rendering.renderHelpReactive(lines)
        if (state.reactive) requestFrameLocked()
    }

    @Synchronized
    fun renderErrorDetail(error: ErrorRenderer.ErrorInfo, critical: Boolean = false) {
        renderedErrors.incrementAndGet()
        stopSpinner()
        val lines =
            if (critical) errors.renderCritical(error, canvas.width)
            else errors.render(error, canvas.width)
        renderBlock(lines)
    }

    @Synchronized
    fun renderToast(toast: Toast) {
        val lines = toasts.render(toast, canvas.width)
        if (lines.isNotEmpty()) renderBlock(lines)
    }

    private val renderedErrors = AtomicInteger(0)

    val errorCount: Int get() = renderedErrors.get()

    fun resetErrorCount() {
        renderedErrors.set(0)
    }

    @Synchronized
    fun renderError(message: String) {
        renderedErrors.incrementAndGet()
        stopSpinner()
        val safeMessage = redactionFilter.redact(message)
        competitiveErrorLedger.recordExecutionError(safeMessage, "Review command input and provider status")
        if (safeMessage.contains("approval", ignoreCase = true) || safeMessage.contains("confirm", ignoreCase = true)) {
            state.verificationState = "awaiting approval"
            signalAttention()
            val dialog = dialogRenderer.renderConfirm(
                title = "Operator confirmation required",
                body = safeMessage,
                confirmLabel = "approve",
                cancelLabel = "cancel",
                confirmSelected = false,
                terminalWidth = canvas.width
            )
            if (state.reactive) rendering.renderBlockReactive(dialog) else rendering.renderBlockPlain(dialog)
            if (state.reactive) requestFrameLocked()
            return
        }
        val lines = errors.render(
            ErrorRenderer.ErrorInfo(title = "Command failed", message = safeMessage),
            canvas.width
        )
        if (state.reactive) {
            rendering.renderBlockReactive(lines)
            requestFrameLocked()
        } else {
            rendering.renderErrorBlockPlain(lines)
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
        if (!state.reactive || boundedRendering.isPasteSuspended()) return
        val frame = layout.build(
            width = canvas.width, height = canvas.height, transcript = transcriptBuffer, composer = composer,
            provider = state.provider, workspace = state.workspace, tracker = state.tracker, activity = state.activity,
            verificationState = state.verificationState, activeScreen = state.activeScreen, activeTab = state.activeTab,
            openTabCount = state.openTabCount, activePatchId = state.activePatchId, tabs = state.tabs
        )
        boundedRendering.controlBackgroundUpdate(state.reactive) {
            canvas.render(frame)
        }
    }

    private fun signalAttention() {
        if (!state.reactive || !System.getenv("ATROPOS_DISABLE_BELL").isNullOrBlank()) return
        synchronized(plainOutput.lock) {
            plainOutput.out.print('\u0007')
            plainOutput.out.flush()
        }
    }

    private companion object {
        const val PROGRESS_CELLS = 22
    }
}
