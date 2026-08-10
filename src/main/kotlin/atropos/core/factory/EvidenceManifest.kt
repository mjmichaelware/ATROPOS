package atropos.core.factory

data class EvidenceManifest(
    val projectPath: String,
    val commitId: String,
    val branch: String,
    val files: List<String>,
    val verification: String,
    val exportPath: String,
    val treeSha256: String,
    val planningDagId: String? = null,
    val plannedAtomIds: List<String> = emptyList(),
    val verificationOutputSha256: String? = null,
    val promptSha256: String? = null,
    val promptFingerprint: String? = null,
    val researchSha256: String? = null,
    val directorDecision: String? = null,
    val auditorDecision: String? = null,
    val auditorReportSha256: String? = null,
    val completionGate: String? = null,
    val promptSpans: String? = null,
    val researchChannels: String? = null,
    val contextHash: String? = null,
    val atomResearch: List<String> = emptyList(),
    val memoryPointers: List<String> = emptyList(),
    val atomizerStatus: String? = null,
    val journalRunId: String? = null,
    val hrRouterRequestId: String? = null,
    val hrRouterAction: String? = null,
    val proposalSha256: String? = null,
    val clarificationAnswersSha256: String? = null,
    val clarificationLineageSha256: String? = null
) {
    /**
     * Fail closed before the evidence commit when the manifest cannot prove
     * the source, lineage, independent decisions, and per-file digests that
     * the factory completion gate requires.
     */
    fun requireComplete(hashes: Map<String, String>) {
        require(commitId.matches(Regex("[0-9a-f]{40}"))) { "evidence commit is missing or malformed" }
        require(branch.isNotBlank()) { "evidence branch is missing" }
        require(files.isNotEmpty() && files.distinct().size == files.size) {
            "evidence file list is empty or contains duplicate paths"
        }
        require(files.all(::isSafeRelativePath)) {
            "evidence contains an absolute or traversal path"
        }
        require(hashes.keys == files.toSet()) { "evidence file/hash coverage is incomplete" }
        require(hashes.values.all { it.matches(Regex("[0-9a-f]{64}")) }) { "evidence contains an invalid file hash" }
        require(treeSha256.matches(Regex("[0-9a-f]{64}"))) { "evidence tree hash is missing or malformed" }
        require(verification.isNotBlank() && exportPath.isNotBlank()) { "evidence verification or export record is missing" }
        require(verificationOutputSha256?.matches(Regex("[0-9a-f]{64}")) == true) {
            "evidence verification output hash is missing or malformed"
        }
        require(promptSha256?.matches(Regex("[0-9a-f]{64}")) == true && researchSha256?.matches(Regex("[0-9a-f]{64}")) == true) {
            "evidence lineage hashes are missing or malformed"
        }
        require(proposalSha256?.matches(Regex("[0-9a-f]{64}")) == true) {
            "evidence source proposal hash is missing or malformed"
        }
        require(clarificationAnswersSha256 == null || clarificationAnswersSha256.matches(Regex("[0-9a-f]{64}"))) {
            "evidence clarification answer hash is malformed"
        }
        require(clarificationLineageSha256 == null || clarificationLineageSha256.matches(Regex("[0-9a-f]{64}"))) {
            "evidence clarification lineage hash is malformed"
        }
        require((clarificationAnswersSha256 == null) == (clarificationLineageSha256 == null)) {
            "evidence clarification hashes must be provided together"
        }
        require(promptFingerprint?.matches(Regex("prompt-[0-9a-f]{16}")) == true && !promptSpans.isNullOrBlank() && promptSpans != "none") {
            "evidence prompt lineage is incomplete"
        }
        require(FactoryLineage.markersCover(plannedAtomIds, atomResearch, promptFingerprint, promptSpans, researchSha256)) {
            "evidence atom research lineage is incomplete"
        }
        require(directorDecision.isExactDecision("director advisory: no blocking drift")) {
            "evidence director decision is missing"
        }
        require(auditorDecision.isExactDecision("auditor promotion gate passed")) {
            "evidence auditor decision is missing"
        }
        require(auditorReportSha256?.matches(Regex("[0-9a-f]{64}")) == true) {
            "evidence auditor report hash is missing or malformed"
        }
        require(completionGate.isExactDecision("factory completion gate passed")) {
            "evidence completion gate decision is missing"
        }
        require(!atomizerStatus.isNullOrBlank() && !journalRunId.isNullOrBlank()) {
            "evidence atomizer or journal lineage is missing"
        }
    }

    private fun String?.isExactDecision(expected: String): Boolean =
        this?.trim()?.equals(expected, ignoreCase = true) == true

    private fun isSafeRelativePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized.isNotBlank() &&
            normalized != "." &&
            !normalized.startsWith('/') &&
            !normalized.contains("//") &&
            normalized.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    fun render(hashes: Map<String, String>): String = buildString {
        appendLine("project=$projectPath")
        appendLine("commit=$commitId")
        appendLine("branch=$branch")
        appendLine("verification=$verification")
        appendLine("export=$exportPath")
        appendLine("tree_sha256=$treeSha256")
        verificationOutputSha256?.let { appendLine("verification_output_sha256=$it") }
        promptSha256?.let { appendLine("prompt_sha256=$it") }
        promptFingerprint?.let { appendLine("prompt_fingerprint=$it") }
        researchSha256?.let { appendLine("research_sha256=$it") }
        proposalSha256?.let { appendLine("proposal_sha256=$it") }
        if (promptFingerprint != null || promptSha256 != null || researchSha256 != null) {
            appendLine("prompt_artifact=.atropos/research/user-prompt.md")
            appendLine("requirements_artifact=.atropos/research/requirements.md")
            appendLine("atoms_artifact=.atropos/research/atoms.md")
        }
        directorDecision?.let { appendLine("director=$it") }
        auditorDecision?.let { appendLine("auditor=$it") }
        auditorReportSha256?.let { appendLine("auditor_report_sha256=$it") }
        completionGate?.let { appendLine("completion_gate=$it") }
        promptSpans?.let { appendLine("prompt_spans=$it") }
        researchChannels?.let { appendLine("research_channels=$it") }
        contextHash?.let { appendLine("context_hash=$it") }
        if (memoryPointers.isNotEmpty()) appendLine("memory_pointers=${memoryPointers.joinToString(",")}")
        atomizerStatus?.let { appendLine("specgraph_status=$it") }
        hrRouterRequestId?.let { appendLine("hr_router_request=$it") }
        hrRouterAction?.let { appendLine("hr_router_action=$it") }
        clarificationAnswersSha256?.let { appendLine("clarification_answers_sha256=$it") }
        clarificationLineageSha256?.let { appendLine("clarification_lineage_sha256=$it") }
        journalRunId?.let {
            appendLine("journal_run_id=$it")
            appendLine("journal_path=.atropos/runs/$it/events.journal")
        }
        planningDagId?.let { appendLine("planning_dag=$it") }
        if (plannedAtomIds.isNotEmpty()) appendLine("planning_atoms=${plannedAtomIds.joinToString(",")}")
        atomResearch.forEach { appendLine("atom_research=$it") }
        hashes.toSortedMap().forEach { (path, hash) -> appendLine("$path $hash") }
    }
}
