package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.CostMode
import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.ProviderFailure

abstract class BaseScaffoldAdapter(
    final override val providerId: String,
    registry: ProviderDescriptorRegistry,
    final override val models: List<AdapterModel>,
    private val envVars: List<String> = emptyList(),
    private val dryRunOnly: Boolean = true,
    private val implemented: Boolean = true,
    descriptorOverride: ProviderDescriptor? = null
) : ProviderAdapter {
    final override val descriptor: ProviderDescriptor =
        descriptorOverride ?: registry.getById(providerId)
            ?: error("missing descriptor for adapter: $providerId")

    final override val capabilities: Set<ApiCapability> =
        descriptor.capabilities

    final override fun status(): AdapterStatus {
        val configured = envVars.all { System.getenv(it).orEmpty().isNotBlank() }
        val health = when {
            dryRunOnly -> "dry_run"
            configured -> "configured"
            else -> "missing_key"
        }
        return AdapterStatus(
            providerId = providerId,
            implemented = implemented,
            configured = configured,
            dryRunOnly = dryRunOnly,
            modelCount = models.size,
            health = health,
            detail = envVars.joinToString(",").ifBlank { "no-env-required" }
        )
    }

    final override fun complete(request: AdapterRequest): ProviderCallResult {
        if (!canHandle(request)) {
            return ProviderCallResult.Failure(
                ProviderFailure(
                    providerId = providerId,
                    type = NormalizedProviderFailureType.MALFORMED_RESPONSE,
                    cleanSummary = "$providerId cannot handle ${request.task.capability.name.lowercase()}",
                    terminal = true
                )
            )
        }

        if (dryRunOnly || request.dryRun) {
            return ProviderCallResult.LocalOnly(
                task = request.task,
                content = "adapter=$providerId dry_run task=${request.task.kind.name.lowercase()} model=${models.firstOrNull()?.id ?: "none"}"
            )
        }

        val status = status()
        if (!status.configured) {
            return ProviderCallResult.Failure(
                ProviderFailure(
                    providerId = providerId,
                    type = NormalizedProviderFailureType.AUTH_FAILED,
                    cleanSummary = "$providerId missing required key",
                    terminal = true
                )
            )
        }

        return ProviderCallResult.LocalOnly(
            task = request.task,
            content = "adapter=$providerId configured scaffold; HTTP implementation intentionally deferred"
        )
    }
}

class LocalMockAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "local",
        registry = registry,
        models = listOf(AdapterModel("local-toolchain", "local compile/git probes")),
        envVars = emptyList(),
        dryRunOnly = false
    ),
    ChatProviderAdapter,
    CodeProviderAdapter,
    StorageProviderAdapter

class OllamaScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "ollama",
        registry = registry,
        models = listOf(
            AdapterModel("qwen2.5-coder", "local code fallback"),
            AdapterModel("llama3.2", "local chat fallback")
        ),
        envVars = listOf("OLLAMA_HOST", "OLLAMA_MODEL"),
        dryRunOnly = true
    ),
    ChatProviderAdapter,
    CodeProviderAdapter

class GroqScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "groq",
        registry = registry,
        models = listOf(
            AdapterModel("llama-3.3-70b-versatile", "fast chat"),
            AdapterModel("qwen/qwen3-32b", "fast code draft")
        ),
        envVars = listOf("GROQ_API_KEY"),
        dryRunOnly = true
    ),
    ChatProviderAdapter,
    CodeProviderAdapter

class GeminiScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "gemini",
        registry = registry,
        models = listOf(
            AdapterModel("gemini-2.5-flash", "large context"),
            AdapterModel("gemini-2.5-flash-lite", "cheap large context")
        ),
        envVars = listOf("GEMINI_API_KEY"),
        dryRunOnly = true
    ),
    ChatProviderAdapter,
    CodeProviderAdapter

class OpenRouterScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "openrouter",
        registry = registry,
        models = listOf(
            AdapterModel("deepseek/deepseek-r1:free", "free reasoning"),
            AdapterModel("qwen/qwen-2.5-coder-32b-instruct:free", "free code")
        ),
        envVars = listOf("OPENROUTER_API_KEY"),
        dryRunOnly = true
    ),
    ChatProviderAdapter,
    CodeProviderAdapter

class GitHubModelsScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "github_models",
        registry = registry,
        models = listOf(
            AdapterModel("gpt-4o-mini", "github hosted chat"),
            AdapterModel("deepseek-r1", "github hosted reasoning")
        ),
        envVars = listOf("GITHUB_MODELS_TOKEN"),
        dryRunOnly = true
    ),
    ChatProviderAdapter,
    CodeProviderAdapter

class CloudflareAiScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "cloudflare_ai",
        registry = registry,
        models = listOf(
            AdapterModel("@cf/meta/llama-3.1-8b-instruct", "edge chat"),
            AdapterModel("@cf/baai/bge-base-en-v1.5", "edge embeddings")
        ),
        envVars = listOf("CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID"),
        dryRunOnly = true
    ),
    ChatProviderAdapter,
    EmbeddingProviderAdapter,
    EdgeExecutionAdapter

class HuggingFaceScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "huggingface",
        registry = registry,
        models = listOf(
            AdapterModel("Qwen/Qwen2.5-Coder-32B-Instruct", "open code"),
            AdapterModel("BAAI/bge-base-en-v1.5", "open embeddings")
        ),
        envVars = listOf("HUGGINGFACE_API_KEY"),
        dryRunOnly = true
    ),
    ChatProviderAdapter,
    CodeProviderAdapter,
    EmbeddingProviderAdapter,
    AssetProviderAdapter

class NvidiaNimScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "nvidia",
        registry = registry,
        models = listOf(
            AdapterModel("meta/llama-3.1-70b-instruct", "nim chat"),
            AdapterModel("qwen/qwen2.5-coder-32b-instruct", "nim code")
        ),
        envVars = listOf("NVIDIA_API_KEY"),
        dryRunOnly = true
    ),
    ChatProviderAdapter,
    CodeProviderAdapter

class DeepInfraScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "deepinfra",
        registry = registry,
        models = listOf(
            AdapterModel("meta-llama/Meta-Llama-3.1-70B-Instruct", "open chat"),
            AdapterModel("Qwen/Qwen2.5-Coder-32B-Instruct", "open code")
        ),
        envVars = listOf("DEEPINFRA_API_KEY"),
        dryRunOnly = true
    ),
    ChatProviderAdapter,
    CodeProviderAdapter,
    EmbeddingProviderAdapter

class SiliconFlowScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "siliconflow",
        registry = registry,
        models = listOf(
            AdapterModel("deepseek-ai/DeepSeek-R1", "reasoning"),
            AdapterModel("Qwen/Qwen2.5-Coder-32B-Instruct", "code")
        ),
        envVars = listOf("SILICONFLOW_API_KEY"),
        dryRunOnly = true
    ),
    ChatProviderAdapter,
    CodeProviderAdapter,
    AssetProviderAdapter

class CerebrasScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "cerebras",
        registry = registry,
        models = listOf(
            AdapterModel("llama3.1-8b", "fast syntax"),
            AdapterModel("llama-3.3-70b", "fast chat")
        ),
        envVars = listOf("CEREBRAS_API_KEY"),
        dryRunOnly = true
    ),
    ChatProviderAdapter,
    CodeProviderAdapter

class SambaNovaScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "sambanova",
        registry = registry,
        models = listOf(
            AdapterModel("Meta-Llama-3.1-70B-Instruct", "fast structured output"),
            AdapterModel("Qwen2.5-Coder-32B-Instruct", "fast code")
        ),
        envVars = listOf("SAMBANOVA_API_KEY"),
        dryRunOnly = true
    ),
    ChatProviderAdapter,
    CodeProviderAdapter

class JinaReaderScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "jina",
        registry = registry,
        models = listOf(
            AdapterModel("jina-reader", "URL to clean markdown"),
            AdapterModel("jina-embeddings-v3", "embeddings")
        ),
        envVars = listOf("JINA_API_KEY"),
        dryRunOnly = true
    ),
    SearchProviderAdapter,
    EmbeddingProviderAdapter

class SerpApiScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "serpapi",
        registry = registry,
        models = listOf(AdapterModel("google-search", "scarce web lookup")),
        envVars = listOf("SERPAPI_API_KEY"),
        dryRunOnly = true
    ),
    SearchProviderAdapter

class GoogleDriveScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "google_drive",
        registry = registry,
        models = listOf(AdapterModel("drive-v3", "optional source-doc sync")),
        envVars = listOf("GOOGLE_APPLICATION_CREDENTIALS"),
        dryRunOnly = true
    ),
    StorageProviderAdapter

class LocalScraperScaffoldAdapter(registry: ProviderDescriptorRegistry) :
    BaseScaffoldAdapter(
        providerId = "local_scraper",
        registry = registry,
        descriptorOverride = ProviderDescriptor(
            id = "local_scraper",
            displayName = "Local Scraper",
            costMode = CostMode.LOCAL,
            quotaTier = 0,
            capabilities = setOf(ApiCapability.READER, ApiCapability.WEB, ApiCapability.LOCAL_TOOL),
            requiredEnv = emptyList(),
            fallbackChain = listOf("local"),
            endpointId = "local.scraper",
            isLocal = true,
            notes = "local degraded docs fallback"
        ),
        models = listOf(AdapterModel("local-reader", "local source-doc fallback")),
        envVars = emptyList(),
        dryRunOnly = false
    ),
    SearchProviderAdapter
