package atropos.core.factory

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

private val FACTORY_PROJECT_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

data class FactoryClarificationRequest(
    val promptFingerprint: String,
    val questions: List<String>,
    val questionsSha256: String,
    val path: String
) {
    companion object {
        fun persist(runRoot: Path, promptFingerprint: String, questions: List<String>): FactoryClarificationRequest {
            require(promptFingerprint.matches(Regex("prompt-[0-9a-f]{16}"))) {
                "factory clarification prompt lineage is invalid"
            }
            require(questions.isNotEmpty() && questions.size <= 8) {
                "factory clarification question count is outside the bounded range"
            }
            require(questions.all { it.isNotBlank() && it.length <= 240 && '\n' !in it && '\r' !in it }) {
                "factory clarification questions must be bounded single-line text"
            }
            val body = buildString {
                appendLine("prompt_fingerprint=$promptFingerprint")
                appendLine("questions_sha256=${FactoryLineage.sha256(questions.joinToString("\n"))}")
                questions.forEachIndexed { index, question -> appendLine("q${index + 1}=YES/NO: $question") }
                appendLine("answers=UNANSWERED")
            }
            val normalizedRoot = safeRunRoot(runRoot)
            val path = normalizedRoot.resolve("clarification-questions.md")
            writeAtomically(path, body)
            return FactoryClarificationRequest(
                promptFingerprint,
                questions,
                FactoryLineage.sha256(questions.joinToString("\n")),
                path.toString()
            )
        }

        /**
         * Rehydrates the one clarification artifact emitted by [persist].
         * Answers must be loaded from that artifact so a caller cannot attach
         * YES/NO data to a different prompt by supplying a copied fingerprint.
         */
        fun load(runRoot: Path): FactoryClarificationRequest {
            val normalizedRoot = safeRunRoot(runRoot)
            val projectId = normalizedRoot.fileName?.toString().orEmpty()
            require(FACTORY_PROJECT_ID_PATTERN.matches(projectId)) { "invalid factory clarification project id" }
            val path = normalizedRoot.resolve("clarification-questions.md").normalize()
            require(path.parent == normalizedRoot && Files.isRegularFile(path) && !Files.isSymbolicLink(path)) {
                "factory clarification questions artifact is unavailable"
            }
            val fields = Files.readAllLines(path, StandardCharsets.UTF_8)
            val fingerprint = fields.firstOrNull { it.startsWith("prompt_fingerprint=") }
                ?.substringAfter('=')?.trim().orEmpty()
            require(fingerprint.matches(Regex("prompt-[0-9a-f]{16}"))) {
                "factory clarification prompt lineage is invalid"
            }
            val questionEntries = fields
                .filter { it.matches(Regex("q[1-8]=YES/NO: .+")) }
                .map { line ->
                    line.substringAfter('q').substringBefore('=').toInt() to
                        line.substringAfter("YES/NO: ").trim()
                }
                .sortedBy { it.first }
            require(questionEntries.size in 1..8) {
                "factory clarification question count is outside the bounded range"
            }
            require(questionEntries.map { it.first } == (1..questionEntries.size).toList()) {
                "factory clarification question indexes are not contiguous"
            }
            val questions = questionEntries.map { it.second }
            require(questions.isNotEmpty()) { "factory clarification questions are missing" }
            require(questions.all { it.length <= 240 && '\n' !in it && '\r' !in it }) {
                "factory clarification questions exceed the bounded line length"
            }
            val questionsSha = FactoryLineage.sha256(questions.joinToString("\n"))
            val recordedSha = fields.firstOrNull { it.startsWith("questions_sha256=") }
                ?.substringAfter('=')?.trim()
            require(recordedSha == questionsSha) { "factory clarification question hash mismatch" }
            return FactoryClarificationRequest(fingerprint, questions, questionsSha, path.toString())
        }

        fun persistAnswers(
            runRoot: Path,
            request: FactoryClarificationRequest,
            answers: List<Boolean>
        ): String {
            val normalizedRoot = safeRunRoot(runRoot)
            val requestPath = Path.of(request.path).toAbsolutePath().normalize()
            require(requestPath.parent == normalizedRoot && requestPath.fileName.toString() == "clarification-questions.md") {
                "clarification answers must remain beside the original questions artifact"
            }
            require(!Files.isSymbolicLink(requestPath) && Files.isRegularFile(requestPath)) {
                "clarification questions artifact is unavailable"
            }
            val persistedRequest = load(normalizedRoot)
            require(
                Path.of(persistedRequest.path).toAbsolutePath().normalize() == requestPath &&
                    persistedRequest.promptFingerprint == request.promptFingerprint &&
                    persistedRequest.questionsSha256 == request.questionsSha256 &&
                    persistedRequest.questions == request.questions
            ) {
                "clarification request does not match the persisted questions artifact"
            }
            require(
                FactoryLineage.sha256(persistedRequest.questions.joinToString("\n")) == persistedRequest.questionsSha256
            ) {
                "clarification question lineage is inconsistent"
            }
            require(answers.size == persistedRequest.questions.size) { "one YES/NO answer is required per question" }
            val rendered = answers.mapIndexed { index, answer -> "q${index + 1}=${if (answer) "YES" else "NO"}" }
            val answersSha256 = FactoryLineage.sha256(rendered.joinToString("\n"))
            val lineageSha256 = FactoryLineage.sha256(
                listOf(persistedRequest.promptFingerprint, persistedRequest.questionsSha256, answersSha256).joinToString("|")
            )
            val body = buildString {
                appendLine("prompt_fingerprint=${persistedRequest.promptFingerprint}")
                appendLine("questions_sha256=${persistedRequest.questionsSha256}")
                appendLine("timestamp_utc=${Instant.now()}")
                appendLine("answers_sha256=$answersSha256")
                appendLine("lineage_sha256=$lineageSha256")
                rendered.forEach(::appendLine)
            }
            val path = runRoot.resolve("clarification-answers.md")
            require(!Files.isSymbolicLink(path)) { "clarification answers cannot be a symbolic link" }
            Files.createDirectories(runRoot)
            writeAtomically(path, body)
            return FactoryLineage.sha256(body)
        }

        fun loadPrompt(runRoot: Path): String {
            val normalizedRoot = safeRunRoot(runRoot)
            val path = normalizedRoot.resolve("user-prompt.md").normalize()
            require(path.parent == normalizedRoot && Files.isRegularFile(path) && !Files.isSymbolicLink(path)) {
                "factory prompt artifact is unavailable"
            }
            val fields = Files.readAllLines(path, StandardCharsets.UTF_8)
            val prompt = fields.firstOrNull { it.startsWith("raw_text_redacted=") }
                ?.substringAfter('=')?.trim().orEmpty()
            val rawHash = fields.firstOrNull { it.startsWith("raw_text_sha256=") }
                ?.substringAfter('=')?.trim().orEmpty()
            val redactedHash = fields.firstOrNull { it.startsWith("redacted_text_sha256=") }
                ?.substringAfter('=')?.trim().orEmpty()
            require(prompt.isNotBlank() && FactoryLineage.sha256(prompt) == redactedHash) {
                "factory prompt artifact has invalid replay text"
            }
            require(rawHash == redactedHash) {
                "factory prompt contains redacted material; replay requires the original prompt"
            }
            return prompt
        }

        fun loadAnswers(runRoot: Path, request: FactoryClarificationRequest): List<Boolean> {
            return loadEvidence(runRoot, request).answers
        }

        fun loadEvidence(runRoot: Path, request: FactoryClarificationRequest): FactoryClarificationEvidence {
            val normalizedRoot = safeRunRoot(runRoot)
            val path = normalizedRoot.resolve("clarification-answers.md").normalize()
            require(path.parent == normalizedRoot && Files.isRegularFile(path) && !Files.isSymbolicLink(path)) {
                "factory clarification answers artifact is unavailable"
            }
            val fields = Files.readAllLines(path, StandardCharsets.UTF_8)
            require(fields.firstOrNull { it.startsWith("prompt_fingerprint=") }
                ?.substringAfter('=')?.trim() == request.promptFingerprint) {
                "factory clarification answer fingerprint mismatch"
            }
            require(fields.firstOrNull { it.startsWith("questions_sha256=") }
                ?.substringAfter('=')?.trim() == request.questionsSha256) {
                "factory clarification answer question hash mismatch"
            }
            val entries = fields.filter { it.matches(Regex("q[1-8]=(YES|NO)")) }
                .map { it.substringAfter('q').substringBefore('=').toInt() to it.substringAfter('=' ) }
                .sortedBy { it.first }
            require(entries.map { it.first } == (1..request.questions.size).toList()) {
                "factory clarification answer indexes are incomplete"
            }
            val rendered = entries.mapIndexed { index, entry -> "q${index + 1}=${entry.second}" }
            val answersSha = FactoryLineage.sha256(rendered.joinToString("\n"))
            require(fields.firstOrNull { it.startsWith("answers_sha256=") }
                ?.substringAfter('=')?.trim() == answersSha) {
                "factory clarification answer hash mismatch"
            }
            val lineageSha = FactoryLineage.sha256(
                listOf(request.promptFingerprint, request.questionsSha256, answersSha).joinToString("|")
            )
            require(fields.firstOrNull { it.startsWith("lineage_sha256=") }
                ?.substringAfter('=')?.trim() == lineageSha) {
                "factory clarification answer lineage mismatch"
            }
            return FactoryClarificationEvidence(
                answers = entries.map { it.second == "YES" },
                answersSha256 = answersSha,
                lineageSha256 = lineageSha
            )
        }

        private fun safeRunRoot(runRoot: Path): Path {
            val normalized = runRoot.toAbsolutePath().normalize()
            require(Files.isDirectory(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                "factory clarification root is unavailable"
            }
            require(!hasSymbolicComponent(normalized)) {
                "factory clarification root is redirected"
            }
            require(runCatching { normalized.toRealPath() }.isSuccess) {
                "factory clarification root cannot be resolved"
            }
            return normalized
        }

        private fun hasSymbolicComponent(path: Path): Boolean {
            val root = path.root ?: return true
            var cursor = root
            for (part in root.relativize(path)) {
                cursor = cursor.resolve(part)
                if (Files.isSymbolicLink(cursor)) return true
            }
            return false
        }
    }
}
