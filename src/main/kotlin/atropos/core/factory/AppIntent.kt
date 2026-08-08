package atropos.core.factory

data class AppIntent(
    val name: String,
    val kind: String,
    val features: List<String>
) {
    fun capabilities(): Set<AppCapability> = features
        .map { it.lowercase() }
        .mapNotNull { AppCapability.fromFeature(it) }
        .toSet()
}

enum class AppCapability {
    ARITHMETIC;

    companion object {
        fun fromFeature(feature: String): AppCapability? = when (feature) {
            "calculator", "math", "arithmetic", "expression" -> ARITHMETIC
            else -> null
        }
    }
}
