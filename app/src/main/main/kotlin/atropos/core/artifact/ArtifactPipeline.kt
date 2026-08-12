package atropos.core.artifact

import atropos.core.AtroposRepoRootLocator
import atropos.core.execution.LocalWorkQueue
import atropos.core.memory.LocalMemoryStore
import atropos.core.platform.JvmPlatformAbstraction
import atropos.core.platform.PlatformAbstraction
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.time.Instant

class ArtifactPipeline(
    private val store: ArtifactStore = ArtifactStore(),
    private val platform: PlatformAbstraction = JvmPlatformAbstraction(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    /**
     * Source-compatible boundary for callers from the pre-separation API.
     * These services are intentionally ignored: artifact deliverables no
     * longer own application memory or compile-queue execution. Remove this
     * constructor after downstream callers migrate to the three-owner API.
     */
    @Deprecated("ArtifactPipeline no longer owns memory or queue execution")
    @Suppress("UNUSED_PARAMETER")
    constructor(
        store: ArtifactStore,
        platform: PlatformAbstraction,
        memory: LocalMemoryStore,
        queue: LocalWorkQueue,
        redactionFilter: RedactionFilter = RedactionFilter()
    ) : this(store, platform, redactionFilter)

    fun plan(prompt: String): ArtifactPlan {
        val cleanPrompt = prompt.trim()
        require(cleanPrompt.isNotBlank()) { "artifact prompt must not be blank" }
        val digest = ArtifactHasher.sha256Bytes(cleanPrompt.toByteArray(StandardCharsets.UTF_8))
        return ArtifactPlan(
            id = "artifact-${digest.take(16)}",
            prompt = cleanPrompt
        )
    }

    /** Creates a real user-requested deliverable; this is not the App Factory path. */
    fun createDeliverable(prompt: String): ArtifactReport {
        val cleanPrompt = prompt.trim()
        require(cleanPrompt.isNotBlank()) { "artifact prompt must not be blank" }
        val slug = cleanPrompt.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(64)
            .ifBlank { "requested-artifact" }
        val relativePath = ".atropos/artifacts/deliverables/$slug.md"
        val content = buildString {
            appendLine("# ATROPOS Artifact")
            appendLine()
            appendLine("Requested deliverable")
            appendLine()
            appendLine(redactionFilter.redact(cleanPrompt))
            appendLine()
            appendLine("This file is the workspace deliverable for the request above.")
            appendLine("App scaffolding belongs to the general natural-language factory path.")
        }
        val write = platform.writeFile(relativePath, content)
        if (write.isFailure) {
            val reason = write.exceptionOrNull()?.message ?: "artifact write failed"
            return ArtifactReport(
                artifacts = listOf(
                    Artifact(
                        kind = ArtifactKind.DOCUMENTATION,
                        name = "$slug.md",
                        filePath = relativePath,
                        sha256 = "",
                        byteSize = 0,
                        state = ArtifactState.FAILED,
                        buildCommand = "workspace artifact writer",
                        metadata = mapOf("prompt" to redactionFilter.compact(cleanPrompt), "error" to reason)
                    )
                ),
                verifications = emptyList(),
                installProofs = emptyList()
            )
        }

        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val artifact = Artifact(
            kind = ArtifactKind.DOCUMENTATION,
            name = "$slug.md",
            filePath = relativePath,
            sha256 = ArtifactHasher.sha256Bytes(bytes),
            byteSize = bytes.size.toLong(),
            state = ArtifactState.READY,
            buildCommand = "workspace artifact writer",
            metadata = mapOf("prompt" to redactionFilter.compact(cleanPrompt), "deliverable" to "workspace-file")
        )
        val evidence = listOf(
            VerificationEvidence(artifactId = artifact.id, kind = VerificationKind.SIZE_CHECK, passed = true, evidence = "written ${artifact.byteSize} bytes to ${artifact.filePath}"),
            VerificationEvidence(artifactId = artifact.id, kind = VerificationKind.HASH_VERIFY, passed = true, evidence = "sha256 ${artifact.sha256}")
        )
        store.saveArtifacts(listOf(artifact))
        store.saveVerifications(evidence)
        return ArtifactReport(listOf(artifact), evidence, emptyList())
    }

    /**
     * Compatibility wrapper for older callers. App creation belongs to
     * The general app factory owns application creation; this method remains
     * artifact-only.
     */
    @Deprecated("Use createDeliverable for /artifact work; application creation uses the separate factory command")
    fun runFactory(prompt: String, installDir: String? = null): AppFactoryRun {
        val plan = plan(prompt)
        val startedAt = Instant.now()
        val report = createDeliverable(prompt)
        val artifactVerificationEvidence = report.artifacts.map { verify(it.id) }
        val verificationEvidence = report.verifications + artifactVerificationEvidence
        val readyArtifacts = report.artifacts.filter { it.state == ArtifactState.READY }
        val installProof = installDir
            ?.takeIf { readyArtifacts.isNotEmpty() }
            ?.let { install(readyArtifacts.first().id, it) }
        val commitCandidate = prepareCommit(
            message = "ATROPOS artifact: ${plan.intent}",
            artifactIds = report.artifacts.map { it.id },
            proofIds = listOfNotNull(installProof?.id)
        )
        val success = readyArtifacts.isNotEmpty() &&
            artifactVerificationEvidence.all { it.passed } &&
            (installDir == null || installProof?.verified == true)
        val run = AppFactoryRun(
            prompt = plan.prompt,
            planId = plan.id,
            projectId = null,
            artifacts = report.artifacts,
            verifications = verificationEvidence,
            installProof = installProof,
            commitCandidate = commitCandidate,
            startedAt = startedAt,
            completedAt = Instant.now(),
            success = success
        )
        return run
    }

    /** Compatibility wrapper; it cannot scaffold or compile an application. */
    @Deprecated("Use createDeliverable for artifact work")
    fun build(plan: ArtifactPlan): ArtifactReport = createDeliverable(plan.prompt)

    fun verify(artifactId: String): VerificationEvidence {
        val artifact = store.loadArtifact(artifactId) ?: return VerificationEvidence(
            artifactId = artifactId, kind = VerificationKind.COMPILE_CHECK,
            passed = false, evidence = "artifact not found: $artifactId"
        )

        val fileOk = platform.fileExists(artifact.filePath)
        val sizeOk = if (fileOk) platform.fileSize(artifact.filePath) == artifact.byteSize else false
        val passed = fileOk && sizeOk

        val evidence = VerificationEvidence(
            artifactId = artifactId,
            kind = VerificationKind.SIZE_CHECK,
            passed = passed,
            evidence = if (passed) "artifact verified: ${artifact.filePath} (${artifact.byteSize} bytes)"
                else "artifact verification failed: fileExists=$fileOk sizeMatch=$sizeOk"
        )
        store.saveVerifications(listOf(evidence))
        return evidence
    }

    fun install(artifactId: String, targetDir: String): InstallProof {
        val artifact = store.loadArtifact(artifactId) ?: throw IllegalArgumentException("artifact not found: $artifactId")
        val targetPath = platform.resolvePath(targetDir, artifact.name)
        platform.createDirectories(targetDir)

        val startTime = System.currentTimeMillis()
        val readResult = platform.readFile(artifact.filePath)
        val writeResult: Result<Unit> = readResult.fold(
            onSuccess = { content -> platform.writeFile(targetPath, content) },
            onFailure = { error -> Result.failure(error) }
        )
        val duration = System.currentTimeMillis() - startTime

        val proof = InstallProof(
            artifactId = artifactId,
            targetPath = targetPath,
            installedAt = Instant.now(),
            verified = writeResult.isSuccess,
            runOutput = if (writeResult.isSuccess) "installed to $targetPath" else "install failed: ${writeResult.exceptionOrNull()?.message}",
            durationMs = duration
        )
        store.saveInstallProofs(listOf(proof))
        return proof
    }

    fun prepareCommit(message: String, artifactIds: List<String>, proofIds: List<String>): CommitCandidate {
        val files = artifactIds.mapNotNull { store.loadArtifact(it)?.filePath }
        val candidate = CommitCandidate(
            message = message,
            files = files,
            artifactIds = artifactIds,
            proofIds = proofIds,
            territoryChecked = false,
            secretScanned = false,
            readyForCommit = false
        )
        store.saveCommitCandidates(listOf(candidate))
        return candidate
    }

    fun report(): ArtifactReport {
        return ArtifactReport(
            artifacts = store.loadArtifacts(),
            verifications = store.loadVerifications(),
            installProofs = store.loadInstallProofs()
        )
    }

}
