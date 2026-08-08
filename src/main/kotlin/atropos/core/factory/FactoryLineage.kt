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

data class FactoryLineage(
    val promptFingerprint: String,
    val promptSha256: String,
    val researchSha256: String,
    val researchDocument: String,
    val promptDocument: String,
    val projectId: String,
    val confidence: FactoryConfidence,
    val promptSpans: String = "none",
    val researchChannels: String = "",
    val contextHash: String? = null
) {
    fun withPlan(
        planId: String,
        atomIds: List<String>,
        atomResearch: List<String> = emptyList()
    ): FactoryLineage {
        val document = buildString {
            append(researchDocument)
            appendLine("internal_dag=$planId")
            appendLine("atoms=${atomIds.joinToString(",")}")
            atomResearch.forEach(::appendLine)
        }
        return copy(researchDocument = document, researchSha256 = sha256(document))
    }

    fun withContext(hash: String): FactoryLineage = copy(contextHash = hash)

    fun projectFiles(planId: String, atomIds: List<String>): Map<String, String> = mapOf(
        ".atropos/research/user-prompt.md" to promptDocument,
        ".atropos/research/requirements.md" to researchDocument,
        ".atropos/research/atoms.md" to buildString {
            appendLine("# Factory atoms")
            appendLine("prompt_fingerprint=$promptFingerprint")
            appendLine("prompt_sha256=$promptSha256")
            appendLine("research_sha256=$researchSha256")
            appendLine("prompt_spans=$promptSpans")
            appendLine("research_channels=$researchChannels")
            contextHash?.let { appendLine("context_hash=$it") }
            appendLine("atomizer=internal-atropos-dag-fallback")
            appendLine("internal_dag=$planId")
            atomIds.forEach {
                appendLine("$it | prompt_fingerprint=$promptFingerprint | prompt_sha256=$promptSha256 | prompt_spans=$promptSpans | research=bounded_channels_attempted")
            }
        }
    )

    companion object {
        fun prepare(
            root: Path,
            projectId: String,
            prompt: String,
            spec: AppProjectSpec,
            runMemory: LocalMemoryStore? = null
        ): FactoryLineage {
            val canonicalPrompt = prompt.trim()
            val redacted = RedactionFilter().redact(canonicalPrompt)
            val timestamp = Instant.now().toString()
            val promptSha = sha256(canonicalPrompt)
            val redactedPromptSha = sha256(redacted)
            val fingerprint = "prompt-${promptSha.take(16)}"
            val promptSpans = promptWordSpans(redacted, spec.intent.name, spec.intent.features)
            val promptDocument = """# User prompt artifact
prompt_fingerprint=$fingerprint
sha256=$promptSha
raw_text_sha256=$promptSha
redacted_text_sha256=$redactedPromptSha
timestamp_utc=$timestamp
raw_text_redacted=$redacted
"""
            val runRoot = root.resolve(".atropos/research/factory").resolve(projectId)
            Files.createDirectories(runRoot)
            Files.writeString(runRoot.resolve("user-prompt.md"), promptDocument, StandardCharsets.UTF_8)
            val effectiveMemory = runMemory ?: LocalMemoryStore(root.resolve(".atropos/memory").toFile())
            val promptMemoryStatus = runCatching {
                effectiveMemory.rememberDetailed(
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
                "PASS"
            }.getOrElse { failure ->
                "SKIPPED_SOFT_FAIL:${failure.javaClass.simpleName.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(80)}"
            }
            val research = FactoryResearchService(memory = effectiveMemory).collect(
                root = root,
                prompt = redacted,
                projectId = projectId,
                promptSpans = promptSpans,
                promptArtifactMemoryStatus = promptMemoryStatus,
                providerSuggestionsPredicate = { report ->
                    FactoryConfidence.calculate(spec, report).score < FactoryConfidence.MINIMUM
                }
            )
            val confidence = FactoryConfidence.calculate(spec, research)
            val researchDocument = """# Application requirements
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
${research.render().trimEnd()}
"""
            Files.writeString(runRoot.resolve("requirements.md"), researchDocument, StandardCharsets.UTF_8)
            if (confidence.score < FactoryConfidence.MINIMUM) {
                throw FactoryClarificationRequired(
                    FactoryClarificationRequest.persist(runRoot, fingerprint, confidence.questions)
                )
            }
            return FactoryLineage(fingerprint, promptSha, sha256(researchDocument), researchDocument, promptDocument, projectId, confidence, promptSpans, research.render())
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

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

data class FactoryClarificationRequest(
    val promptFingerprint: String,
    val questions: List<String>,
    val questionsSha256: String,
    val path: String
) {
    companion object {
        fun persist(runRoot: Path, promptFingerprint: String, questions: List<String>): FactoryClarificationRequest {
            val body = buildString {
                appendLine("prompt_fingerprint=$promptFingerprint")
                appendLine("questions_sha256=${FactoryLineage.sha256(questions.joinToString("\n"))}")
                questions.forEachIndexed { index, question -> appendLine("q${index + 1}=YES/NO: $question") }
                appendLine("answers=UNANSWERED")
            }
            val path = runRoot.resolve("clarification-questions.md")
            Files.writeString(path, body, StandardCharsets.UTF_8)
            return FactoryClarificationRequest(
                promptFingerprint,
                questions,
                FactoryLineage.sha256(questions.joinToString("\n")),
                path.toString()
            )
        }

        fun persistAnswers(
            runRoot: Path,
            request: FactoryClarificationRequest,
            answers: List<Boolean>
        ): String {
            require(answers.size == request.questions.size) { "one YES/NO answer is required per question" }
            val rendered = answers.mapIndexed { index, answer -> "q${index + 1}=${if (answer) "YES" else "NO"}" }
            val answersSha256 = FactoryLineage.sha256(rendered.joinToString("\n"))
            val lineageSha256 = FactoryLineage.sha256(
                listOf(request.promptFingerprint, request.questionsSha256, answersSha256).joinToString("|")
            )
            val body = buildString {
                appendLine("prompt_fingerprint=${request.promptFingerprint}")
                appendLine("questions_sha256=${request.questionsSha256}")
                appendLine("timestamp_utc=${Instant.now()}")
                appendLine("answers_sha256=$answersSha256")
                appendLine("lineage_sha256=$lineageSha256")
                rendered.forEach(::appendLine)
            }
            val path = runRoot.resolve("clarification-answers.md")
            Files.createDirectories(runRoot)
            Files.writeString(path, body, StandardCharsets.UTF_8)
            return FactoryLineage.sha256(body)
        }
    }
}

class FactoryClarificationRequired(
    val request: FactoryClarificationRequest
) : IllegalArgumentException(request.questions.joinToString(" ") { "YES/NO: $it" }) {
    val questions: List<String> get() = request.questions
}

data class FactoryConfidence(
    val score: Int,
    val breakdown: String,
    val questions: List<String>
) {
    companion object {
        const val MINIMUM = 70

        fun calculate(spec: AppProjectSpec): FactoryConfidence {
            val clarity = if (spec.intent.name != "generated-app") 30 else 10
            val surface = if (spec.intent.kind.isNotBlank()) 25 else 0
            val knowHow = if (spec.intent.features.isNotEmpty()) 25 else 15
            val gaps = if (spec.testRequired) 20 else 10
            val score = clarity + surface + knowHow + gaps
            val questions = if (score < MINIMUM) listOf("Should this be a CLI, web app, or service?", "What is the primary behavior?") else emptyList()
            return FactoryConfidence(score, "clarity=$clarity,surface=$surface,know_how=$knowHow,gaps=$gaps", questions)
        }

        fun calculate(spec: AppProjectSpec, research: FactoryResearchReport): FactoryConfidence {
            val base = calculate(spec)
            val researchPasses = research.channelLog.count { it.contains("=PASS") }
            val score = (base.score + researchPasses.coerceAtMost(2) * 5).coerceAtMost(100)
            return base.copy(
                score = score,
                breakdown = "${base.breakdown},research_passes=$researchPasses",
                questions = if (score < MINIMUM) base.questions else emptyList()
            )
        }
    }
}
