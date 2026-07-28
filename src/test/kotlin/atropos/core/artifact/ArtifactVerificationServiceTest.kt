package atropos.core.artifact

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtifactVerificationServiceTest {
    @Test
    fun secretCheckKeepsCleanCommitMessageReady() {
        val root = Files.createTempDirectory("atropos-artifact-secret-clean-")
        val store = ArtifactStore(root)
        store.saveArtifacts(listOf(Artifact(kind = ArtifactKind.BINARY_JAR, name = "clean.jar", filePath = "clean.jar", sha256 = "abc", byteSize = 1, state = ArtifactState.READY)))
        val service = ArtifactVerificationService(pipeline = ArtifactPipeline(store = store))

        val candidate = service.finalizeCommit("clean release", artifactIds = emptyList(), proofIds = emptyList(), secretCheck = true)

        assertTrue(candidate.secretScanned)
        assertTrue(candidate.readyForCommit)
    }

    @Test
    fun secretCheckBlocksCommitMessageWithSecretLikeContent() {
        val root = Files.createTempDirectory("atropos-artifact-secret-block-")
        val service = ArtifactVerificationService(pipeline = ArtifactPipeline(store = ArtifactStore(root)))

        val candidate = service.finalizeCommit("token sk-1234567890abcdef1234567890abcdef", artifactIds = emptyList(), proofIds = emptyList(), secretCheck = true)

        assertTrue(candidate.secretScanned)
        assertFalse(candidate.readyForCommit)
    }
}
