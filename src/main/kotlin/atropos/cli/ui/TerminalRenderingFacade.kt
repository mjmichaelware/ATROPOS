/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.verification.VerificationResult

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
    private val transcriptBuffer: TranscriptBuffer
) {
    fun renderWelcomePlain(provider: String, workspace: String) {
        plainOutput.emitPlain("ATROPOS", canvas.width)
        plainOutput.emitPlain(
            "${provider.lowercase()} · ${TerminalText.compactPath(workspace)} · /help",
            canvas.width
        )
    }

    fun renderDashboardPlain(activeTab: String, activeScreen: String, provider: String, workspace: String) {
        plainOutput.emitPlain(
            "dashboard: $activeTab:$activeScreen · ${provider.lowercase()} · ${TerminalText.compactPath(workspace)}",
            canvas.width
        )
    }

    fun renderStatusPlain(provider: String, workspace: String) {
        plainOutput.emitPlain("$provider · ${TerminalText.compactPath(workspace)}", canvas.width)
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

    fun renderNoticePlain(message: String) {
        plainOutput.emitPlain(message, canvas.width)
    }

    fun renderNoticeReactive(message: String) {
        val shaped = RailBlockFormatter.format(message, theme, canvas.width)
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
