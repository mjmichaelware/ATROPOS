package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability

object DataInfraResearchProviderCatalog {
    private val specs = listOf(
        DataInfraProviderSpec(
            providerId = "jina",
            displayName = "Jina Reader",
            schema = DataInfraProviderSchema.JINA_READER,
            endpoint = "https://r.jina.ai/http://example.com",
            requiredEnv = listOf("JINA_API_KEY"),
            capabilities = setOf(ApiCapability.READER, ApiCapability.WEB, ApiCapability.EMBED),
            localFallback = "local html/text reader"
        ),
        DataInfraProviderSpec(
            providerId = "serpapi",
            displayName = "SerpAPI",
            schema = DataInfraProviderSchema.SERPAPI_WEB,
            endpoint = "https://serpapi.com/search.json",
            requiredEnv = listOf("SERPAPI_API_KEY"),
            capabilities = setOf(ApiCapability.WEB),
            localFallback = "queued manual web lookup"
        ),
        DataInfraProviderSpec(
            providerId = "supabase",
            displayName = "Supabase",
            schema = DataInfraProviderSchema.SUPABASE_STORAGE_VECTOR,
            endpoint = "supabase optional database/vector/storage",
            requiredEnv = listOf("SUPABASE_URL", "SUPABASE_ANON_KEY"),
            capabilities = setOf(ApiCapability.DATABASE, ApiCapability.VECTOR_DB, ApiCapability.EDGE, ApiCapability.STORAGE),
            localFallback = "local jsonl memory and queue"
        ),
        DataInfraProviderSpec(
            providerId = "pinecone",
            displayName = "Pinecone",
            schema = DataInfraProviderSchema.PINECONE_VECTOR,
            endpoint = "pinecone optional vector index",
            requiredEnv = listOf("PINECONE_API_KEY"),
            capabilities = setOf(ApiCapability.VECTOR_DB),
            localFallback = "local lexical memory search"
        ),
        DataInfraProviderSpec(
            providerId = "google_drive",
            displayName = "Google Drive",
            schema = DataInfraProviderSchema.GOOGLE_DRIVE_STORAGE,
            endpoint = "google drive optional export target",
            requiredEnv = listOf("GOOGLE_APPLICATION_CREDENTIALS"),
            capabilities = setOf(ApiCapability.STORAGE),
            localFallback = "local Downloads export"
        ),
        DataInfraProviderSpec(
            providerId = "github_actions",
            displayName = "GitHub Actions",
            schema = DataInfraProviderSchema.GITHUB_ACTIONS_CI,
            endpoint = "github actions optional remote ci",
            requiredEnv = listOf("GITHUB_TOKEN"),
            capabilities = setOf(ApiCapability.CI, ApiCapability.EDGE),
            localFallback = "local work queue compile"
        ),
        DataInfraProviderSpec(
            providerId = "google_cloud_free",
            displayName = "Google Cloud Free Tier",
            schema = DataInfraProviderSchema.GOOGLE_CLOUD_FREE,
            endpoint = "google cloud optional secret/storage/edge",
            requiredEnv = listOf("GOOGLE_APPLICATION_CREDENTIALS"),
            capabilities = setOf(ApiCapability.SECRET, ApiCapability.STORAGE, ApiCapability.EDGE),
            localFallback = "local secret templates and file exports"
        )
    )

    fun get(providerId: String): DataInfraProviderSpec? =
        specs.firstOrNull { it.providerId == providerId }

    fun all(): List<DataInfraProviderSpec> = specs
}
