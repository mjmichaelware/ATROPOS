package atropos.core.provider.adapter

import atropos.core.provider.ProviderDescriptor

fun buildKernelAdapter(
    descriptor: ProviderDescriptor,
    env: Map<String, String> = System.getenv()
): ProviderAdapter =
    when {
        descriptor.isLocal ->
            LocalKernelAdapter(descriptor)
        OpenAiCompatibleProviderCatalog.get(descriptor.id) != null ->
            OpenAiCompatibleKernelAdapter(
                descriptor = descriptor,
                spec = OpenAiCompatibleProviderCatalog.get(descriptor.id)!!,
                env = env
            )
        NonOpenAiFreeProviderCatalog.get(descriptor.id)?.schema == NonOpenAiProviderSchema.GEMINI ->
            GeminiKernelAdapter(descriptor, NonOpenAiFreeProviderCatalog.get(descriptor.id)!!, env)
        NonOpenAiFreeProviderCatalog.get(descriptor.id)?.schema == NonOpenAiProviderSchema.GITHUB_MODELS ->
            GithubModelsKernelAdapter(descriptor, NonOpenAiFreeProviderCatalog.get(descriptor.id)!!, env)
        NonOpenAiFreeProviderCatalog.get(descriptor.id)?.schema == NonOpenAiProviderSchema.CLOUDFLARE_AI ->
            CloudflareAiKernelAdapter(descriptor, NonOpenAiFreeProviderCatalog.get(descriptor.id)!!, env)
        NonOpenAiFreeProviderCatalog.get(descriptor.id)?.schema == NonOpenAiProviderSchema.CLOUDFLARE_WORKERS ->
            CloudflareWorkersKernelAdapter(descriptor, NonOpenAiFreeProviderCatalog.get(descriptor.id)!!, env)
        NonOpenAiFreeProviderCatalog.get(descriptor.id)?.schema == NonOpenAiProviderSchema.ANTHROPIC ->
            AnthropicKernelAdapter(descriptor, NonOpenAiFreeProviderCatalog.get(descriptor.id)!!, env)
        DataInfraResearchProviderCatalog.get(descriptor.id)?.schema == DataInfraProviderSchema.JINA_READER ->
            JinaReaderKernelAdapter(descriptor, DataInfraResearchProviderCatalog.get(descriptor.id)!!, env)
        DataInfraResearchProviderCatalog.get(descriptor.id)?.schema == DataInfraProviderSchema.SERPAPI_WEB ->
            SerpApiKernelAdapter(descriptor, DataInfraResearchProviderCatalog.get(descriptor.id)!!, env)
        DataInfraResearchProviderCatalog.get(descriptor.id) != null ->
            LocalFallbackDataInfraAdapter(descriptor, DataInfraResearchProviderCatalog.get(descriptor.id)!!, env)
        AssetProviderCatalog.get(descriptor.id) != null ->
            AssetKernelAdapter(descriptor, AssetProviderCatalog.get(descriptor.id)!!, env)
        else ->
            DescriptorOnlyKernelAdapter(descriptor)
    }
