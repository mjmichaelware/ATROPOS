package atropos.cli.ui

import atropos.cli.ui.design.Breakpoint
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponsiveNativeGrammarTest {
    @Test
    fun uses_the_canonical_breakpoint_vocabulary_at_native_widths() {
        val grammar = ResponsiveNativeGrammar()

        assertEquals(Breakpoint.COMPACT, grammar.layout(40).breakpoint)
        assertEquals(Breakpoint.MEDIUM, grammar.layout(80).breakpoint)
        assertEquals(Breakpoint.WIDE, grammar.layout(120).breakpoint)
        assertEquals(Breakpoint.ULTRA, grammar.layout(160).breakpoint)
    }
}
