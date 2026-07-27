/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import atropos.core.provider.ProviderTruthService

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
        val inv = inventory(config).configured
        fun ok(name: String): Boolean = inv[name] == true && name !in unavailable
        val task = classify(prompt)
        return when (task) {
            TaskClass.PRIVACY_LOCAL -> RouteDecision("ollama", task, "privacy/local request")
            TaskClass.VERIFY_OR_BUILD -> RouteDecision("ollama", task, "local verifier/build should run before paid LLM")
            TaskClass.VISION_OR_MULTIMODAL -> when {
                ok("github_models") -> RouteDecision("github_models", task, "free cloud route")
                ok("cloudflare_ai") -> RouteDecision("cloudflare_ai", task, "free cloud route")
                else -> RouteDecision("ollama", task, "no implemented vision provider selected; local fallback")
            }
            TaskClass.CHEAP_FAST -> when {
                ok("groq") -> RouteDecision("groq", task, "fast/cheap cloud key configured")
                ok("github_models") -> RouteDecision("github_models", task, "free cloud fallback configured")
                ok("cloudflare_ai") -> RouteDecision("cloudflare_ai", task, "free cloud fallback configured")
                else -> RouteDecision("ollama", task, "no fast cloud key available")
            }
            TaskClass.SMALL_CHAT -> when {
                ok("groq") -> RouteDecision("groq", task, "low-latency chat")
                ok("github_models") -> RouteDecision("github_models", task, "free cloud chat fallback")
                ok("cloudflare_ai") -> RouteDecision("cloudflare_ai", task, "free cloud chat fallback")
                else -> RouteDecision("ollama", task, "local chat fallback")
            }
            TaskClass.CODING -> when {
                ok("groq") -> RouteDecision("groq", task, "fast coding fallback")
                ok("github_models") -> RouteDecision("github_models", task, "free coding fallback")
                ok("cloudflare_ai") -> RouteDecision("cloudflare_ai", task, "free coding fallback")
                else -> RouteDecision("ollama", task, "local coding fallback")
            }
            TaskClass.LONG_REASONING -> when {
                ok("github_models") -> RouteDecision("github_models", task, "free reasoning fallback")
                ok("cloudflare_ai") -> RouteDecision("cloudflare_ai", task, "free reasoning fallback")
                ok("groq") -> RouteDecision("groq", task, "fast reasoning fallback")
                else -> RouteDecision("ollama", task, "local long-reasoning fallback")
            }
            TaskClass.UNKNOWN -> when {
                ok("groq") -> RouteDecision("groq", task, "default fast configured route")
                ok("github_models") -> RouteDecision("github_models", task, "default free cloud route")
                ok("cloudflare_ai") -> RouteDecision("cloudflare_ai", task, "default free cloud route")
                else -> RouteDecision("ollama", task, "default local route")
            }
        }
    }

    fun providersReport(config: AtroposConfig): String {
        return ProviderTruthService(config).snapshot().renderInventory()
    }

}
