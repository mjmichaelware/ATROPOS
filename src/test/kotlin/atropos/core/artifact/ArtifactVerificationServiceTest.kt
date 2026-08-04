package atropos.core.artifact

import atropos.core.platform.JvmPlatformAbstraction
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun hashVerificationRecomputesArtifactContent() {
        val root = Files.createTempDirectory("atropos-artifact-hash-ok-")
        Files.writeString(root.resolve("candidate.jar"), "candidate bytes")
        val artifact = Artifact(
            kind = ArtifactKind.BINARY_JAR,
            name = "candidate.jar",
            filePath = "candidate.jar",
            sha256 = sha256("candidate bytes"),
            byteSize = "candidate bytes".length.toLong(),
            state = ArtifactState.READY
        )
        val store = ArtifactStore(root)
        store.saveArtifacts(listOf(artifact))
        val service = ArtifactVerificationService(
            pipeline = ArtifactPipeline(store = store, platform = JvmPlatformAbstraction(root)),
            platform = JvmPlatformAbstraction(root)
        )

        val hashEvidence = service.verifyFull(artifact.id).single { it.kind == VerificationKind.HASH_VERIFY }

        assertTrue(hashEvidence.passed, hashEvidence.evidence)
        assertTrue(hashEvidence.evidence.contains("hash verified"))
    }

    @Test
    fun hashVerificationFailsClosedForMissingOrMismatchedHash() {
        val root = Files.createTempDirectory("atropos-artifact-hash-fail-")
        Files.writeString(root.resolve("candidate.jar"), "candidate bytes")
        val store = ArtifactStore(root)
        val blank = Artifact(
            kind = ArtifactKind.BINARY_JAR,
            name = "blank.jar",
            filePath = "candidate.jar",
            sha256 = "",
            byteSize = "candidate bytes".length.toLong(),
            state = ArtifactState.READY
        )
        val mismatch = blank.copy(id = "artifact-mismatch", sha256 = sha256("different bytes"))
        store.saveArtifacts(listOf(blank, mismatch))
        val service = ArtifactVerificationService(
            pipeline = ArtifactPipeline(store = store, platform = JvmPlatformAbstraction(root)),
            platform = JvmPlatformAbstraction(root)
        )

        val blankHash = service.verifyFull(blank.id).single { it.kind == VerificationKind.HASH_VERIFY }
        val mismatchHash = service.verifyFull(mismatch.id).single { it.kind == VerificationKind.HASH_VERIFY }

        assertFalse(blankHash.passed)
        assertEquals("missing expected sha256", blankHash.evidence)
        assertFalse(mismatchHash.passed)
        assertTrue(mismatchHash.evidence.startsWith("hash mismatch"))
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
