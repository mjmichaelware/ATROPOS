package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfHostCradleRuntimeStateTest {
    @Test
    fun records_latest_self_host_goal_and_phase() {
        assertEquals("shg-7abcea5c-417", SelfHostCradleRuntimeState.LAST_SELF_HOST_GOAL)
        assertEquals("11", SelfHostCradleRuntimeState.LAST_SELF_HOST_PHASE)
    }

    @Test
    fun source_template_is_deterministic_and_escaped() {
        val source = SelfHostCradleRuntimeState.sourceFor("goal\"-1", "phase\n11")
        assertTrue(source.contains("LAST_SELF_HOST_GOAL: String = \"goal\\\"-1\""))
        assertTrue(source.contains("LAST_SELF_HOST_PHASE: String = \"phase\\n11\""))
    }
}
