/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.session.QuotaSessionTracker
import atropos.core.verification.VerificationResult
import atropos.core.observability.ResponsiveBranding
import atropos.cli.ui.disclosure.DisclosureRowSet
import atropos.cli.ui.disclosure.DisclosureRowStyle
import atropos.cli.ui.disclosure.DisclosureRowFormatter

class TerminalRenderingFacade(
    private val plainOutput: PlainTerminalOutput,
    private val canvas: TerminalCanvas,
    private val theme: TerminalTheme,
    private val transcript: TranscriptRenderer,
    private val markdown: MarkdownRenderer,
    private val welcome: WelcomePanel,
    private val statusBar: StatusBarRenderer,
    private val verification: VerificationRenderer,
    private val help: CommandHelpRenderer,
    private val transcriptBuffer: TranscriptBuffer,
    /**
     * Semantic colouring for renderers that still emit flat text. One
     * transform over their output, rather than an edit to each of them:
     * dozens of renderers would drift apart, a single seam cannot.
     */
    private val colorizer: SemanticLineColorizer = SemanticLineColorizer(theme),
    private val statusLine: StatusLineRenderer = StatusLineRenderer(theme),
    private val sessionOverview: SessionOverviewRenderer = SessionOverviewRenderer(theme)
) {
    fun renderWelcomePlain(provider: String, workspace: String) {
        plainOutput.emitPlain(ResponsiveBranding.renderBrandingLogo(canvas.width), canvas.width)
        plainOutput.emitPlain(
            "${theme.accessibleLabel("provider", provider.lowercase())} · " +
                "${TerminalText.compactPath(workspace)} · /help",
            canvas.width
        )
    }

    /**
     * The dashboard for a non-reactive terminal.
     *
     * [WelcomePanel] draws the reactive one and needs a canvas to lay out
     * against, so the plain path used a single hand-built line that omitted the
     * mode and the commands. [SessionOverviewRenderer] is the block form of the
     * same state: it wraps to the width it is given and needs no compositor,
     * which is exactly the constraint a piped or `dumb` terminal imposes.
     */
    fun renderDashboardPlain(
        activeTab: String,
        activeScreen: String,
        provider: String,
        workspace: String,
        mode: String,
        commands: List<String>
    ) {
        plainOutput.emitPlain("dashboard: $activeTab:$activeScreen", canvas.width)
        sessionOverview.render(
            SessionPresentationState(
                provider = provider,
                mode = mode,
                workspace = workspace,
                commands = commands,
                tokens = MetricValue.Unknown,
                cost = MetricValue.Unknown,
                activeOperation = null,
                activeScreen = activeScreen,
                activeTab = activeTab
            ),
            canvas.width
        ).forEach { plainOutput.emitPlain(it, canvas.width) }
    }

    /**
     * The status line for a non-reactive terminal.
     *
     * Carries the tracker, so tokens and cost survive into piped output. The
     * previous line dropped both, which meant the one surface an operator can
     * actually capture into a log was the one that could not answer "what has
     * this session spent".
     */
    fun renderStatusPlain(provider: String, workspace: String, mode: String, tracker: QuotaSessionTracker?) {
        plainOutput.emitPlain(
            statusLine.render(provider, mode, workspace, tracker, canvas.width),
            canvas.width
        )
    }

    fun renderAssistantPlain(provider: String, rendered: String) {
        plainOutput.emitPlain("", canvas.width)
        plainOutput.emitPlain("${provider.lowercase()}:", canvas.width)
        plainOutput.emitPlain(rendered, canvas.width)
    }

    fun renderAssistantReactive(provider: String, rendered: String) {
        transcriptBuffer.append(transcript.assistantHeader(provider))
        transcript.assistantBody(rendered).forEach(transcriptBuffer::append)
        transcriptBuffer.append(transcript.assistantFooter())
    }

    fun renderBlockPlain(lines: List<String>) {
        lines.forEach { plainOutput.emitPlain(it, canvas.width) }
    }

    fun renderBlockReactive(lines: List<String>) {
        lines.forEach(transcriptBuffer::append)
    }

    /** Renders the canonical expandable disclosure set into the transcript. */
    fun renderDisclosurePlain(rows: DisclosureRowSet, style: DisclosureRowStyle = DisclosureRowStyle.DEFAULT) {
        rows.rows.flatMap { DisclosureRowFormatter.render(it, style) }
            .forEach { plainOutput.emitPlain(it, canvas.width) }
    }

    fun renderNoticePlain(message: String) {
        plainOutput.emitPlain(colorizer.colorize(message), canvas.width)
    }

    fun renderNoticeReactive(message: String) {
        // Coloured before shaping: the formatter aligns on the raw text, and
        // painting first would make it measure escape sequences as width.
        val shaped = RailBlockFormatter.format(colorizer.colorize(message), theme, canvas.width)
        transcriptBuffer.append(
            if (message.startsWith("provider switched", ignoreCase = true)) {
                transcript.success(shaped)
            } else {
                transcript.notice(shaped)
            }
        )
    }

    fun renderHelpPlain(lines: List<String>) {
        lines.forEach { plainOutput.emitPlain(it, canvas.width) }
    }

    fun renderHelpReactive(lines: List<String>) {
        lines.forEach(transcriptBuffer::append)
    }

    fun renderErrorReactive(message: String) {
        transcriptBuffer.append(transcript.error(message))
    }

    fun renderErrorPlain(message: String) {
        plainOutput.emitError(message)
    }

    fun renderVerificationReactive(result: VerificationResult) {
        verification.render(result, canvas.width).forEach(transcriptBuffer::append)
    }

    fun renderMarkdownReactive(rendered: String) {
        transcript.assistantBody(rendered).forEach(transcriptBuffer::append)
    }

    fun renderSpinnerPlain(message: String) {
        plainOutput.emitPlain("... $message", canvas.width)
    }

    fun renderHeaderPlain() {
        plainOutput.emitPlain("ATROPOS", canvas.width)
    }
}
