/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.input.CommandRegistry
import atropos.cli.session.QuotaSessionTracker
import atropos.core.AtroposConfig
import atropos.core.verification.VerificationResult
import java.io.PrintStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AnsiTerminalEngine(
    private val capabilities:
        ConfigurationManager =
            ConfigurationManager(),
    private val out: PrintStream = System.out,
    private val errors: PrintStream = System.err,
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
    private var mode = "ASK"
    private var workspace =
        capabilities.homePath()
    private var tracker =
        QuotaSessionTracker()
    private var activity: String? = null
    private var verificationState:
        String? = null

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
        paletteSelection: Int = 0
    ) {
        this.mode = inputMode
        this.provider = provider
        this.tracker = tracker

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
        0
    )

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

    @Synchronized
    fun renderNotice(message: String) {
        if (!reactive) {
            emitPlain(message)
            return
        }

        transcriptBuffer.append(
            when {
                message.startsWith(
                    "provider switched",
                    ignoreCase = true
                ) -> transcript.success(message)

                else -> transcript.notice(message)
            }
        )

        requestFrameLocked()
    }

    @Synchronized
    fun renderHelp() {
        transcriptBuffer.append(
            theme.brand("commands")
        )
        CommandRegistry.helpLines()
            .forEach(
                transcriptBuffer::append
            )

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
                verificationState
        )

        canvas.render(frame)
    }

    private fun emitPlain(message: String) {
        synchronized(outputLock) {
            out.println(
                TerminalText.stripAnsi(message)
            )
            out.flush()
        }
    }
}
