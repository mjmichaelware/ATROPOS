package atropos.core.planning

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InternalPlanningGraphServiceTest {
    @Test
    fun unusable_specgraph_continues_through_internal_extractor_and_persists_dag() {
        val root = Files.createTempDirectory("internal-dag-fallback")
        val service = InternalPlanningGraphService(
            repoRoot = root,
            canonicalAtoms = object : CanonicalAtomProvider {
                override fun atomsFor(
                    projectId: String,
                    sourcePath: String,
                    content: String,
                    promptFingerprint: String,
                    promptSpans: String
                ): CanonicalAtomSet? = null
            }
        )

        val dag = service.planFromTexts(
            projectId = "factory-fallback",
            label = "fallback",
            sources = mapOf("requirements" to "The application must persist state and verify tests."),
            promptFingerprint = "prompt-0123456789abcdef",
            promptSpans = "application@0-11|class=feature"
        )

        assertFalse(dag.nodes.isEmpty())
        assertTrue(Files.isRegularFile(dag.metaFile))
        assertTrue(dag.nodes.any { it.label.contains("data_lifecycle") })
    }
}
