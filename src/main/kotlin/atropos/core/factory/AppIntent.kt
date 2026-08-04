package atropos.core.factory

data class AppIntent(
    val name: String,
    val kind: String,
    val features: List<String>
)
