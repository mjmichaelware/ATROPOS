/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

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
        return ProviderInventory(
            configured = mapOf(
                "groq" to config.keys.groq.isNotBlank(),
                "openai" to config.keys.openai.isNotBlank(),
                "anthropic" to config.keys.anthropic.isNotBlank(),
                "xai" to config.keys.xai.isNotBlank()
            ),
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
                ok("openai") -> RouteDecision("openai", task, "vision-capable cloud route")
                else -> RouteDecision("ollama", task, "no implemented vision provider selected; local fallback")
            }
            TaskClass.CHEAP_FAST -> when {
                ok("groq") -> RouteDecision("groq", task, "fast/cheap cloud key configured")
                ok("openai") -> RouteDecision("openai", task, "cloud fallback configured")
                else -> RouteDecision("ollama", task, "no fast cloud key available")
            }
            TaskClass.SMALL_CHAT -> when {
                ok("groq") -> RouteDecision("groq", task, "low-latency chat")
                ok("openai") -> RouteDecision("openai", task, "cloud chat fallback")
                ok("anthropic") -> RouteDecision("anthropic", task, "cloud chat fallback")
                else -> RouteDecision("ollama", task, "local chat fallback")
            }
            TaskClass.CODING -> when {
                ok("anthropic") -> RouteDecision("anthropic", task, "coding/debugging priority")
                ok("openai") -> RouteDecision("openai", task, "coding fallback")
                ok("groq") -> RouteDecision("groq", task, "fast coding fallback")
                else -> RouteDecision("ollama", task, "local coding fallback")
            }
            TaskClass.LONG_REASONING -> when {
                ok("anthropic") -> RouteDecision("anthropic", task, "deep reasoning priority")
                ok("openai") -> RouteDecision("openai", task, "deep reasoning fallback")
                else -> RouteDecision("ollama", task, "local long-reasoning fallback")
            }
            TaskClass.UNKNOWN -> when {
                ok("anthropic") -> RouteDecision("anthropic", task, "default strongest configured route")
                ok("openai") -> RouteDecision("openai", task, "default configured route")
                ok("groq") -> RouteDecision("groq", task, "default fast configured route")
                else -> RouteDecision("ollama", task, "default local route")
            }
        }
    }

    fun providersReport(config: AtroposConfig): String {
        val inv = inventory(config)
        fun mark(name: String): String = if (inv.configured[name] == true) "configured" else "missing"
        val ollama = if (inv.ollama.online) "online ${inv.ollama.selectedModel} (${inv.ollama.models.size} models)" else "offline"
        return listOf(
            "providers:",
            "groq: ${mark("groq")}",
            "openai: ${mark("openai")}",
            "anthropic: ${mark("anthropic")}",
            "xai: ${mark("xai")}",
            "ollama: $ollama",
            "gemini: not installed"
        ).joinToString("\n")
    }
}
