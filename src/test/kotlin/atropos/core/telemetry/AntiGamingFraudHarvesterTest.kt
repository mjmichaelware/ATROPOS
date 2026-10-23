/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.EngineDomainViolationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntiGamingFraudHarvesterTest {

    @Test
    fun `test engine root mutation triggers EngineDomainViolationException and fraud penalty`() {
        val harvester = AntiGamingFraudAndSelfVerificationAuditorHarvester()

        assertFailsWith<EngineDomainViolationException> {
            harvester.auditProposal(
                agentId = "test-agent",
                instanceId = "test-instance",
                targetFilePath = "src/main/kotlin/atropos/core/TargetWorkspaceLocator.kt",
                proposedContent = "println()",
                isEngineRootMutation = true
            )
        }
    }

    @Test
    fun `test vacuous assertion mock is rejected`() {
        val harvester = AntiGamingFraudAndSelfVerificationAuditorHarvester()

        val decision = harvester.auditProposal(
            agentId = "test-agent",
            instanceId = "test-instance",
            targetFilePath = "target/tests/CalculatorTest.kt",
            proposedContent = "fun test() { assertTrue(true) }",
            isEngineRootMutation = false
        )

        assertFalse(decision.passed)
        assertTrue(decision.reason.contains("Vacuous test bypass"))
    }
}
