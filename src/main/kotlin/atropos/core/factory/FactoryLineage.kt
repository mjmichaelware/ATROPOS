package atropos.core.factory

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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
    val clarificationLineageSha256: String? = null,
    val acceptanceFreeze: FactoryAcceptanceFreeze? = null
) {
    fun atomizationState(): String = if (
        atomizerStatus.contains("SKIPPED_SOFT_FAIL", ignoreCase = true) ||
        atomizerStatus.contains("SOFT_SKIP", ignoreCase = true)
    ) {
        "DEGRADED"
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

    fun withAcceptanceFreeze(atomIds: List<String>): FactoryLineage =
        if (acceptanceFreeze != null) this else copy(
            acceptanceFreeze = FactoryAcceptanceFreeze.create(
                promptSha256 = promptSha256,
                researchSha256 = researchSha256,
                atomIds = atomIds,
                promptSpans = promptSpans
            )
        )

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
            require(hasField(researchDocument, "clarification_answers_sha256", clarificationAnswersSha256 ?: "")) {
                "factory clarification answer hash is missing from requirements lineage"
            }
            require(hasField(researchDocument, "clarification_lineage_sha256", clarificationLineageSha256 ?: "")) {
                "factory clarification lineage hash is missing from requirements lineage"
            }
        }
    }

    private fun hasField(document: String, name: String, expected: String): Boolean =
        document.lineSequence().any { it == "$name=$expected" }

    fun projectFiles(planId: String, atomIds: List<String>): Map<String, String> = mapOf(
        ".atropos/research/user-prompt.md" to promptDocument,
        ".atropos/research/requirements.md" to researchDocument,
        ".atropos/research/acceptance-freeze.md" to acceptanceFreeze?.document.orEmpty(),
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

        fun sha256(value: String): String = FactoryLineageFactory.sha256(value)
    }
}

internal fun writeAtomically(path: Path, content: String) {
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
