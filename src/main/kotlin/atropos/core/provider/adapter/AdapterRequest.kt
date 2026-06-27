package atropos.core.provider.adapter

import atropos.core.provider.ProviderTask

data class AdapterRequest(
    val task: ProviderTask,
    val prompt: String,
    val context: String = "",
    val dryRun: Boolean = true,
    val deadlineEpochMs: Long = System.currentTimeMillis() + task.maxLatencyMs,
    val liveNetworkAllowed: Boolean = System.getenv()["ATROPOS_LIVE_PROVIDER_TESTS"] == "1",
    val metadata: Map<String, String> = emptyMap()
)

data class AdapterStatus(
    val providerId: String,
    val implemented: Boolean,
    val configured: Boolean,
    val dryRunOnly: Boolean,
    val modelCount: Int,
    val health: String,
    val detail: String
)
