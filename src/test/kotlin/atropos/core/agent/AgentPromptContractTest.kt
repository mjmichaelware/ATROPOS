package atropos.core.agent

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.provider.ContextAttestationService
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.provider.ContextEnvelopeSerializer
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentPromptContractTest {
    @Test
    fun buildWithEnvelope_injects_the_supplied_dag_envelope_and_verifies_against_it() {
        val repoRoot = Files.createTempDirectory("atropos-prompt-envelope-")
        val territory = "src/main/kotlin/atropos/core/agent"
        val node = DagNode(
            id = "provider-node",
            dagId = "dag-self-host",
            label = "provider advisory",
            territory = listOf(territory),
            action = DagNodeAction.PROVIDER_CALL,
            actionPayload = "inspect self-host code",
            createdAt = Instant.parse("2026-07-27T09:20:00Z"),
            updatedAt = Instant.parse("2026-07-27T09:20:00Z"),
            metaFile = Path.of("unused")
        )
        val envelope = ContextEnvelopeFactory.createForDagNode(
            providerId = "groq",
            modelId = "",
            task = "inspect self-host code",
            repoRoot = repoRoot,
            dagNode = node,
            branch = "main",
            baselineCommit = "abc123"
        )

        val prompt = AgentPromptContract.buildWithEnvelope(
            context = "SOURCE_PACK_ID=pack-123\nFILE $territory/SelfHostCradleRuntimeState.kt\nobject X\nEND FILE",
            envelope = envelope
        )
        val response = "done\n\n" + ContextEnvelopeSerializer.attestationBlock(
            systemIdentity = envelope.systemIdentity,
            repository = envelope.repository,
            taskOrNodeId = envelope.nodeId,
            role = envelope.hierarchyRole,
            contextVersion = envelope.contextVersion,
            contextHash = envelope.canonicalContextHash
        )

        val verified = ContextAttestationService.verify(envelope, response)

        assertTrue(prompt.contains("DAG: dag-self-host"), prompt)
        assertTrue(prompt.contains("Node: provider-node"), prompt)
        assertTrue(prompt.contains("Assigned territory: $territory"), prompt)
        assertTrue(prompt.contains("SOURCE_PACK_ID=pack-123"), prompt)
        assertTrue(verified is ContextAttestationService.VerifiedResult.Accepted)
        assertEquals(envelope.canonicalContextHash, (verified as ContextAttestationService.VerifiedResult.Accepted).attestation.contextHash)
    }
}
