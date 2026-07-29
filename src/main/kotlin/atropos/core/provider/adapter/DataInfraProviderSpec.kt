package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability

data class DataInfraProviderSpec(
    val providerId: String,
    val displayName: String,
    val schema: DataInfraProviderSchema,
    val endpoint: String,
    val requiredEnv: List<String>,
    val capabilities: Set<ApiCapability>,
    val localFallback: String
)
