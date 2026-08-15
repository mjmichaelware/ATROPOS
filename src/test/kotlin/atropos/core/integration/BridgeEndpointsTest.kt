/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import kotlin.test.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeEndpointsTest {

    @Test
    fun `BridgeEndpoints creates and retrieves projects`() {
        val bridge = BridgeEndpoints()
        val p = bridge.createProject("p1", "Project1", "operator")
        assertEquals(p, bridge.getProject("p1"))
    }

    @Test
    fun `executeCli blocks command injection characters`() {
        val bridge = BridgeEndpoints()
        assertFailsWith<IllegalArgumentException> {
            bridge.executeCli(listOf("rm", "-rf", ";", "ls"))
        }
        assertEquals("EXECUTED: ls -la", bridge.executeCli(listOf("ls", "-la")))
    }

    @Test
    fun `recordApproval maintains history`() {
        val bridge = BridgeEndpoints()
        bridge.recordApproval("APPROVED")
        bridge.recordApproval("REJECTED")
        assertEquals(listOf("APPROVED", "REJECTED"), bridge.getApprovalHistory())
    }

    @Test
    fun `reportQueueFault appends queue faults`() {
        val bridge = BridgeEndpoints()
        bridge.reportQueueFault("corrupt entry")
        assertEquals(listOf("corrupt entry"), bridge.getQueueFaults())
    }

    @Test
    fun `PipelineField contains howDescription`() {
        val field = PipelineField("compile", "compile source", "using gradlew compileKotlin")
        assertEquals("using gradlew compileKotlin", field.howDescription)
    }
}
