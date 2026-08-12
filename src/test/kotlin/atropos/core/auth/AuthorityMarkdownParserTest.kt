/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The grammar both loaders share. Prose must be ignored and examples must not
 * become instructions — a fenced snippet showing a gate turned off is
 * documentation, and treating it as a setting would make writing about the gate
 * a way to disable it.
 */
class AuthorityMarkdownParserTest {

    @Test
    fun `all three key forms are recognised`() {
        val parsed = AuthorityMarkdownParser.parse(
            """
            secretPolicy: vault
            - maxDepth: 3
            **humanAuthority**: required
            """.trimIndent()
        )

        assertEquals("vault", parsed["secretPolicy"])
        assertEquals("3", parsed["maxDepth"])
        assertEquals("required", parsed["humanAuthority"])
    }

    @Test
    fun `prose containing a colon is not a setting`() {
        val parsed = AuthorityMarkdownParser.parse(
            "The rule is simple: never write outside your territory.\n"
        )

        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `a fenced example never becomes an instruction`() {
        val parsed = AuthorityMarkdownParser.parse(
            """
            Do not do this:

            ```
            boundedAgencyGate: off
            ```

            boundedAgencyGate: on
            """.trimIndent()
        )

        assertEquals("on", parsed["boundedAgencyGate"])
    }

    @Test
    fun `the first statement of a key wins`() {
        val parsed = AuthorityMarkdownParser.parse("mode: strict\nmode: lenient\n")

        assertEquals("strict", parsed["mode"])
    }

    @Test
    fun `headings are not keys`() {
        val parsed = AuthorityMarkdownParser.parse("## Nodes: the list\n")

        assertFalse(parsed.containsKey("Nodes"))
    }

    @Test
    fun `a section yields its lines without the heading`() {
        val lines = AuthorityMarkdownParser.section(
            "## Nodes\n- a | worker\n- b | auditor\n\n## Other\n- c\n",
            "Nodes"
        )

        assertEquals(listOf("a | worker", "b | auditor"), lines)
    }
}
