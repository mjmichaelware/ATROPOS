/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.ColorTier
import atropos.cli.ui.design.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A theme that recolours the logo and the footer is not a colour scheme.
 * Readability in dense output comes from every *kind* of token having its own
 * colour wherever it appears, so the eye finds the one line that matters
 * without reading the rest.
 *
 * Colour is forced on rather than detected. A test JVM has no console, so
 * [ConfigurationManager.isColorEnabled] is false under CI and every assertion
 * about colour would silently pass on uncoloured text — which is how a bug that
 * painted passing runs red survived a green suite.
 */
class SemanticLineColorizerTest {

    private val theme = TerminalTheme(
        ConfigurationManager(
            envProvider = { name -> if (name == "TERM") "xterm-256color" else null },
            hasConsole = true
        ),
        tierOverride = ColorTier.TRUECOLOR
    )
    private val colorizer = SemanticLineColorizer(theme)

    /** The text with every SGR sequence removed. */
    private fun stripped(value: String): String =
        value.replace(Regex("\\u001B\\[[0-9;]*m"), "")

    /** True when [token] appears painted in [role] somewhere in [painted]. */
    private fun paintedAs(painted: String, token: String, role: Role): Boolean =
        painted.contains(theme.paint(role, token))

    @Test
    fun `text is never altered, only wrapped`() {
        val line = "specgraph_status        PASS:canonical_specgraph_atomizer"

        assertEquals(line, stripped(colorizer.colorize(line)), "content must survive exactly")
    }

    @Test
    fun `an outcome outranks the path it sits on`() {
        val failure = colorizer.colorize("evidence   FAILED /some/path/here")
        val success = colorizer.colorize("evidence   PASS /some/path/here")

        assertTrue(failure != success, "a failure and a pass must not render identically")
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

        assertEquals(
            rendered.size,
            rendered.distinct().size,
            "four different outcomes must not all paint the same"
        )
    }

    @Test
    fun `a heading is painted as one unit`() {
        val painted = colorizer.colorize("FACTORY RUN VERIFIED REPOSITORY OUTPUT")

        assertEquals("FACTORY RUN VERIFIED REPOSITORY OUTPUT", stripped(painted))
    }

    /**
     * The regression that motivated per-token painting. This exact line came off
     * a real run: it is a *pass* that happens to report how many candidates were
     * rejected, and painting the value as one unit made the whole thing red.
     */
    @Test
    fun `a rejection count on a passing line does not paint as a failure`() {
        val painted = colorizer.colorize("st_memory=PASS scoped_hits=1 rejected=15")

        assertTrue(paintedAs(painted, "PASS", Role.STATUS_VERIFIED), "the outcome must read as a pass")
        assertTrue(
            !painted.contains(theme.paint(Role.STATUS_ERROR, "15")),
            "a count is not an outcome"
        )
        assertEquals("st_memory=PASS scoped_hits=1 rejected=15", stripped(painted))
    }

    @Test
    fun `each field of a compound line is coloured on its own`() {
        val painted = colorizer.colorize("specgraph=PASS canonical_specgraph_atomizer atom_count=2")

        assertTrue(paintedAs(painted, "PASS", Role.STATUS_VERIFIED))
        assertTrue(paintedAs(painted, "specgraph=", Role.TEXT_SECONDARY), "a label is not the news")
        assertTrue(paintedAs(painted, "2", Role.INFO), "a count reads as a number")
    }

    /**
     * `SKIPPED_SOFT_FAIL` contains `FAIL`. Matching markers as substrings made
     * every degraded channel look like a broken run, which is the one
     * distinction an operator most needs to make at a glance.
     */
    @Test
    fun `a soft fail is not painted as a hard failure`() {
        val painted = colorizer.colorize("dloi=SKIPPED_SOFT_FAIL[DEGRADED]:no_exact_match")

        assertTrue(
            paintedAs(painted, "SKIPPED_SOFT_FAIL[DEGRADED]:no_exact_match", Role.STATUS_PENDING),
            "a degraded channel is amber, not red"
        )
    }

    @Test
    fun `a path keeps its own colour when nothing outranks it`() {
        val painted = colorizer.colorize("generated_project       /data/data/com.termux/files/home/x")

        assertTrue(paintedAs(painted, "/data/data/com.termux/files/home/x", Role.PATH))
    }

    @Test
    fun `an opaque id recedes`() {
        val painted = colorizer.colorize("id                      factory-e93a403662a75003")

        assertTrue(paintedAs(painted, "factory-e93a403662a75003", Role.TEXT_MUTED))
    }
}
