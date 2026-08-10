/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import atropos.core.provider.ProviderTruthService
import atropos.core.provider.ApiCapability
import atropos.core.provider.StaticProviderDescriptorRegistry

enum class TaskClass {
    SMALL_CHAT,
    CODING,
    LONG_REASONING,
    PRIVACY_LOCAL,
    CHEAP_FAST,
    VISION_OR_MULTIMODAL,
    VERIFY_OR_BUILD,
    UNKNOWN
}

data class RouteDecision(
    val provider: String,
    val taskClass: TaskClass,
    val reason: String
)

data class ProviderInventory(
    val configured: Map<String, Boolean>,
    val ollama: OllamaStatus
)

class ProviderDecisionEngine {
    private val registry = StaticProviderDescriptorRegistry()

    fun inventory(config: AtroposConfig): ProviderInventory {
        val truth = ProviderTruthService(config).snapshot()
        return ProviderInventory(
            configured = truth.records.associate { it.id to it.keyPresent },
            ollama = OllamaHealthProbe().probe()
        )
    }

    fun classify(prompt: String): TaskClass {
        val p = prompt.lowercase().trim()
        return when {
            p.startsWith("/verify") || p == "verify" || p.contains("run build") || p.contains("run tests") -> TaskClass.VERIFY_OR_BUILD
            p.contains("local only") || p.contains("offline") || p.contains("private") || p.contains("no cloud") -> TaskClass.PRIVACY_LOCAL
            p.contains("image") || p.contains("screenshot") || p.contains("vision") || p.contains("multimodal") -> TaskClass.VISION_OR_MULTIMODAL
            p.contains("kotlin") || p.contains("compile error") || p.contains("stack trace") || p.contains("debug") || p.contains("fix this") || p.contains("code") || p.contains("function") || p.contains("class ") -> TaskClass.CODING
            p.length > 1200 || p.contains("architecture") || p.contains("deep reasoning") || p.contains("plan this system") -> TaskClass.LONG_REASONING
            p.contains("cheap") || p.contains("fast") || p.contains("quick") -> TaskClass.CHEAP_FAST
            p.length <= 120 -> TaskClass.SMALL_CHAT
            else -> TaskClass.UNKNOWN
        }
    }

    fun decide(prompt: String, config: AtroposConfig, unavailable: Set<String> = emptySet()): RouteDecision {
        val task = classify(prompt)
        val truth = ProviderTruthService(config).snapshot()
        val capability = capabilityFor(task)
        val capabilityIds = registry.getByCapability(capability).map { it.id }.toSet()
        val preferred = when (task) {
            TaskClass.CODING,
            TaskClass.VERIFY_OR_BUILD -> truth.patchOrder
            else -> truth.askOrder
        }.filterNot(unavailable::contains)
        val selected = preferred.firstOrNull { it in capabilityIds }
            ?: preferred.firstOrNull()
            ?: registry.getByCapability(capability).firstOrNull()?.id
            ?: truth.selectedProvider
        return RouteDecision(selected, task, "canonical provider descriptor/truth route")
    }

    fun providersReport(config: AtroposConfig): String {
        return ProviderTruthService(config).snapshot().renderInventory()
    }

    private fun capabilityFor(task: TaskClass): ApiCapability = when (task) {
        TaskClass.PRIVACY_LOCAL -> ApiCapability.LOCAL_TOOL
        TaskClass.VERIFY_OR_BUILD -> ApiCapability.REPAIR
        TaskClass.VISION_OR_MULTIMODAL -> ApiCapability.VISION
        TaskClass.CODING -> ApiCapability.CODE
        TaskClass.LONG_REASONING -> ApiCapability.PLAN
        else -> ApiCapability.CHAT
    }

}
