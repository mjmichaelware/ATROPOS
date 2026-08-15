package atropos.core.factory

import atropos.core.security.RedactionFilter
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryAuthority
import atropos.core.memory.MemoryKind
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

private val FACTORY_PROJECT_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

object FactoryLineageFactory {
    private val runRootGuard = FactoryRunRootGuard()

    fun prepare(
        root: Path,
        projectId: String,
        prompt: String,
        spec: AppProjectSpec,
        runMemory: LocalMemoryStore? = null,
        clarificationAnswers: List<Boolean> = emptyList()
    ): FactoryLineage {
        require(FACTORY_PROJECT_ID_PATTERN.matches(projectId)) {
            "factory project id must contain only portable identifier characters"
        }
        val normalizedRoot = root.toAbsolutePath().normalize()
        require(Files.isDirectory(normalizedRoot, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            "factory lineage root is unavailable"
        }
        require(!Files.isSymbolicLink(normalizedRoot)) {
            "factory lineage root cannot be a symbolic link"
        }
        val realRoot = normalizedRoot.toRealPath()
        val canonicalPrompt = prompt.trim()
        val redacted = RedactionFilter().redact(canonicalPrompt)
        val timestamp = Instant.now().toString()
        val promptSha = sha256(canonicalPrompt)
        val redactedPromptSha = sha256(redacted)
        val fingerprint = "prompt-${promptSha.take(16)}"
        val promptSpans = promptWordSpans(redacted, spec.intent.name, spec.intent.features)
        val promptDocument = """# User prompt artifact
project_id=$projectId
prompt_fingerprint=$fingerprint
sha256=$promptSha
raw_text_sha256=$promptSha
redacted_text_sha256=$redactedPromptSha
timestamp_utc=$timestamp
prompt_spans=$promptSpans
raw_text_redacted=$redacted
"""
        val runRoot = normalizedRoot.resolve(".atropos/research/factory").resolve(projectId).normalize()
        require(runRoot.startsWith(normalizedRoot)) { "factory lineage path escaped repository root" }
        // toRealPath() throws NoSuchFileException on a path that does not exist
        // yet, which is every first run for a new project, so it cannot be the
        // pre-creation guard. FactoryRunRootGuard proves the same property
        // against the deepest ancestor that does exist.
        require(runRootGuard.isSafeToCreate(runRoot, normalizedRoot)) {
            "factory lineage path is redirected before creation"
        }
        Files.createDirectories(runRoot)
        require(
            Files.isDirectory(runRoot, java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(runRoot) &&
                runRoot.toRealPath().startsWith(realRoot)
        ) { "factory lineage path is unavailable or redirected" }
        writeAtomically(runRoot.resolve("user-prompt.md"), promptDocument)
        val clarificationEvidence = if (clarificationAnswers.isEmpty()) {
            null
        } else {
            val request = FactoryClarificationRequest.load(runRoot)
            val evidence = FactoryClarificationRequest.loadEvidence(runRoot, request)
            require(evidence.answers == clarificationAnswers) {
                "factory clarification answers do not match the persisted lineage"
            }
            evidence
        }
        val memoryPointers = mutableListOf<String>()
        val effectiveMemory = runMemory ?: runCatching {
            LocalMemoryStore(root.resolve(".atropos/memory").toFile())
        }.getOrNull()
        val promptMemoryStatus = if (effectiveMemory == null) {
            "SKIPPED_SOFT_FAIL:memory_initialization"
        } else runCatching {
            val promptRecord = effectiveMemory.rememberDetailed(
                kind = MemoryKind.SESSION,
                title = "factory prompt artifact",
                body = buildString {
                    appendLine("project_id=$projectId")
                    appendLine("repository=${root.fileName}")
                    System.getenv("ATROPOS_OPERATOR_ID")?.takeIf { it.isNotBlank() }?.let { appendLine("operator_id=$it") }
                    appendLine("prompt_fingerprint=$fingerprint")
                    appendLine("prompt_sha256=$promptSha")
                    appendLine("redacted_prompt_sha256=$redactedPromptSha")
                    append(redacted)
                },
                tags = buildList {
                    add("factory")
                    add(projectId)
                    add(fingerprint)
                    System.getenv("ATROPOS_OPERATOR_ID")?.takeIf { it.isNotBlank() }?.let { add("operator-$it") }
                },
                subjectType = "factory-prompt",
                subjectId = projectId,
                sourceCoordinate = runRoot.resolve("user-prompt.md").toString(),
                authority = MemoryAuthority.OBSERVATION
            )
            memoryPointers += "st:${promptRecord.id}"
            "PASS"
        }.getOrElse { failure ->
            "SKIPPED_SOFT_FAIL:${failure.javaClass.simpleName.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(80)}"
        }
        val research = FactoryResearchService(memory = effectiveMemory).collect(
            root = root,
            prompt = redacted,
            projectId = projectId,
            promptSpans = promptSpans,
            promptFingerprint = fingerprint,
            promptArtifactMemoryStatus = promptMemoryStatus,
            providerSuggestionsPredicate = { report ->
                FactoryConfidence.calculate(spec, report).score < FactoryConfidence.MINIMUM
            }
        )
        var confidence = FactoryConfidence.calculate(spec, research)
        if (clarificationAnswers.isNotEmpty()) {
            confidence = confidence.afterAnswers(clarificationAnswers)
        }
        val researchDocument = """# Application requirements
project_id=$projectId
prompt_fingerprint=$fingerprint
prompt_sha256=$promptSha
goal=Generate a ${spec.intent.kind} named ${spec.intent.name} from the user request.
scope=${spec.intent.features.joinToString(", ").ifBlank { "general application behavior" }}
non_goals=No unrelated host-repository mutation; no provider prose executes directly.
tests=Generated source must compile and its executable tests must pass.
acceptance=Real source behavior, bounded territory, Git history, evidence, and independent audit.
confidence=${confidence.score}
confidence_breakdown=${confidence.breakdown}
prompt_spans=$promptSpans
${clarificationEvidence?.let { "clarification_answers_sha256=${it.answersSha256}\nclarification_lineage_sha256=${it.lineageSha256}\n" }.orEmpty()}${research.render().trimEnd()}

${FactoryRequirementStatements.render(spec.intent)}
"""
        writeAtomically(runRoot.resolve("requirements.md"), researchDocument)
        val requirementsMemoryStatus = if (effectiveMemory == null) {
            "SKIPPED_SOFT_FAIL:memory_unavailable"
        } else runCatching {
            val requirementsRecord = effectiveMemory.rememberDetailed(
                kind = MemoryKind.SOURCE,
                title = "factory requirements artifact",
                body = researchDocument,
                tags = listOf("factory", projectId, fingerprint, "requirements"),
                subjectType = "factory-requirements",
                subjectId = projectId,
                sourceCoordinate = runRoot.resolve("requirements.md").toString(),
                authority = MemoryAuthority.SOURCE_REFERENCE
            )
            memoryPointers += "st:${requirementsRecord.id}"
            "PASS"
        }.getOrElse { failure ->
            "SKIPPED_SOFT_FAIL:${failure.javaClass.simpleName.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(80)}"
        }
        if (confidence.score < FactoryConfidence.MINIMUM) {
            throw FactoryClarificationRequired(
                FactoryClarificationRequest.persist(runRoot, fingerprint, confidence.questions)
            )
        }
        return FactoryLineage(
            fingerprint,
            promptSha,
            sha256(researchDocument),
            researchDocument,
            promptDocument,
            projectId,
            confidence,
            promptSpans,
            research.render() + "requirements_memory=$requirementsMemoryStatus\n",
            research.lifecycleState(),
            memoryPointers = (research.memoryPointers.map { "hit:$it" } + memoryPointers).distinct(),
            atomizerStatus = research.specGraphStatus,
            clarificationAnswersSha256 = clarificationEvidence?.answersSha256,
            clarificationLineageSha256 = clarificationEvidence?.lineageSha256
        )
    }

    private fun promptWordSpans(prompt: String, appName: String, features: List<String>): String {
        val featureWords = (listOf(appName) + features)
            .map { it.lowercase() }
            .toSet()
        val matches = Regex("\\b[A-Za-z][A-Za-z0-9_-]*\\b").findAll(prompt).take(32)
        return matches.joinToString(";") {
            "${it.value}@${it.range.first}-${it.range.last + 1}|class=${classifySpan(it.value, featureWords)}"
        }.ifBlank { "none" }
    }

    private fun classifySpan(value: String, featureWords: Set<String>): String {
        val normalized = value.lowercase()
        return when {
            normalized in featureWords -> "feature"
            normalized in setOf("cli", "web", "android", "desktop") -> "surface-word"
            normalized in setOf("test", "tests", "readme", "license") -> "acceptance"
            normalized in setOf("must", "should", "required") -> "constraint"
            normalized in setOf("build", "create", "generate") -> "function"
            else -> "prose"
        }
    }

    internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
