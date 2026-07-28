package atropos.core.provider.adapter

data class NonOpenAiProviderSpec(
    val providerId: String,
    val displayName: String,
    val schema: NonOpenAiProviderSchema,
    val endpoint: String,
    val defaultModel: String,
    val fallbackModels: List<String>,
    val requiredEnv: List<String>
) {
    val models: List<String> = (listOf(defaultModel) + fallbackModels).distinct()
}
