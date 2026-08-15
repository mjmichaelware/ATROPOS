/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import java.time.Instant

data class ProviderSpec(
    val id: Int,
    val name: String,
    val quotaWeight: Int,
    val costPerToken: Double,
    val isFree: Boolean = false
)

data class TaskRoute(
    val task: String,
    val preferredProviderId: Int,
    val fallbackProviderId: Int,
    val alternativeProviderId: Int,
    val rateLimitHits: Int = 0
)

object FallbackChainRegistry {
    const val LOCAL_TOOLCHAIN = 0
    const val CUSTOM_USER_API = 29

    val providers = listOf(
        ProviderSpec(LOCAL_TOOLCHAIN, "LocalToolchain", 0, 0.0, true),
        ProviderSpec(1, "OpenRouter-Claude", 10, 0.000015),
        ProviderSpec(2, "OpenRouter-GPT4", 12, 0.000030),
        ProviderSpec(3, "OpenRouter-FreeRotation", 1, 0.0, true),
        ProviderSpec(CUSTOM_USER_API, "CustomUserApi", 5, 0.000005)
    )

    // Task routing matrix as data (14 tasks x 6 columns: task, pref, fb, alt, weight, cost)
    val taskRoutingMatrix = listOf(
        TaskRoute("chat", 1, 2, 3),
        TaskRoute("code", 1, 2, LOCAL_TOOLCHAIN),
        TaskRoute("repair", 1, LOCAL_TOOLCHAIN, 3),
        TaskRoute("planning", 1, 2, LOCAL_TOOLCHAIN),
        TaskRoute("docs", LOCAL_TOOLCHAIN, 1, 3),
        TaskRoute("search", LOCAL_TOOLCHAIN, 1, 3),
        TaskRoute("embed", LOCAL_TOOLCHAIN, 1, 2),
        TaskRoute("memory", LOCAL_TOOLCHAIN, 1, CUSTOM_USER_API),
        TaskRoute("secret", LOCAL_TOOLCHAIN, CUSTOM_USER_API, 1),
        TaskRoute("edge", LOCAL_TOOLCHAIN, CUSTOM_USER_API, 3),
        TaskRoute("asset", LOCAL_TOOLCHAIN, 1, 2),
        TaskRoute("completion", 1, 2, LOCAL_TOOLCHAIN),
        TaskRoute("audit", LOCAL_TOOLCHAIN, CUSTOM_USER_API, 1),
        TaskRoute("deploy", LOCAL_TOOLCHAIN, 1, 2)
    )

    // Named, inspectable chains
    val CHAT_CHAIN = listOf(1, 2, 3)
    val CODE_CHAIN = listOf(1, 2, LOCAL_TOOLCHAIN)
    val REPAIR_CHAIN = listOf(1, LOCAL_TOOLCHAIN, 3)
    val PLANNING_CHAIN = listOf(1, 2, LOCAL_TOOLCHAIN)
    val DOCS_CHAIN = listOf(LOCAL_TOOLCHAIN, 1, 3)
    val SEARCH_CHAIN = listOf(LOCAL_TOOLCHAIN, 1, 3)
    val EMBED_CHAIN = listOf(LOCAL_TOOLCHAIN, 1, 2)
    val MEMORY_CHAIN = listOf(LOCAL_TOOLCHAIN, 1, CUSTOM_USER_API)
    val SECRET_CHAIN = listOf(LOCAL_TOOLCHAIN, CUSTOM_USER_API, 1)
    val EDGE_CHAIN = listOf(LOCAL_TOOLCHAIN, CUSTOM_USER_API, 3)
    val ASSET_CHAIN = listOf(LOCAL_TOOLCHAIN, 1, 2)

    /** OpenRouter free-model rotation when rate-limited. */
    fun getActiveOpenRouterModel(rateLimited: Boolean): Int {
        return if (rateLimited) {
            3 // fallback to free-model rotation
        } else {
            1 // primary model
        }
    }

    /** Primary sort logic: quota_weight ASC. */
    fun getSortedProviders(): List<ProviderSpec> {
        return providers.sortedBy { it.quotaWeight }
    }
}
