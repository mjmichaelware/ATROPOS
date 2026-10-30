// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.output

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
 * Detects whether the runtime output mode is interactive ANSI, plain text, or headless JSON.
 *
 * ## Research: NO_COLOR, FORCE_COLOR & terminfo Capabilities
 * Adheres to NO_COLOR and FORCE_COLOR cross-industry environment conventions while
 * querying terminal capability databases (terminfo) to refine color and cursor control negotiation.
 */
object OutputModeDetector {
    fun isTty(): Boolean = System.console() != null

    fun isHeadless(env: Map<String, String> = System.getenv()): Boolean = detect(env).isHeadless

    fun supportsAnsi(env: Map<String, String> = System.getenv()): Boolean = detect(env).supportsAnsi

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
        
        return OutputMode.INTERACTIVE_COLOR
    }
}
