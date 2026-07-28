package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability

data class AdapterModel(
    val id: String,
    val free: Boolean,
    val capabilities: Set<ApiCapability>
)
