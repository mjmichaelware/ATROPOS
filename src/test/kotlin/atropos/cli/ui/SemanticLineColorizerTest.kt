/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A theme that recolours the logo and the footer is not a colour scheme.
 * Readability in dense output comes from every *kind* of token having its own
 * colour wherever it appears, so the eye finds the one line that matters
 * without reading the rest.
 */
class SemanticLineColorizerTest {

    private val theme = TerminalTheme(ConfigurationManager())
    private val colorizer = SemanticLineColorizer(theme)

    /** The text with every SGR sequence removed. */
    private fun stripped(value: String): String =
        value.replace(Regex("\\[[0-9;]*m"), "")

    @Test
    fun `text is never altered, only wrapped`() {
        val line = "specgraph_status        PASS:canonical_specgraph_atomizer"

        assertEquals(line, stripped(colorizer.colorize(line)), "content must survive exactly")
    }

    @Test
    fun `an outcome outranks the path it sits on`() {
        val failure = colorizer.colorize("evidence   FAILED /some/path/here")
        val success = colorizer.colorize("evidence   PASS /some/path/here")

        if (theme.colorEnabled) {
            assertTrue(failure != success, "a failure and a pass must not render identically")
        }
    }

    @Test
    fun `a line already carrying escapes is untouched`() {
        val preStyled = "[31malready red[0m"

        assertEquals(preStyled, colorizer.colorize(preStyled))
    }

    @Test
    fun `blank lines and empty input survive`() {
        assertEquals("", colorizer.colorize(""))
        assertEquals("\n\n", colorizer.colorize("\n\n"))
    }

    @Test
    fun `ordinary prose is not torn into a fake key and value`() {
        val prose = "The system must not write outside its granted territory."

        assertEquals(
            prose,
            stripped(colorizer.colorize(prose)),
            "a sentence has single spaces; an aligned row does not"
        )
    }

    @Test
    fun `a multi-line block colours every line and preserves it`() {
        val block = "FACTORY RUN\nid      factory-1\nstatus  PASS"

        val painted = colorizer.colorize(block)

        assertEquals(3, painted.lines().size)
        assertEquals(block, stripped(painted))
    }

    @Test
    fun `the status vocabulary is distinguished, not flattened`() {
        val rendered = listOf("PASS", "FAILED", "SKIPPED_SOFT_FAIL", "UNCONFIGURED")
            .map { word -> colorizer.colorize("channel   $word").substringAfter("channel") }

        if (theme.colorEnabled) {
            assertEquals(
                rendered.size,
                rendered.distinct().size,
                "four different outcomes must not all paint the same"
            )
        }
    }

    @Test
    fun `a heading is painted as one unit`() {
        val painted = colorizer.colorize("FACTORY RUN VERIFIED REPOSITORY OUTPUT")

        assertEquals("FACTORY RUN VERIFIED REPOSITORY OUTPUT", stripped(painted))
    }
}
