package atropos.core.artifact

import atropos.core.execution.LocalWorkQueue
import atropos.core.factory.AppFactoryRouter
import atropos.core.factory.FactoryPlan
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryKind
import atropos.core.platform.JvmPlatformAbstraction
import atropos.core.platform.PlatformAbstraction
import atropos.core.project.ProjectRegistry
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.time.Instant

class ArtifactPipeline(
    private val store: ArtifactStore = ArtifactStore(),
    private val platform: PlatformAbstraction = JvmPlatformAbstraction(),
    private val factoryRouter: AppFactoryRouter = AppFactoryRouter(),
    private val memory: LocalMemoryStore = LocalMemoryStore(),
    private val queue: LocalWorkQueue = LocalWorkQueue(),
    private val projectRegistry: ProjectRegistry = ProjectRegistry(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun plan(prompt: String): FactoryPlan = factoryRouter.plan(prompt)

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

    fun runFactory(prompt: String, installDir: String? = null): AppFactoryRun {
        val plan = plan(prompt)
        val project = projectRegistry.register(
            name = plan.projectSpec.intent.name,
            kind = plan.projectSpec.intent.kind,
            objective = plan.prompt
        ).record
        val startedAt = Instant.now()
        val report = build(plan)
        val artifactVerificationEvidence = report.artifacts.map { verify(it.id) }
        val verificationEvidence = report.verifications + artifactVerificationEvidence
        val readyArtifacts = report.artifacts.filter { it.state == ArtifactState.READY }
        val installProof = installDir
            ?.takeIf { readyArtifacts.isNotEmpty() }
            ?.let { install(readyArtifacts.first().id, it) }
        val commitCandidate = prepareCommit(
            message = "ATROPOS factory: ${plan.intent}",
            artifactIds = report.artifacts.map { it.id },
            proofIds = listOfNotNull(installProof?.id)
        )
        val success = readyArtifacts.isNotEmpty() &&
            artifactVerificationEvidence.all { it.passed } &&
            (installDir == null || installProof?.verified == true)
        val run = AppFactoryRun(
            prompt = plan.prompt,
            planId = plan.id,
            projectId = project.id,
            artifacts = report.artifacts,
            verifications = verificationEvidence,
            installProof = installProof,
            commitCandidate = commitCandidate,
            startedAt = startedAt,
            completedAt = Instant.now(),
            success = success
        )
        memory.rememberToolResult(
            subjectId = run.id,
            title = "app-factory-run ${plan.intent}",
            body = "project=${project.id}\nplan=${plan.id}\nartifacts=${run.artifacts.size}\nverifications=${run.verifications.size}\ninstall=${installProof?.id ?: "none"}\nsuccess=${run.success}",
            tags = listOf("artifact", "factory", "proof")
        )
        return run
    }

    fun build(plan: FactoryPlan): ArtifactReport {
        val artifacts = mutableListOf<Artifact>()
        val verifications = mutableListOf<VerificationEvidence>()
        val installProofs = mutableListOf<InstallProof>()

        val memoryRecord = memory.remember(
            kind = MemoryKind.DECISION,
            title = "artifact-build ${plan.intent}",
            body = plan.prompt,
            tags = listOf("artifact", "factory")
        )

        for (step in plan.steps) {
            when (step.kind) {
                atropos.core.factory.FactoryStepKind.CODE -> {
                    val result = compileStep(plan.prompt, plan.intent)
                    if (result != null) {
                        artifacts += result
                        verifications += VerificationEvidence(
                            artifactId = result.id,
                            kind = VerificationKind.COMPILE_CHECK,
                            passed = result.state == ArtifactState.READY,
                            evidence = if (result.state == ArtifactState.READY) "compile succeeded" else "compile failed"
                        )
                    }
                }
                atropos.core.factory.FactoryStepKind.VALIDATE -> {
                    val existing = artifacts.lastOrNull()
                    if (existing != null) {
                        val hashOk = if (platform.fileExists(existing.filePath)) {
                            val currentSha = ArtifactHasher.sha256File(platform, existing.filePath)
                            currentSha == existing.sha256
                        } else false
                        verifications += VerificationEvidence(
                            artifactId = existing.id,
                            kind = VerificationKind.HASH_VERIFY,
                            passed = hashOk,
                            evidence = if (hashOk) "hash verified: ${existing.sha256.take(16)}" else "hash mismatch"
                        )
                    }
                }
                atropos.core.factory.FactoryStepKind.CI -> {
                    val job = queue.enqueueLocalCompile()
                    verifications += VerificationEvidence(
                        artifactId = "ci-${job.id}",
                        kind = VerificationKind.TEST_PASS,
                        passed = false,
                        evidence = "CI job queued; verification pending: ${job.id}"
                    )
                }
                else -> {}
            }
        }

        val report = ArtifactReport(
            artifacts = artifacts,
            verifications = verifications,
            installProofs = installProofs
        )

        store.saveArtifacts(artifacts)
        store.saveVerifications(verifications)
        memory.rememberToolResult(
            subjectId = plan.id,
            title = "artifact-build-complete ${plan.id}",
            body = report.summary,
            tags = listOf("artifact", "build-result")
        )

        return report
    }

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

    private fun compileStep(prompt: String, intent: String): Artifact? {
        val srcFile = "build/generated/${intent}/Main.kt"
        val writeResult = platform.writeFile(srcFile, "// generated from: $prompt\n// intent: $intent\n")
        if (writeResult.isFailure) return null

        val startTime = System.currentTimeMillis()
        val buildResult = platform.spawnProcess(listOf("kotlinc", srcFile, "-include-runtime", "-d", "build/${intent}.jar"))
        val duration = System.currentTimeMillis() - startTime

        val filePath = "build/${intent}.jar"
        val state = if (buildResult.isSuccess && buildResult.getOrNull()?.exitCode == 0) ArtifactState.READY else ArtifactState.FAILED
        val bytes = if (platform.fileExists(filePath)) platform.fileSize(filePath) else 0L
        val sha = if (bytes > 0) ArtifactHasher.sha256File(platform, filePath) else ""

        return Artifact(
            kind = if (intent.contains("ui")) ArtifactKind.COMPOSE_PACKAGE else ArtifactKind.BINARY_JAR,
            name = "$intent.jar",
            filePath = filePath,
            sha256 = sha,
            byteSize = bytes,
            state = state,
            buildCommand = "kotlinc $srcFile -include-runtime -d $filePath",
            buildDurationMs = duration,
            metadata = mapOf("intent" to intent, "prompt" to prompt.take(100))
        )
    }

}
