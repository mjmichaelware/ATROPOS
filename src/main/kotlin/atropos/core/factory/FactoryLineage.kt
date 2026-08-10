package atropos.core.factory

import atropos.core.security.RedactionFilter
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryAuthority
import atropos.core.memory.MemoryKind
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant

private val FACTORY_PROJECT_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

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
    val researchState: String = "COMPLETED_WITH_SOFT_FAILS",
    val contextHash: String? = null,
    val memoryPointers: List<String> = emptyList(),
    val atomResearch: List<String> = emptyList(),
    val atomizerStatus: String = "SKIPPED_SOFT_FAIL:SpecGraph unavailable; internal DAG fallback required",
    val clarificationAnswersSha256: String? = null,
    val clarificationLineageSha256: String? = null
) {
    fun atomizationState(): String = if (
        atomizerStatus.contains("SKIPPED_SOFT_FAIL", ignoreCase = true) ||
        atomizerStatus.contains("SOFT_SKIP", ignoreCase = true)
    ) {
        "COMPLETED_WITH_SOFT_FAILS"
    } else {
        "COMPLETED"
    }

    fun withPlan(
        planId: String,
        atomIds: List<String>,
        atomResearch: List<String> = emptyList(),
        memoryPointers: List<String> = emptyList()
    ): FactoryLineage {
        require(markersCover(atomIds, atomResearch, promptFingerprint, promptSpans, researchSha256)) {
            "atom research markers do not exactly cover the planned atoms or current lineage"
        }
        return copy(
            atomResearch = atomResearch,
            memoryPointers = (this.memoryPointers + memoryPointers).distinct()
        )
    }

    fun withContext(hash: String): FactoryLineage = copy(contextHash = hash)

    fun requireBoundTo(expectedProjectId: String, spec: AppProjectSpec) {
        val canonicalPrompt = spec.prompt.trim()
        val expectedPromptSha256 = sha256(canonicalPrompt)
        require(projectId == expectedProjectId) {
            "factory lineage project binding mismatch"
        }
        require(promptSha256 == expectedPromptSha256) {
            "factory lineage prompt hash does not match the current specification"
        }
        require(promptFingerprint == "prompt-${expectedPromptSha256.take(16)}") {
            "factory lineage prompt fingerprint does not match the current specification"
        }
        require(promptSpans.isNotBlank() && promptSpans != "none") {
            "factory lineage prompt spans are missing"
        }
        require(researchSha256 == sha256(researchDocument)) {
            "factory lineage research hash does not match the requirements artifact"
        }
        require(hasField(promptDocument, "project_id", expectedProjectId)) {
            "factory prompt artifact is bound to a different project"
        }
        require(hasField(promptDocument, "prompt_fingerprint", promptFingerprint)) {
            "factory prompt artifact fingerprint does not match the current lineage"
        }
        require(hasField(promptDocument, "sha256", expectedPromptSha256)) {
            "factory prompt artifact hash does not match the current specification"
        }
        require(hasField(promptDocument, "raw_text_sha256", expectedPromptSha256)) {
            "factory prompt artifact raw hash does not match the current specification"
        }
        require(hasField(promptDocument, "prompt_spans", promptSpans)) {
            "factory prompt artifact spans do not match the current lineage"
        }
        require(hasField(researchDocument, "prompt_fingerprint", promptFingerprint)) {
            "factory requirements artifact fingerprint does not match the current lineage"
        }
        require(hasField(researchDocument, "prompt_sha256", expectedPromptSha256)) {
            "factory requirements artifact is bound to a different prompt"
        }
        require(hasField(researchDocument, "prompt_spans", promptSpans)) {
            "factory requirements artifact spans do not match the current lineage"
        }
        if (clarificationAnswersSha256 != null || clarificationLineageSha256 != null) {
            require(clarificationAnswersSha256?.matches(Regex("[0-9a-f]{64}")) == true) {
                "factory clarification answer hash is malformed"
            }
            require(clarificationLineageSha256?.matches(Regex("[0-9a-f]{64}")) == true) {
                "factory clarification lineage hash is malformed"
            }
            require(hasField(researchDocument, "clarification_answers_sha256", clarificationAnswersSha256)) {
                "factory clarification answer hash is missing from requirements lineage"
            }
            require(hasField(researchDocument, "clarification_lineage_sha256", clarificationLineageSha256)) {
                "factory clarification lineage hash is missing from requirements lineage"
            }
        }
    }

    private fun hasField(document: String, name: String, expected: String): Boolean =
        document.lineSequence().any { it == "$name=$expected" }

    fun projectFiles(planId: String, atomIds: List<String>): Map<String, String> = mapOf(
        ".atropos/research/user-prompt.md" to promptDocument,
        ".atropos/research/requirements.md" to researchDocument,
        ".atropos/research/atoms.md" to buildString {
            appendLine("# Factory atoms")
            appendLine("project_id=$projectId")
            appendLine("prompt_fingerprint=$promptFingerprint")
            appendLine("prompt_sha256=$promptSha256")
            appendLine("research_sha256=$researchSha256")
            appendLine("research_state=$researchState")
            appendLine("prompt_spans=$promptSpans")
            appendLine("research_channels=$researchChannels")
            appendLine("memory_pointers=${memoryPointers.joinToString(",").ifBlank { "none" }}")
            contextHash?.let { appendLine("context_hash=$it") }
            val atomizer = if (atomizerStatus.contains("internal DAG fallback", ignoreCase = true)) {
                "internal-atropos-dag-fallback"
            } else {
                "specgraph"
            }
            appendLine("atomizer=$atomizer")
            appendLine("atomization_state=${atomizationState()}")
            appendLine("specgraph_status=$atomizerStatus")
            clarificationAnswersSha256?.let { appendLine("clarification_answers_sha256=$it") }
            clarificationLineageSha256?.let { appendLine("clarification_lineage_sha256=$it") }
            appendLine("internal_dag=$planId")
            atomResearch.forEach(::appendLine)
            atomIds.forEach {
                appendLine("$it | prompt_fingerprint=$promptFingerprint | prompt_sha256=$promptSha256 | prompt_spans=$promptSpans | research_sha256=$researchSha256 | research=bounded_channels_attempted")
            }
        }
    )

    companion object {
        internal fun markerBinds(
            marker: String,
            promptFingerprint: String?,
            promptSpans: String?,
            researchSha256: String?
        ): Boolean {
            val fields = marker.trim().split(Regex("\\s+")).toSet()
            fun matches(name: String, value: String?): Boolean =
                !value.isNullOrBlank() && "$name=$value" in fields

            return matches("prompt_fingerprint", promptFingerprint) &&
                matches("prompt_spans", promptSpans) &&
                matches("research_sha256", researchSha256)
        }

        internal fun markerAtomIds(markers: List<String>): Set<String> = markers.mapNotNull { marker ->
            marker.trim().split(Regex("\\s+")).firstOrNull { it.startsWith("atom=") }
                ?.removePrefix("atom=")
        }.toSet()

        internal fun markersCover(
            plannedAtomIds: List<String>,
            markers: List<String>,
            promptFingerprint: String?,
            promptSpans: String?,
            researchSha256: String?
        ): Boolean = if (plannedAtomIds.isEmpty()) {
            markers.isEmpty()
        } else {
            markerAtomIds(markers) == plannedAtomIds.toSet() &&
                markers.all { markerBinds(it, promptFingerprint, promptSpans, researchSha256) }
        }

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
            require(!hasSymbolicComponent(runRoot)) {
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

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

private fun writeAtomically(path: Path, content: String) {
    Files.createDirectories(path.parent)
    val temporary = Files.createTempFile(path.parent, ".${path.fileName}", ".tmp")
    try {
        Files.writeString(temporary, content, StandardCharsets.UTF_8)
        try {
            Files.move(
                temporary,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

data class FactoryClarificationEvidence(
    val answers: List<Boolean>,
    val answersSha256: String,
    val lineageSha256: String
)

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
    fun afterAnswers(answers: List<Boolean>): FactoryConfidence {
        require(answers.size == questions.size) {
            "one YES/NO answer is required per confidence question"
        }
        val uplift = answers.mapIndexed { index, answer ->
            if (!answer) 0 else 10 + index * 5
        }.sum()
        val resolvedScore = (score + uplift).coerceAtMost(100)
        return copy(
            score = resolvedScore,
            breakdown = "$breakdown,clarification_yes_uplift=$uplift",
            questions = if (resolvedScore >= MINIMUM) emptyList() else questions
        )
    }

    companion object {
        const val MINIMUM = 70

        fun calculate(spec: AppProjectSpec): FactoryConfidence {
            val clarity = if (spec.intent.name != "generated-app") 30 else 10
            val surface = if (spec.intent.kind.isNotBlank()) 25 else 0
            // An empty feature set means the request has no extracted primary
            // behavior. It must not reach the scaffold at the confidence
            // threshold merely because the surface defaults to CLI.
            val knowHow = if (spec.intent.features.isNotEmpty()) 25 else 0
            val gaps = if (spec.testRequired) 20 else 10
            val score = clarity + surface + knowHow + gaps
            val questions = if (score < MINIMUM) listOf(
                "Should the detected ${spec.intent.kind} surface be used?",
                "Does the prompt name a primary behavior to implement?"
            ) else emptyList()
            return FactoryConfidence(score, "clarity=$clarity,surface=$surface,know_how=$knowHow,gaps=$gaps", questions)
        }

        fun calculate(spec: AppProjectSpec, research: FactoryResearchReport): FactoryConfidence {
            val base = calculate(spec)
            val primaryChannels = setOf("st_memory", "lt_memory", "dloi", "lakehouse", "bounded_fetch")
            val researchPasses = research.channelLog.count { line ->
                val channel = line.substringBefore('=').trim()
                channel in primaryChannels && line.startsWith("$channel=PASS")
            }
            val score = (base.score + researchPasses.coerceAtMost(2) * 5).coerceAtMost(100)
            return base.copy(
                score = score,
                breakdown = "${base.breakdown},research_passes=$researchPasses",
                questions = if (score < MINIMUM) base.questions else emptyList()
            )
        }
    }
}
