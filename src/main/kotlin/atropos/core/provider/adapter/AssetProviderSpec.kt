package atropos.core.provider.adapter

data class AssetProviderSpec(
    val providerId: String,
    val displayName: String,
    val schema: AssetProviderSchema,
    val endpoint: String,
    val requiredEnv: List<String>,
    val defaultModel: String,
    val fallbackModels: List<String>
) {
    val models: List<String> = (listOf(defaultModel) + fallbackModels).distinct()
}
