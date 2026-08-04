/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InboundToolBridgeTest {

    private val bridge = InboundToolBridge(allowedOperations = setOf("read", "inspect"))

    private fun request(
        operation: String = "read",
        paths: List<String> = listOf("src/main"),
        caller: String = "client-1",
        network: Boolean = false
    ) = InboundToolRequest(InboundSource.MCP, caller, operation, paths, network)

    @Test
    fun `an allowed operation with declared paths is admitted to the gate`() {
        val admission = bridge.admit(request())
        assertTrue(admission.admitted)
        assertEquals("mcp:client-1", (admission as InboundAdmission.Admitted).actorId)
    }

    @Test
    fun `admission is not permission — it carries no authority`() {
        val admitted = bridge.admit(request()) as InboundAdmission.Admitted
        // It only describes; the gate still decides.
        assertEquals(listOf("src/main"), admitted.territory)
    }

    @Test
    fun `an unexposed operation is refused`() {
        assertFalse(bridge.admit(request(operation = "shell")).admitted)
    }

    @Test
    fun `a pathless request cannot be territory-bounded and is refused`() {
        val refused = bridge.admit(request(paths = emptyList())) as InboundAdmission.Refused
        assertTrue(refused.reason.contains("declare the paths"))
    }

    @Test
    fun `traversal from an external caller is refused`() {
        assertFalse(bridge.admit(request(paths = listOf("../etc"))).admitted)
    }

    @Test
    fun `an anonymous caller is refused`() {
        assertFalse(bridge.admit(request(caller = "  ")).admitted)
    }

    @Test
    fun `network access is refused unless explicitly granted`() {
        assertFalse(bridge.admit(request(network = true)).admitted)
        val permissive = InboundToolBridge(setOf("read"), networkPermitted = true)
        assertTrue(permissive.admit(request(network = true)).admitted)
    }

    @Test
    fun `computer-use callers get the same bounds as MCP`() {
        val computerUse = InboundToolRequest(
            InboundSource.COMPUTER_USE, "actuator", "shell", listOf("src"), false
        )
        assertFalse(bridge.admit(computerUse).admitted, "no source gets a weaker path")
    }
}
