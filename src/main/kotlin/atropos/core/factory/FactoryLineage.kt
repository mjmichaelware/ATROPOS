package atropos.core.factory

import atropos.core.security.RedactionFilter
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
    fun withPlan(planId: String, atomIds: List<String>): FactoryLineage {
        val document = researchDocument + "\ninternal_dag=$planId\natoms=" + atomIds.joinToString(",") + "\n"
        return copy(researchDocument = document, researchSha256 = sha256(document))
    }

    fun withContext(hash: String): FactoryLineage = copy(contextHash = hash)

    fun projectFiles(planId: String, atomIds: List<String>): Map<String, String> = mapOf(
        ".atropos/research/user-prompt.md" to promptDocument,
        ".atropos/research/requirements.md" to researchDocument,
        ".atropos/research/atoms.md" to buildString {
            appendLine("# Factory atoms")
            appendLine("prompt_fingerprint=$promptFingerprint")
            appendLine("research_sha256=$researchSha256")
            appendLine("prompt_spans=$promptSpans")
            contextHash?.let { appendLine("context_hash=$it") }
            appendLine("atomizer=internal-atropos-dag-fallback")
            appendLine("internal_dag=$planId")
            atomIds.forEach { appendLine("$it | prompt_fingerprint=$promptFingerprint | research=bounded_channels_attempted") }
        }
    )

    companion object {
        fun prepare(root: Path, projectId: String, prompt: String, spec: AppProjectSpec): FactoryLineage {
            val redacted = RedactionFilter().redact(prompt.trim())
            val timestamp = Instant.now().toString()
            val promptSha = sha256(redacted)
            val fingerprint = "prompt-${promptSha.take(16)}"
            val promptSpans = promptWordSpans(redacted)
            val promptDocument = """# User prompt artifact
prompt_fingerprint=$fingerprint
sha256=$promptSha
timestamp_utc=$timestamp
raw_text=$redacted
"""
            val runRoot = root.resolve(".atropos/research/factory").resolve(projectId)
            Files.createDirectories(runRoot)
            Files.writeString(runRoot.resolve("user-prompt.md"), promptDocument, StandardCharsets.UTF_8)
            val confidence = FactoryConfidence.calculate(spec)
            if (confidence.score < FactoryConfidence.MINIMUM) {
                throw FactoryClarificationRequired(
                    FactoryClarificationRequest.persist(runRoot, fingerprint, confidence.questions)
                )
            }
            val research = FactoryResearchService().collect(root, redacted)
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
            return FactoryLineage(fingerprint, promptSha, sha256(researchDocument), researchDocument, promptDocument, projectId, confidence, promptSpans, research.render())
        }

        private fun promptWordSpans(prompt: String): String {
            val matches = Regex("\\b[A-Za-z][A-Za-z0-9_-]*\\b").findAll(prompt).take(32)
            return matches.joinToString(";") { "${it.value}@${it.range.first}-${it.range.last + 1}" }.ifBlank { "none" }
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
            val body = buildString {
                appendLine("prompt_fingerprint=${request.promptFingerprint}")
                appendLine("questions_sha256=${request.questionsSha256}")
                appendLine("answers_sha256=${FactoryLineage.sha256(rendered.joinToString("\n"))}")
                rendered.forEach(::appendLine)
            }
            val path = runRoot.resolve("clarification-answers.md")
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
    }
}
