package atropos.core.factory

data class AppProjectSpec(
    val prompt: String,
    val intent: AppIntent,
    val testRequired: Boolean = true,
    val standardFiles: List<String> = listOf("README.md", "LICENSE", ".gitignore", "AGENTS.md")
)
