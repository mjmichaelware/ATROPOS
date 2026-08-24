package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability

data class OpenAiCompatibleProviderSpec(
    val providerId: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val fallbackModels: List<String>,
    val apiKeyEnv: String,
    val freeTier: Boolean,
    val headers: Map<String, String> = emptyMap(),
    val endpointEnv: String? = null,
    val apiKeyHeader: String = "Authorization"
) {
    val models: List<String> = (listOf(defaultModel) + fallbackModels).distinct()
}
