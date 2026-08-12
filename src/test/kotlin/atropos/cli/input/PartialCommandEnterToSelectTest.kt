package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PartialCommandEnterToSelectTest {
    private val selector = PartialCommandEnterToSelect()

    @Test
    fun enter_selects_highest_ranked_canonical_completion() {
        assertEquals("/status", selector.resolve("/statuz"))
        assertEquals("/help", selector.resolve("?"))
    }

    @Test
    fun ordinary_natural_language_is_not_reinterpreted_as_a_command() {
        assertNull(selector.resolve("build a notes CLI with tests"))
    }
}
