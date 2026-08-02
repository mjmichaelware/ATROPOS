package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class SelfHostCradleRuntimeStateTest {
    @Test
    fun records_latest_self_host_goal_and_phase() {
        assertEquals("shg-e681c24a-a92", SelfHostCradleRuntimeState.LAST_SELF_HOST_GOAL)
        assertEquals("11", SelfHostCradleRuntimeState.LAST_SELF_HOST_PHASE)
    }
}
