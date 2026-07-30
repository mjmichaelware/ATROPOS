package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelfHostMutationPayloadParserTest {
    private val parser = SelfHostMutationPayloadParser()

    @Test
    fun accepts_relative_non_empty_payload_and_preserves_delimiters_in_content() {
        val parsed = parser.parse("src/main/kotlin/atropos/core/agent/State.kt::const val VALUE = \"a::b\"")

        assertEquals("src/main/kotlin/atropos/core/agent/State.kt", parsed?.path?.toString())
        assertEquals("const val VALUE = \"a::b\"", parsed?.content)
    }

    @Test
    fun refuses_absolute_empty_and_malformed_payloads() {
        assertNull(parser.parse("/tmp/out.kt::content"))
        assertNull(parser.parse("../out.kt::content"))
        assertNull(parser.parse("src/../out.kt::content"))
        assertNull(parser.parse("bad\u0000path.kt::content"))
        assertNull(parser.parse("relative.kt::   "))
        assertNull(parser.parse("relative.kt"))
    }
}
