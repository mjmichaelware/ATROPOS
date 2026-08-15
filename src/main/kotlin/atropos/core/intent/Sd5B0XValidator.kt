/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.intent

object Sd5B0XValidator {
    fun validateB01ThroughB04(request: AdmissionRequest, swarmConfig: String): Boolean {
        // SD5#B01 & B02: Payload size constraints and Non-override admitting policies
        val policyResult = NonOverrideAdmittingPolicy.evaluate(request)
        if (policyResult is AdmissionResult.Denied) return false

        // SD5#B03: Swarm/Agents parsing logic must be completely parseable
        val agents = SwarmAgentParser.parse(swarmConfig)
        if (agents.isEmpty()) return false

        // SD5#B04: Invariant validation rules require at least one agent to carry 'write' permissions for admittance
        val hasWrite = agents.any { "write" in it.permissions }
        if (!hasWrite) return false

        return true
    }
}
