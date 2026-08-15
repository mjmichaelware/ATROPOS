/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.intent

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class Sd5B0XPoliciesTest {
    
    @Test
    fun `test swarm agent parsing`() {
        val input = "agent: coder | role: specialist | permissions: [read, write]\nagent: reviewer | role: auditor | permissions: [read]"
        val configs = SwarmAgentParser.parse(input)
        assertEquals(2, configs.size)
        assertEquals("coder", configs[0].agentName)
        assertEquals(listOf("read", "write"), configs[0].permissions)
    }

    @Test
    fun `test non override admitting policy`() {
        val requestNoOverride = AdmissionRequest("B02", false, 500)
        assertEquals(AdmissionResult.Admitted, NonOverrideAdmittingPolicy.evaluate(requestNoOverride))
        
        val requestOverride = AdmissionRequest("B02", true, 500)
        assertTrue(NonOverrideAdmittingPolicy.evaluate(requestOverride) is AdmissionResult.Denied)
    }

    @Test
    fun `test sd5 b0x validation pipeline`() {
        val request = AdmissionRequest("B01", false, 500)
        val swarmConfig = "agent: ops | role: admin | permissions: [read, write]"
        assertTrue(Sd5B0XValidator.validateB01ThroughB04(request, swarmConfig))
        
        val badRequest = AdmissionRequest("B02", true, 500)
        assertFalse(Sd5B0XValidator.validateB01ThroughB04(badRequest, swarmConfig))
        
        val noWriteConfig = "agent: reviewer | role: auditor | permissions: [read]"
        assertFalse(Sd5B0XValidator.validateB01ThroughB04(request, noWriteConfig))
    }
}
