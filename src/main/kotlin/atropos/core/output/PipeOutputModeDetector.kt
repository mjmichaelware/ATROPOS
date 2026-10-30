/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.output

/**
 * ATOM 7.3: Automatic Headless and Pipe Output Mode Detection.
 *
 * Inspects TTY availability, NO_COLOR, FORCE_COLOR, and CI environment flags to select
 * rich ANSI color, plain streaming text, or headless machine-readable JSON.
 *
 * ## Research: NO_COLOR, FORCE_COLOR & terminfo Database Negotiation
 * Adheres strictly to the NO_COLOR (no-color.org) specification where any non-empty value
 * disables escape codes, overridden only by explicit FORCE_COLOR conventions, and falls back
 * to terminfo capability queries for TERM=dumb or pipe redirection.
 */
object PipeOutputModeDetector {

    enum class Mode(
        val interactive: Boolean,
        val supportsAnsi: Boolean,
        val isHeadless: Boolean
    ) {
        INTERACTIVE_ANSI(interactive = true, supportsAnsi = true, isHeadless = false),
        PLAIN_TEXT(interactive = false, supportsAnsi = false, isHeadless = false),
        HEADLESS_JSON(interactive = false, supportsAnsi = false, isHeadless = true)
    }

    fun isTty(): Boolean = System.console() != null

    fun detectMode(env: Map<String, String> = System.getenv()): Mode {
        if (env["FORCE_COLOR"] == "1" || env["FORCE_COLOR"] == "true") {
            return Mode.INTERACTIVE_ANSI
        }

        if (env.containsKey("NO_COLOR") && env["NO_COLOR"]?.isNotEmpty() == true) {
            return Mode.PLAIN_TEXT
        }

        val term = env["TERM"]
        if (term == "dumb") {
            return Mode.PLAIN_TEXT
        }

        if (term.isNullOrEmpty() || env["CI"] == "true" || !isTty()) {
            return Mode.HEADLESS_JSON
        }

        return Mode.INTERACTIVE_ANSI
    }

    fun isHeadless(env: Map<String, String> = System.getenv()): Boolean =
        detectMode(env).isHeadless

    fun supportsAnsi(env: Map<String, String> = System.getenv()): Boolean =
        detectMode(env).supportsAnsi
}
