/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModeRethemeTest {
    @Test
    fun maps_modes_to_stable_semantic_roles() {
        val retheme = ModeRetheme()
        assertEquals(ModeRetheme.ModeStyle("plan", Role.INFO), retheme.style("PLAN"))
        assertEquals(ModeRetheme.ModeStyle("build", Role.STATUS_PENDING), retheme.style("factory"))
        assertEquals(ModeRetheme.ModeStyle("build", Role.STATUS_PENDING), retheme.style("BUILD"))
        assertEquals(ModeRetheme.ModeStyle("agent", Role.STATUS_VERIFIED), retheme.style("AGENT"))
        assertEquals(ModeRetheme.ModeStyle("agent", Role.STATUS_VERIFIED), retheme.style("self-host"))
        assertEquals(ModeRetheme.ModeStyle("ask", Role.ACCENT_FOCUS), retheme.style(""))
        assertEquals(ModeRetheme.ModeStyle("ask", Role.ACCENT_FOCUS), retheme.style("unknown"))
    }

    @Test
    fun case_insensitive_mode_matching() {
        val retheme = ModeRetheme()
        assertEquals(ModeRetheme.ModeStyle("plan", Role.INFO), retheme.style("plan"))
        assertEquals(ModeRetheme.ModeStyle("plan", Role.INFO), retheme.style("Plan"))
        assertEquals(ModeRetheme.ModeStyle("build", Role.STATUS_PENDING), retheme.style("factory"))
        assertEquals(ModeRetheme.ModeStyle("build", Role.STATUS_PENDING), retheme.style("Factory"))
    }
}