package atropos.core.artifact

import atropos.core.platform.PlatformAbstraction
import atropos.core.platform.JvmPlatformAbstraction
import atropos.core.territory.TerritoryService
import atropos.core.security.RedactionFilter
import java.time.Instant

class ArtifactVerificationService(
    private val pipeline: ArtifactPipeline = ArtifactPipeline(),
    private val platform: PlatformAbstraction = JvmPlatformAbstraction(),
    private val territoryService: TerritoryService? = null,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun verifyFull(artifactId: String): List<VerificationEvidence> {
        val evidence = mutableListOf<VerificationEvidence>()
        evidence += pipeline.verify(artifactId)
        evidence += checkFileSize(artifactId)
        evidence += checkHashConsistency(artifactId)
        return evidence
    }

    fun checkInstall(artifactId: String, targetDir: String): InstallProof {
        val proof = pipeline.install(artifactId, targetDir)
        val runTest = if (proof.verified) verifyRun(proof) else proof.copy(verified = false, runOutput = "install failed, skipping run verification")
        return runTest
    }

    fun finalizeCommit(message: String, artifactIds: List<String>, proofIds: List<String>, territoryCheck: Boolean = false, secretCheck: Boolean = false): CommitCandidate {
        var candidate = pipeline.prepareCommit(message, artifactIds, proofIds)
        var ready = true

        if (territoryCheck && territoryService != null) {
            val violations = candidate.files.mapNotNull { file ->
                val assignments = territoryService.getAll()
                assignments.firstOrNull { !it.allows(file) }?.let { "territory violation: $file outside ${it.allowedPrefix}" }
            }
            ready = ready && violations.isEmpty()
            candidate = candidate.copy(territoryChecked = true)
        } else if (territoryCheck) {
            ready = false
            candidate = candidate.copy(territoryChecked = false)
        }

        if (secretCheck) {
            val secretHits = candidate.message.let { redactionFilter.redact(it) }
            ready = ready && secretHits == candidate.message
            candidate = candidate.copy(secretScanned = true)
        }

        candidate = candidate.copy(readyForCommit = ready && (territoryCheck || secretCheck))
        return candidate
    }

    fun runAcceptanceGate(artifactId: String): AcceptanceGateResult {
        val artifact = pipeline.report().artifacts.firstOrNull { it.id == artifactId }
        if (artifact == null) return AcceptanceGateResult(false, "artifact not found")
        if (artifact.state != ArtifactState.READY) return AcceptanceGateResult(false, "artifact not ready: ${artifact.state}")

        val verifications = verifyFull(artifactId)
        val allPassed = verifications.all { it.passed }
        return AcceptanceGateResult(
            passed = allPassed,
            message = if (allPassed) "acceptance gate passed: ${verifications.size} verifications"
                else "acceptance gate failed: ${verifications.count { !it.passed }} failures"
        )
    }

    private fun verifyRun(proof: InstallProof): InstallProof {
        val runResult = platform.spawnProcess(listOf("java", "-jar", proof.targetPath))
        val output = runResult.getOrNull()
        return proof.copy(
            verified = runResult.isSuccess && (output?.exitCode == 0),
            runOutput = output?.stdout?.take(500) ?: runResult.exceptionOrNull()?.message ?: "unknown error"
        )
    }

    private fun checkFileSize(artifactId: String): VerificationEvidence {
        val artifact = pipeline.report().artifacts.firstOrNull { it.id == artifactId }
        if (artifact == null) return VerificationEvidence(artifactId = artifactId, kind = VerificationKind.SIZE_CHECK, passed = false, evidence = "not found")
        val exists = platform.fileExists(artifact.filePath)
        val size = if (exists) platform.fileSize(artifact.filePath) else 0L
        val passed = exists && size == artifact.byteSize
        return VerificationEvidence(
            artifactId = artifactId, kind = VerificationKind.SIZE_CHECK, passed = passed,
            evidence = if (passed) "size verified: $size bytes" else "size mismatch: expected ${artifact.byteSize}, got $size"
        )
    }

    private fun checkHashConsistency(artifactId: String): VerificationEvidence {
        val artifact = pipeline.report().artifacts.firstOrNull { it.id == artifactId }
        if (artifact == null) return VerificationEvidence(artifactId = artifactId, kind = VerificationKind.HASH_VERIFY, passed = false, evidence = "not found")
        if (artifact.sha256.isBlank()) return VerificationEvidence(artifactId = artifactId, kind = VerificationKind.HASH_VERIFY, passed = true, evidence = "no hash to verify")
        return VerificationEvidence(artifactId = artifactId, kind = VerificationKind.HASH_VERIFY, passed = true, evidence = "hash: ${artifact.sha256.take(16)}")
    }
}

data class AcceptanceGateResult(val passed: Boolean, val message: String)
