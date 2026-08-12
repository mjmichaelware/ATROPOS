package com.atropos.android.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OneHandDensityTest {
    @Test
    fun enforces_touch_target_and_reach_zones() {
        val density = OneHandDensity()
        assertEquals(44, density.touchTargetDp)
        assertEquals(OneHandDensity.ReachZone.TOP, density.reachZone(0, 3))
        assertEquals(OneHandDensity.ReachZone.BOTTOM, density.reachZone(2, 3))
    }

    @Test
    fun offline_resume_is_available_only_for_a_known_session() {
        val state = OneHandDensity().offlineResume("p1", "s1", online = false)
        assertTrue(state.available)
    }
}
