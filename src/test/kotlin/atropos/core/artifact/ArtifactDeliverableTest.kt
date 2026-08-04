/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.artifact

import atropos.core.execution.LocalWorkQueue
import atropos.core.memory.LocalMemoryStore
import atropos.core.platform.JvmPlatformAbstraction
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtifactDeliverableTest {
    @Test
    fun artifact_command_writes_real_deliverable_without_validation_jar() {
        val root = Files.createTempDirectory("atropos-artifact-deliverable-")
        val pipeline = ArtifactPipeline(
            store = ArtifactStore(root),
            platform = JvmPlatformAbstraction(root),
            memory = LocalMemoryStore(root.resolve(".atropos/memory").toFile(), env = emptyMap()),
            queue = LocalWorkQueue(root.resolve(".atropos/work-queue").toFile(), env = emptyMap())
        )

        val report = pipeline.createDeliverable("create a release report")
        val artifact = report.artifacts.single()
        val path = root.resolve(artifact.filePath)

        assertTrue(Files.isRegularFile(path))
        assertTrue(Files.readString(path).contains("create a release report"))
        assertTrue(artifact.sha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(report.verifications.all { it.passed })
        assertFalse(Files.exists(root.resolve("build/validation.jar")))
    }

    @Test
    fun artifact_deliverable_redacts_secret_like_prompt_content() {
        val root = Files.createTempDirectory("atropos-artifact-redaction-")
        val pipeline = ArtifactPipeline(
            store = ArtifactStore(root),
            platform = JvmPlatformAbstraction(root)
        )

        val report = pipeline.createDeliverable("document api_key=sk-1234567890abcdef")
        val text = Files.readString(root.resolve(report.artifacts.single().filePath))
        assertFalse("sk-1234567890abcdef" in text)
    }
}
