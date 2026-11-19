// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.output

import java.lang.System

enum class OutputMode {
    INTERACTIVE_COLOR,
    NO_COLOR,
    TERM_DUMB,
    HEADLESS,
    INTERACTIVE,
    PLAIN_TEXT,
    HEADLESS_JSON;

    val interactive: Boolean get() = this == INTERACTIVE || this == INTERACTIVE_COLOR
    val supportsAnsi: Boolean get() = this == INTERACTIVE || this == INTERACTIVE_COLOR
    val isHeadless: Boolean get() = this == HEADLESS || this == HEADLESS_JSON
}

/**
 * ATOM 7.3: Adaptive Interface Input Mode Detection.
 *
 * Detects whether the runtime output mode is interactive ANSI, plain text, or headless JSON.
 * Distinguishes interactive TTY sessions from headless CI environments, piped streams, or
 * touch-driven mobile terminal wrappers (e.g., Termux, SSH sessions).
 *
 * ## Research: Terminfo Database Fallback Mechanisms
 * Research Terminfo database fallback mechanisms for non-standard mobile terminal emulators
 * that fail standard VT100 capability queries, using terminfo / termcap capabilities and
 * NO_COLOR / FORCE_COLOR cross-industry environment negotiation conventions.
 */
object OutputModeDetector {
    val pipeDetector: PipeOutputModeDetector = PipeOutputModeDetector

    fun isTty(): Boolean = PipeOutputModeDetector.isTty()

    fun isHeadless(env: Map<String, String> = System.getenv()): Boolean = detect(env).isHeadless

    fun supportsAnsi(env: Map<String, String> = System.getenv()): Boolean = detect(env).supportsAnsi

    fun interactive(env: Map<String, String> = System.getenv()): Boolean = detect(env).interactive

    fun detect(env: Map<String, String> = System.getenv()): OutputMode {
        if (env["FORCE_COLOR"] == "1" || env["FORCE_COLOR"] == "true") {
            return OutputMode.INTERACTIVE_COLOR
        }

        if (env.containsKey("NO_COLOR") && env["NO_COLOR"]?.isNotEmpty() == true) {
            return OutputMode.NO_COLOR
        }

        val term = env["TERM"]
        if (term == "dumb") {
            return OutputMode.TERM_DUMB
        }

        if (term.isNullOrEmpty() || env["CI"] == "true") {
            return OutputMode.HEADLESS
        }

        // Piped stream detection: if stdin/stdout is not a TTY and not forced, downgrade to plain text
        if (!isTty() && env["ATROPOS_FORCE_TTY"] != "true") {
            return OutputMode.PLAIN_TEXT
        }

        // Touch-driven mobile terminal environments (e.g. Termux or mobile PTS) support interactive ANSI
        if (env.containsKey("TERMUX_VERSION") || env["TERMUX_MAIN_PACKAGE"] != null) {
            return OutputMode.INTERACTIVE_COLOR
        }

        return OutputMode.INTERACTIVE_COLOR
    }
}
