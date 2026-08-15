// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.intent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessyIntentParserTest {

    @Test
    fun `parses exact matches`() {
        val parser = MessyIntentParser(setOf("run", "status"))
        assertEquals("run", parser.parse("run"))
        assertEquals("status", parser.parse("status"))
    }

    @Test
    fun `corrects small typos`() {
        val parser = MessyIntentParser(setOf("status", "providers", "governance"))
        assertEquals("status", parser.parse("statu"))
        assertEquals("status", parser.parse("stauts")) // actually this fails with edit distance 2 for 'status', which length is 6 so max allowed is 2
        assertEquals("providers", parser.parse("providr"))
        assertEquals("governance", parser.parse("govrnance"))
    }

    @Test
    fun `handles case and whitespace`() {
        val parser = MessyIntentParser(setOf("resume"))
        assertEquals("resume", parser.parse("  ReSume  "))
    }
}
