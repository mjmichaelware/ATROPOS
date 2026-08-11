package atropos.core.factory

data class GeneratedAppProject(
    val path: String,
    val spec: AppProjectSpec,
    val files: List<String>,
    val evidencePath: String,
    val commitId: String,
    val branch: String,
    val treeSha256: String,
    val exportPath: String,
    val planningDagId: String? = null,
    val plannedAtomIds: List<String> = emptyList(),
    val proposalSha256: String = ""
)
