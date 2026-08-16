package atropos.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class PortableEngineStateTest {
    @Test
    fun reducer_preserves_identity_and_changes_only_status() {
        val initial = PortableEngineState("project", "run", PortableRunStatus.IDLE, "local", "node-1")
        val next = PortableEngineReducer.reduce(initial, PortableRunEvent.BeginWork)
        assertEquals(PortableRunStatus.WORKING, next.status)
        assertEquals(initial.projectId, next.projectId)
        assertEquals(initial.checkpointId, next.checkpointId)
    }
}
