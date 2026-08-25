/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider.adapter

object OpenAiCompatibleProviderCatalog {
    private val specs = listOf(
        OpenAiCompatibleProviderSpec(
            providerId = "groq",
            displayName = "Groq",
            baseUrl = "https://api.groq.com/openai/v1/chat/completions",
            defaultModel = "llama-3.1-8b-instant",
            fallbackModels = listOf("llama-3.3-70b-versatile"),
            apiKeyEnv = "GROQ_API_KEY",
            freeTier = true
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "openrouter",
            displayName = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1/chat/completions",
            defaultModel = "meta-llama/llama-3.1-8b-instruct:free",
            fallbackModels = listOf("mistralai/mistral-7b-instruct:free"),
            apiKeyEnv = "OPENROUTER_API_KEY",
            freeTier = true,
            headers = mapOf(
                "HTTP-Referer" to "https://local.atropos.invalid",
                "X-Title" to "ATROPOS"
            )
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "together",
            displayName = "Together AI",
            baseUrl = "https://api.together.xyz/v1/chat/completions",
            defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
            fallbackModels = listOf("meta-llama/Llama-3.2-3B-Instruct-Turbo"),
            apiKeyEnv = "TOGETHER_API_KEY",
            freeTier = false
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "fireworks",
            displayName = "Fireworks AI",
            baseUrl = "https://api.fireworks.ai/inference/v1/chat/completions",
            defaultModel = "accounts/fireworks/models/llama-v3p1-8b-instruct",
            fallbackModels = listOf("accounts/fireworks/models/mixtral-8x7b-instruct"),
            apiKeyEnv = "FIREWORKS_API_KEY",
            freeTier = false
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "azure_openai",
            displayName = "Azure OpenAI",
            baseUrl = "https://azure-openai.invalid/openai/deployments/atropos/chat/completions?api-version=2024-06-01",
            defaultModel = "atropos",
            fallbackModels = emptyList(),
            apiKeyEnv = "AZURE_OPENAI_API_KEY",
            freeTier = false,
            endpointEnv = "AZURE_OPENAI_ENDPOINT",
            apiKeyHeader = "api-key"
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "deepinfra",
            displayName = "DeepInfra",
            baseUrl = "https://api.deepinfra.com/v1/openai/chat/completions",
            defaultModel = "meta-llama/Meta-Llama-3.1-8B-Instruct",
            fallbackModels = listOf("mistralai/Mistral-7B-Instruct-v0.3"),
            apiKeyEnv = "DEEPINFRA_API_KEY",
            freeTier = false
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "siliconflow",
            displayName = "SiliconFlow",
            baseUrl = "https://api.siliconflow.cn/v1/chat/completions",
            defaultModel = "Qwen/Qwen2.5-7B-Instruct",
            fallbackModels = listOf("THUDM/glm-4-9b-chat"),
            apiKeyEnv = "SILICONFLOW_API_KEY",
            freeTier = false
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "openai",
            displayName = "OpenAI",
            baseUrl = "https://api.openai.com/v1/chat/completions",
            defaultModel = "gpt-4o-mini",
            fallbackModels = listOf("gpt-4o"),
            apiKeyEnv = "OPENAI_API_KEY",
            freeTier = false,
            endpointEnv = "OPENAI_API_BASE"
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "mistral",
            displayName = "Mistral",
            baseUrl = "https://api.mistral.ai/v1/chat/completions",
            defaultModel = "mistral-large-latest",
            fallbackModels = listOf("open-mistral-7b"),
            apiKeyEnv = "MISTRAL_API_KEY",
            freeTier = false
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "cohere",
            displayName = "Cohere",
            baseUrl = "https://api.cohere.com/v1/chat/completions",
            defaultModel = "command-r-plus",
            fallbackModels = listOf("command-r"),
            apiKeyEnv = "COHERE_API_KEY",
            freeTier = false
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "xai",
            displayName = "xAI",
            baseUrl = "https://api.x.ai/v1/chat/completions",
            defaultModel = "grok-2-1212",
            fallbackModels = listOf("grok-beta"),
            apiKeyEnv = "XAI_API_KEY",
            freeTier = false
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "deepseek_direct",
            displayName = "DeepSeek Direct",
            baseUrl = "https://api.deepseek.com/chat/completions",
            defaultModel = "deepseek-chat",
            fallbackModels = listOf("deepseek-coder"),
            apiKeyEnv = "DEEPSEEK_API_KEY",
            freeTier = false
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "cerebras",
            displayName = "Cerebras",
            baseUrl = "https://api.cerebras.ai/v1/chat/completions",
            defaultModel = "llama3.1-8b",
            fallbackModels = listOf("llama3.1-70b"),
            apiKeyEnv = "CEREBRAS_API_KEY",
            freeTier = false
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "nvidia",
            displayName = "NVIDIA NIM",
            baseUrl = "https://integrate.api.nvidia.com/v1/chat/completions",
            defaultModel = "meta/llama-3.1-8b-instruct",
            fallbackModels = listOf("meta/llama-3.1-70b-instruct"),
            apiKeyEnv = "NVIDIA_API_KEY",
            freeTier = false
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "sambanova",
            displayName = "SambaNova",
            baseUrl = "https://api.sambanova.ai/v1/chat/completions",
            defaultModel = "Meta-Llama-3.1-8B-Instruct",
            fallbackModels = listOf("Meta-Llama-3.1-70B-Instruct"),
            apiKeyEnv = "SAMBANOVA_API_KEY",
            freeTier = false
        )
    )

    fun get(providerId: String): OpenAiCompatibleProviderSpec? =
        specs.firstOrNull { it.providerId == providerId }

    fun all(): List<OpenAiCompatibleProviderSpec> = specs
}
