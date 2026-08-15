// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.output

enum class OutputMode {
    INTERACTIVE_COLOR,
    NO_COLOR,
    TERM_DUMB,
    HEADLESS
}

object OutputModeDetector {
    fun detect(env: Map<String, String> = System.getenv()): OutputMode {
        if (env.containsKey("NO_COLOR") && env["NO_COLOR"]?.isNotEmpty() == true) {
            return OutputMode.NO_COLOR
        }
        
        val term = env["TERM"]
        if (term == "dumb") {
            return OutputMode.TERM_DUMB
        }
        
        if (term.isNullOrEmpty()) {
            return OutputMode.HEADLESS
        }
        
        return OutputMode.INTERACTIVE_COLOR
    }
}
