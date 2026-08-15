// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.output

import kotlin.test.*

class OutputModeDetectorTest {
    @Test
    fun `detect NO_COLOR`() {
        assertEquals(
            OutputMode.NO_COLOR,
            OutputModeDetector.detect(mapOf("NO_COLOR" to "1", "TERM" to "xterm"))
        )
    }

    @Test
    fun `detect TERM_DUMB`() {
        assertEquals(
            OutputMode.TERM_DUMB,
            OutputModeDetector.detect(mapOf("TERM" to "dumb"))
        )
    }

    @Test
    fun `detect HEADLESS`() {
        assertEquals(
            OutputMode.HEADLESS,
            OutputModeDetector.detect(emptyMap())
        )
    }

    @Test
    fun `detect INTERACTIVE_COLOR`() {
        assertEquals(
            OutputMode.INTERACTIVE_COLOR,
            OutputModeDetector.detect(mapOf("TERM" to "xterm-256color"))
        )
    }
}
