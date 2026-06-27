package atropos.core.provider.adapter

import atropos.core.provider.ProviderTask

data class AdapterRequest(
    val task: ProviderTask,
    val prompt: String,
    val context: String = "",
    val dryRun: Boolean = true,
    val requestId: String = "local-${System.currentTimeMillis()}"
)

data class AdapterModel(
    val id: String,
    val role: String,
    val freeOnlySafe: Boolean = true,
    val notes: String = ""
)

data class AdapterStatus(
    val providerId: String,
    val implemented: Boolean,
    val configured: Boolean,
    val dryRunOnly: Boolean,
    val modelCount: Int,
    val health: String,
    val detail: String = ""
)
