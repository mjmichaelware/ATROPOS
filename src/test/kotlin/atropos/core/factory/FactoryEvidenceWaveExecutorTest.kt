package atropos.core.factory

import atropos.core.dag.DagNode
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FactoryEvidenceWaveExecutorTest {
    @Test
    fun only_manifest_covered_atoms_can_terminalize() {
        val root = Files.createTempDirectory("factory-wave-evidence")
        val freeze = FactoryAcceptanceFreeze.create("a".repeat(64), "b".repeat(64), listOf("a"), "CLI@1-1")
        val evidence = root.resolve("app-manifest.txt")
        Files.writeString(evidence, """
            verification=generated-source-and-tests+deterministic
            acceptance_freeze_sha256=${freeze.sha256}
            completion_gate=factory completion gate passed
            planning_atoms=a
        """.trimIndent())
        val node = node("a")
        assertEquals(setOf("a"), FactoryEvidenceWaveExecutor(evidence, freeze).execute(listOf(node)))
    }

    @Test
    fun missing_atom_coverage_is_refused() {
        val root = Files.createTempDirectory("factory-wave-missing")
        val freeze = FactoryAcceptanceFreeze.create("a".repeat(64), "b".repeat(64), listOf("a"), "CLI@1-1")
        val evidence = root.resolve("app-manifest.txt")
        Files.writeString(evidence, """
            verification=generated-source-and-tests+deterministic
            acceptance_freeze_sha256=${freeze.sha256}
            completion_gate=factory completion gate passed
            planning_atoms=other
        """.trimIndent())
        assertFailsWith<IllegalArgumentException> {
            FactoryEvidenceWaveExecutor(evidence, freeze).execute(listOf(node("a")))
        }
    }

    private fun node(id: String): DagNode = DagNode(
        id = id,
        label = id,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        metaFile = Paths.get("placeholder")
    )
}
