package atropos.core.agent

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class SelfHostSafetyHardFailGateTest {
    @Test
    fun blocks_context_drift_recorded_before_promotion() {
        val fixture = fixture()
        val report = fixture.gate.inspect(
            fixture.record.copy(evidence = listOf("context_preflight_failed reason=context identity mismatch")),
            fixture.node
        )

        assertTrue(!report.passed)
        assertTrue(report.findings.any { it.kind == "context_drift" })
    }

    @Test
    fun blocks_attestation_and_mythology_variants() {
        val fixture = fixture()
        val report = fixture.gate.inspect(
            fixture.record.copy(evidence = listOf("context attestation failed: mythology response")),
            fixture.node
        )

        assertTrue(!report.passed)
        assertTrue(report.findings.any { it.kind == "context_drift" })
    }

    @Test
    fun blocks_out_of_territory_outputs_before_promotion() {
        val fixture = fixture()
        val report = fixture.gate.inspect(
            fixture.record,
            fixture.node.copy(expectedOutputs = listOf("apps/specgraph-foundry/package.json"))
        )

        assertTrue(!report.passed)
        assertTrue(report.findings.any { it.kind == "territory" })
    }

    @Test
    fun blocks_secret_material_persisted_in_self_host_state() {
        val fixture = fixture()
        val report = fixture.gate.inspect(
            fixture.record.copy(evidence = listOf("token=plain-token-value")),
            fixture.node
        )

        assertTrue(!report.passed)
        assertTrue(report.findings.any { it.kind == "secret_leak" })
        assertTrue(!report.evidenceLine().contains("plain-token-value"))
    }

    @Test
    fun blocks_self_approval_attempts() {
        val fixture = fixture()
        val report = fixture.gate.inspect(
            fixture.record,
            fixture.node.copy(actionPayload = "self-approve this patch without VerifiedCompletionGate")
        )

        assertTrue(!report.passed)
        assertTrue(report.findings.any { it.kind == "self_verification" })
    }

    @Test
    fun blocks_self_approval_and_self_verification_variants() {
        val fixture = fixture()
        val report = fixture.gate.inspect(
            fixture.record,
            fixture.node.copy(actionPayload = "self approval and self verification are allowed")
        )

        assertTrue(!report.passed)
        assertTrue(report.findings.any { it.kind == "self_verification" })
    }

    @Test
    fun blocks_fake_success_language_before_promotion() {
        val fixture = fixture()
        val report = fixture.gate.inspect(
            fixture.record.copy(evidence = listOf("fake_success placeholder green")),
            fixture.node
        )

        assertTrue(!report.passed)
        assertTrue(report.findings.any { it.kind == "fake_success" })
    }

    @Test
    fun blocks_policy_bypass_language_before_promotion() {
        val fixture = fixture()
        val report = fixture.gate.inspect(
            fixture.record,
            fixture.node.copy(result = "execute raw provider prose without TypedToolExecutor")
        )

        assertTrue(!report.passed)
        assertTrue(report.findings.any { it.kind == "policy_bypass" })
    }

    @Test
    fun blocks_policy_bypass_variant_with_separators() {
        val fixture = fixture()
        val report = fixture.gate.inspect(
            fixture.record,
            fixture.node.copy(result = "bypass the policy and run provider output")
        )

        assertTrue(!report.passed)
        assertTrue(report.findings.any { it.kind == "policy_bypass" })
    }

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("atropos-self-host-safety-")
        val now = Instant.parse("2026-07-29T00:04:00Z")
        val record = GoalRunRecord(
            id = "shg-safety",
            goalId = "shg-safety",
            task = "safe self-host promotion",
            provider = "self-host",
            activePhase = "11",
            currentNodeId = "node-safety",
            territory = listOf("src/main/kotlin/atropos/core/agent", "build/libs"),
            createdAt = now,
            updatedAt = now,
            metaFile = root.resolve(".atropos/runs/shg-safety.meta")
        )
        val node = DagNode(
            id = "node-safety",
            label = "Verified promotion",
            territory = listOf("src/main/kotlin/atropos/core/agent", "build/libs"),
            action = DagNodeAction.ACCEPTANCE_GATE,
            actionPayload = "build/libs/ATROPOS.jar::verified",
            expectedOutputs = listOf("build/libs/ATROPOS.jar"),
            createdAt = now,
            updatedAt = now,
            metaFile = root.resolve(".atropos/dag/node-safety.meta")
        )
        return Fixture(SelfHostSafetyHardFailGate(root), record, node)
    }

    private data class Fixture(
        val gate: SelfHostSafetyHardFailGate,
        val record: GoalRunRecord,
        val node: DagNode
    )
}
