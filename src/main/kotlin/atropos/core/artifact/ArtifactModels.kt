package atropos.core.artifact

import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

enum class ArtifactKind {
    BINARY_JAR, COMPILED_CLASS, NATIVE_EXECUTABLE, ANDROID_APK, COMPOSE_PACKAGE,
    SOURCE_ARCHIVE, DOCUMENTATION, TEST_REPORT, COVERAGE_REPORT,
    INSTALL_PROOF, SCREENSHOT_PROOF, RUN_PROOF
}

data class ArtifactPlan(
    val id: String,
    val prompt: String,
    val intent: String = "deliverable",
    val description: String = "workspace deliverable",
    val steps: List<String> = listOf("write", "hash", "verify")
)

enum class ArtifactState { BUILDING, VALIDATING, READY, FAILED, ARCHIVED }

enum class VerificationKind { COMPILE_CHECK, TEST_PASS, INSTALL_CONFIRM, RUN_CONFIRM, SCREENSHOT_MATCH, SIZE_CHECK, HASH_VERIFY }

data class Artifact(
    val id: String = "art-${UUID.randomUUID().toString().take(12)}",
    val kind: ArtifactKind,
    val name: String,
    val filePath: String,
    val sha256: String,
    val byteSize: Long,
    val state: ArtifactState = ArtifactState.BUILDING,
    val buildCommand: String = "",
    val buildDurationMs: Long = 0,
    val createdAt: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap()
)

data class VerificationEvidence(
    val id: String = "ev-${UUID.randomUUID().toString().take(12)}",
    val artifactId: String,
    val kind: VerificationKind,
    val passed: Boolean,
    val evidence: String,
    val timestamp: Instant = Instant.now()
)

data class InstallProof(
    val id: String = "proof-${UUID.randomUUID().toString().take(12)}",
    val artifactId: String,
    val targetPath: String,
    val installedAt: Instant = Instant.now(),
    val verified: Boolean = false,
    val runOutput: String = "",
    val durationMs: Long = 0,
    val screenshots: List<String> = emptyList()
)

data class ArtifactReport(
    val artifacts: List<Artifact>,
    val verifications: List<VerificationEvidence>,
    val installProofs: List<InstallProof>,
    val timestamp: Instant = Instant.now()
) {
    val buildSuccessCount: Int get() = artifacts.count { it.state == ArtifactState.READY }
    val buildFailCount: Int get() = artifacts.count { it.state == ArtifactState.FAILED }
    val verificationPassCount: Int get() = verifications.count { it.passed }
    val installedCount: Int get() = installProofs.size

    val summary: String get() {
        return "Artifacts: $buildSuccessCount built, $buildFailCount failed, $verificationPassCount verifications passed, $installedCount installed"
    }
}

data class CommitCandidate(
    val id: String = "commit-${UUID.randomUUID().toString().take(12)}",
    val message: String,
    val files: List<String>,
    val artifactIds: List<String>,
    val proofIds: List<String>,
    val preparedAt: Instant = Instant.now(),
    val territoryChecked: Boolean = false,
    val secretScanned: Boolean = false,
    val readyForCommit: Boolean = false
) {
    companion object {
        fun hash(files: List<String>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            files.sorted().forEach { digest.update(it.toByteArray(Charsets.UTF_8)) }
            return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
        }
    }
}

data class AppFactoryRun(
    val id: String = "run-${UUID.randomUUID().toString().take(12)}",
    val prompt: String,
    val planId: String,
    val projectId: String? = null,
    val artifacts: List<Artifact> = emptyList(),
    val verifications: List<VerificationEvidence> = emptyList(),
    val installProof: InstallProof? = null,
    val commitCandidate: CommitCandidate? = null,
    val startedAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val success: Boolean = false
)
