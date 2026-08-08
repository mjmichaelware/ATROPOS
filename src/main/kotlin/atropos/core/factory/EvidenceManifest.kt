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
    val auditorDecision: String? = null,
    val completionGate: String? = null,
    val promptSpans: String? = null,
    val researchChannels: String? = null,
    val contextHash: String? = null
) {
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
        auditorDecision?.let { appendLine("auditor=$it") }
        completionGate?.let { appendLine("completion_gate=$it") }
        promptSpans?.let { appendLine("prompt_spans=$it") }
        researchChannels?.let { appendLine("research_channels=$it") }
        contextHash?.let { appendLine("context_hash=$it") }
        planningDagId?.let { appendLine("planning_dag=$it") }
        if (plannedAtomIds.isNotEmpty()) appendLine("planning_atoms=${plannedAtomIds.joinToString(",")}")
        hashes.toSortedMap().forEach { (path, hash) -> appendLine("$path $hash") }
    }
}
