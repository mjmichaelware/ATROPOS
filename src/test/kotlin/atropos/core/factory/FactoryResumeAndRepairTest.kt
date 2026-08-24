package atropos.core.factory

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagStore
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FactoryResumeAndRepairTest {
    @Test
    fun resume_context_requires_attested_prompt_and_preserves_freeze() {
        val root = Files.createTempDirectory("factory-resume")
        val store = DagStore(root)
        val dag = store.createDag("resume", listOf(node("atom")), "run-1")
        val freeze = FactoryAcceptanceFreeze.create("a".repeat(64), "b".repeat(64), listOf("atom"), "CLI@1-1")
        FactoryRunHandoff.write(root, "run-1", dag.id, FactoryObligationSnapshot(1, listOf("atom"), emptyList(), emptyList(), emptyList()), freeze)
        val runRoot = root.resolve(".atropos/research/factory/run-1")
        Files.createDirectories(runRoot)
        Files.writeString(runRoot.resolve("user-prompt.md"), "prompt_fingerprint=prompt-${"a".repeat(16)}\n")
        Files.writeString(runRoot.resolve("requirements.md"), "attested=true\n")
        Files.writeString(runRoot.resolve("plan.md"), "plan attested\n")
        Files.writeString(runRoot.resolve("acceptance-freeze.md"), freeze.document)
        val context = FactoryRunHandoff.readContext(root, "run-1")
        assertEquals(freeze.sha256, context.handoff.acceptanceFreezeSha256)
        assertEquals(freeze.sha256, context.acceptanceFreeze.sha256)
        assertContains(context.promptFingerprint, "prompt-")
    }

    @Test
    fun repair_evidence_is_checked_before_dag_reentry() {
        val root = Files.createTempDirectory("factory-repair")
        val store = DagStore(root)
        val dag = store.createDag("repair", listOf(node("atom")), "run-1")
        val freeze = FactoryAcceptanceFreeze.create("a".repeat(64), "b".repeat(64), listOf("atom"), "CLI@1-1")
        val handoff = FactoryRunHandoffState("run-1", dag.id, freeze.sha256, 1, listOf("atom"), emptyList(), emptyList(), emptyList(), null, null)
        val result = FactoryRepairExecutor(FactoryObligationLoop(store)).repairAndResume(
            handoff, freeze,
            repair = { FactoryAcceptanceFreeze.RepairEvidence(freeze.sha256, "./verify.sh", 0, "stderr: none", mapOf("verify" to true)) },
            executeWave = { ready -> ready.map { it.id }.toSet() }
        )
        assertContains(result.evidence, "acceptance_freeze_sha256=${freeze.sha256}")
        assertEquals("open_work=0", result.loop.terminationReason)
    }

    private fun node(id: String): DagNode = DagNode(id, label = id, action = DagNodeAction.VERIFY, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, metaFile = Paths.get("placeholder"))
}
