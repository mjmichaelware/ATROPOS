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
    val verificationOutputSha256: String? = null
) {
    fun render(hashes: Map<String, String>): String = buildString {
        appendLine("project=$projectPath")
        appendLine("commit=$commitId")
        appendLine("branch=$branch")
        appendLine("verification=$verification")
        appendLine("export=$exportPath")
        appendLine("tree_sha256=$treeSha256")
        verificationOutputSha256?.let { appendLine("verification_output_sha256=$it") }
        planningDagId?.let { appendLine("planning_dag=$it") }
        if (plannedAtomIds.isNotEmpty()) appendLine("planning_atoms=${plannedAtomIds.joinToString(",")}")
        hashes.toSortedMap().forEach { (path, hash) -> appendLine("$path $hash") }
    }
}
