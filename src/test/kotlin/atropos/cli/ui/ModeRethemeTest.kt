package atropos.cli.ui

import atropos.cli.ui.design.Role
import kotlin.test.Test
import kotlin.test.assertEquals

class ModeRethemeTest {
    @Test
    fun maps_modes_to_stable_semantic_roles() {
        val retheme = ModeRetheme()
        assertEquals(ModeRetheme.ModeStyle("plan", Role.INFO), retheme.style("PLAN"))
        assertEquals(ModeRetheme.ModeStyle("build", Role.STATUS_PENDING), retheme.style("factory"))
        assertEquals(ModeRetheme.ModeStyle("ask", Role.ACCENT_FOCUS), retheme.style(""))
    }
}
