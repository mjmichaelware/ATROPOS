/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.session.QuotaSessionTracker
import atropos.core.AtroposConfig
import atropos.core.verification.VerificationResult
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AnsiTerminalEngine(
    private val capabilities:
        ConfigurationManager =
            ConfigurationManager(),
    private val out: PrintStream =
        PrintStream(FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8),
    private val errors: PrintStream =
        PrintStream(FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8),
    geometryProvider:
        TerminalGeometryProvider =
            SttyTerminalGeometryProvider()
) {
    private val outputLock = Any()
    private val canvas = TerminalCanvas(
        capabilities,
        out,
        outputLock,
        geometryProvider
    )
    private val theme =
        TerminalTheme(capabilities)
    private val transcript =
        TranscriptRenderer(theme)
    private val markdown =
        MarkdownRenderer(
            capabilities.isColorEnabled
        )
    private val welcome =
        WelcomePanel(theme)
    private val dashboardWorkspaceInspector =
        CachingGitWorkspaceInspector()
    private val transcriptBuffer =
        TranscriptBuffer()
    private val composer =
        ComposerViewport(theme)
    private val statusBar =
        StatusBarRenderer(theme)
    private val layout =
        ViewportLayout(
            theme,
            welcome,
            statusBar
        )
    private val verification =
        VerificationRenderer(theme)
    private val help =
        CommandHelpRenderer(theme)
    private val spinner = SpinnerEngine {
            frame ->

        synchronized(this) {
            activity = frame?.let(
                transcript::activity
            )
            requestFrameLocked()
        }
    }
    private val resizePoller =
        Executors.newSingleThreadScheduledExecutor {
                task ->

            Thread(
                task,
                "atropos-viewport"
            ).apply {
                isDaemon = true
            }
        }

    private var reactive = false
    private var provider = "unknown"
    private var verboseExecution = false
    private var mode = "ASK"
    private var workspace =
        capabilities.homePath()
    private var tracker =
        QuotaSessionTracker()
    private var activity: String? = null
    private var verificationState:
        String? = null
    private var activeScreen = "Dashboard"
    private var activeTab = "tab 1"
    private var openTabCount = 1
    private var activePatchId: String? = null

    @Synchronized
    fun initializeReactive(
        useAlternateScreen: Boolean = true
    ) {
        reactive =
            capabilities.isInteractiveTerminal

        if (!reactive) return

        canvas.initialize(
            useAlternateScreen = useAlternateScreen
        )

        resizePoller.scheduleAtFixedRate({
            synchronized(this) {
                if (
                    reactive &&
                    canvas.refreshGeometry()
                ) {
                    requestFrameLocked()
                }
            }
        }, 250, 250, TimeUnit.MILLISECONDS)

        requestFrameLocked()
    }

    @Synchronized
    fun renderWelcome(
        config: AtroposConfig,
        activeProvider: String
    ) {
        provider = activeProvider

        if (reactive) {
            requestFrameLocked()
        } else {
            emitPlain("ATROPOS")
            emitPlain(
                "${provider.lowercase()} · " +
                    "${TerminalText.compactPath(workspace)} · /help"
            )
        }
    }

    @Synchronized
    fun redrawPrompt(
        buffer: String,
        cursor: Int,
        suggestion: String,
        inputMode: String,
        provider: String,
        tracker: QuotaSessionTracker,
        paletteSelection: Int = 0,
        activeScreen: String = "Dashboard",
        activeTab: String = "tab 1",
        openTabCount: Int = 1
    ) {
        this.mode = inputMode
        this.provider = provider
        this.tracker = tracker
        this.activeScreen = activeScreen
        this.activeTab = activeTab
        this.openTabCount = openTabCount

        composer.update(
            buffer = buffer,
            suggestion = suggestion,
            cursor = cursor,
            mode = inputMode,
            paletteSelection = paletteSelection
        )

        requestFrameLocked()
    }

    fun redrawPrompt(
        buffer: String,
        cursor: Int,
        provider: String,
        tracker: QuotaSessionTracker
    ) = redrawPrompt(
        buffer,
        cursor,
        "",
        mode,
        provider,
        tracker,
        0,
        activeScreen,
        activeTab,
        openTabCount
    )

    @Synchronized
    fun updateAgentPatchState(patchId: String?) {
        activePatchId = patchId?.takeIf { it.isNotBlank() }
        requestFrameLocked()
    }

    @Synchronized
    fun commitPrompt(
        text: String,
        inputMode: String
    ) {
        transcriptBuffer.append(
            transcript.user(
                inputMode,
                text
            )
        )

        composer.update(
            "",
            "",
            0,
            inputMode
        )

        requestFrameLocked()
    }

    @Synchronized
    fun cancelPrompt() {
        transcriptBuffer.append(
            transcript.notice("^C")
        )
        composer.update(
            "",
            "",
            0,
            mode
        )
        requestFrameLocked()
    }

    @Synchronized
    fun startSpinner(message: String) {
        if (reactive) {
            spinner.start(message)
        } else {
            emitPlain("... $message")
        }
    }

    fun updateSpinner(message: String) =
        spinner.update(message)

    fun stopSpinner() {
        if (reactive) spinner.stop()
    }

    fun printThinking() =
        startSpinner("Thinking")

    fun clearThinking() =
        stopSpinner()

    @Synchronized
    fun renderHeader() {
        if (reactive) requestFrameLocked()
        else emitPlain("ATROPOS")
    }

    @Synchronized
    fun renderDashboard(
        activeProvider: String,
        activeTab: String,
        activeScreen: String,
        openTabCount: Int
    ) {
        provider = activeProvider
        this.activeTab = activeTab
        this.activeScreen = activeScreen
        this.openTabCount = openTabCount

        if (reactive) {
            val state = SessionPresentationState(
                provider = provider,
                mode = mode,
                workspace = workspace,
                commands = listOf("/agent status", "/tabs", "/status", "/verify"),
                tokens = tracker.estimatedTokens.takeIf { it > 0 }
                    ?.let { MetricValue.Known(it.toString()) } ?: MetricValue.Unknown,
                cost = tracker.estimatedCostUsd().takeIf { it > 0.0 }
                    ?.let { MetricValue.Known("$" + String.format("%.4f", it)) }
                    ?: MetricValue.Unknown,
                activeOperation = null,
                repository = dashboardWorkspaceInspector.inspect(workspace),
                activeScreen = activeScreen,
                activeTab = activeTab,
                openTabCount = openTabCount,
                activePatchId = activePatchId
            )

            welcome.render(state, canvas.width, (canvas.height * 2 / 3).coerceAtLeast(14))
                .forEach(transcriptBuffer::append)
            requestFrameLocked()
        } else {
            emitPlain("dashboard: $activeTab:$activeScreen · ${provider.lowercase()} · ${TerminalText.compactPath(workspace)}")
        }
    }

    @Synchronized
    fun renderStatusMatrix(
        config: AtroposConfig,
        activeProvider: String
    ) {
        provider = activeProvider

        if (reactive) {
            transcriptBuffer.append(
                statusBar.footer(
                    provider,
                    mode,
                    workspace,
                    tracker,
                    verificationState,
                    canvas.width
                )
            )
            requestFrameLocked()
        } else {
            emitPlain(
                "$provider · " +
                    TerminalText.compactPath(
                        workspace
                    )
            )
        }
    }

    @Synchronized
    fun renderStatus(
        activeProvider: String,
        tracker: QuotaSessionTracker?
    ) {
        provider = activeProvider
        tracker?.let { this.tracker = it }

        transcriptBuffer.append(
            statusBar.footer(
                provider,
                mode,
                workspace,
                this.tracker,
                verificationState,
                canvas.width
            )
        )
        requestFrameLocked()
    }

    @Synchronized
    fun renderPrompt() {
        requestFrameLocked()
    }

    @Synchronized
    fun renderAssistant(
        provider: String,
        response: String
    ) {
        stopSpinner()
        this.provider = provider

        val rendered =
            markdown.render(response)

        if (!reactive) {
            emitPlain("")
            emitPlain("${provider.lowercase()}:")
            emitPlain(rendered)
            return
        }

        transcriptBuffer.append(
            transcript.assistantHeader(provider)
        )

        transcript.assistantBody(rendered)
            .forEach(
                transcriptBuffer::append
            )

        transcriptBuffer.append(
            transcript.assistantFooter()
        )

        requestFrameLocked()
    }

    @Synchronized
    fun renderMarkdown(text: String) {
        stopSpinner()

        transcript.assistantBody(
            markdown.render(text)
        ).forEach(
            transcriptBuffer::append
        )

        requestFrameLocked()
    }

    @Synchronized
    fun renderVerificationResult(
        result: VerificationResult
    ) {
        stopSpinner()
        verificationState = null

        verification.render(
            result,
            canvas.width
        ).forEach(
            transcriptBuffer::append
        )

        requestFrameLocked()
    }

    /** Current viewport width, so callers can lay out to the real terminal. */
    val viewportWidth: Int
        get() = canvas.width

    @Synchronized
    fun setProvider(activeProvider: String) {
        provider = activeProvider
        requestFrameLocked()
    }

    @Synchronized
    fun toggleVerboseExecution(): Boolean {
        verboseExecution = !verboseExecution
        renderNotice("verbose execution: ${if (verboseExecution) "on" else "off"} (transcript is expandable)")
        return verboseExecution
    }

    fun isVerboseExecution(): Boolean = verboseExecution

    @Synchronized
    fun renderExecutionEvent(stage: String, detail: String? = null) {
        val summary = "execution: $stage"
        if (verboseExecution && !detail.isNullOrBlank()) {
            renderNotice("$summary\n  ${detail.trim()}")
        } else {
            renderNotice(summary)
        }
    }

    /**
     * Emits pre-composed lines verbatim.
     *
     * [renderNotice] reshapes legacy `header:` text through
     * [RailBlockFormatter]; a surface that already laid itself out to
     * [viewportWidth] must not be reshaped again, or its columns move.
     */
    @Synchronized
    fun renderBlock(lines: List<String>) {
        if (!reactive) {
            lines.forEach(::emitPlain)
            return
        }
        lines.forEach(transcriptBuffer::append)
        requestFrameLocked()
    }

    @Synchronized
    fun renderNotice(message: String) {
        if (!reactive) {
            emitPlain(message)
            return
        }

        // Legacy `header:` + indented `key: value` output is re-rendered into
        // the reference's rail block layout so every surface reads the same.
        val shaped = RailBlockFormatter.format(message, theme, canvas.width)

        transcriptBuffer.append(
            when {
                message.startsWith(
                    "provider switched",
                    ignoreCase = true
                ) -> transcript.success(shaped)

                else -> transcript.notice(shaped)
            }
        )

        requestFrameLocked()
    }

    @Synchronized
    /**
     * Help in the reference's list shape: a railed block, commands grouped by
     * their leading verb, command in full-contrast ink and description muted.
     *
     * The same rendered lines are emitted in both reactive and plain-terminal
     * modes so help is always visible, but no provider or command execution is
     * triggered here.
     */
    fun renderHelp(query: String = "") {
        val lines = help.lines(query, canvas.width)

        if (!reactive) {
            lines.forEach(::emitPlain)
            return
        }

        lines.forEach(transcriptBuffer::append)
        requestFrameLocked()
    }

    @Synchronized
    fun renderError(message: String) {
        stopSpinner()

        if (reactive) {
            transcriptBuffer.append(
                transcript.error(message)
            )
            requestFrameLocked()
        } else {
            errors.println("error: $message")
            errors.flush()
        }
    }

    fun printLine(
        message: String,
        isError: Boolean = false
    ) {
        if (isError) renderError(message)
        else renderNotice(message)
    }

    fun clearScreen() {
        if (reactive) canvas.clearScreen()
    }

    @Synchronized
    fun cleanup() {
        if (!reactive) {
            spinner.close()
            resizePoller.shutdownNow()
            return
        }

        reactive = false
        resizePoller.shutdownNow()
        spinner.close()
        canvas.close()
    }

    private fun requestFrameLocked() {
        if (!reactive) return

        val frame = layout.build(
            width = canvas.width,
            height = canvas.height,
            transcript = transcriptBuffer,
            composer = composer,
            provider = provider,
            workspace = workspace,
            tracker = tracker,
            activity = activity,
            verificationState =
                verificationState,
            activeScreen = activeScreen,
            activeTab = activeTab,
            openTabCount = openTabCount,
            activePatchId = activePatchId
        )

        canvas.render(frame)
    }

    private fun emitPlain(message: String) {
        synchronized(outputLock) {
            val width = canvas.width.coerceAtLeast(1)
            val plain = TerminalText.stripAnsi(message)
            val lines = plain.split('\n').flatMap { AnsiLineWrapper.wrap(it, width) }
            lines.forEach(out::println)
            out.flush()
        }
    }
}
