package atropos.core.provider.adapter

object AssetProviderCatalog {
    private val specs = listOf(
        AssetProviderSpec(
            providerId = "huggingface",
            displayName = "Hugging Face",
            schema = AssetProviderSchema.HUGGINGFACE,
            endpoint = "https://api-inference.huggingface.co/models/{model}",
            requiredEnv = listOf("HUGGINGFACE_API_KEY"),
            defaultModel = "stabilityai/stable-diffusion-xl-base-1.0",
            fallbackModels = listOf("black-forest-labs/FLUX.1-schnell")
        ),
        AssetProviderSpec(
            providerId = "fal",
            displayName = "Fal.ai",
            schema = AssetProviderSchema.FAL,
            endpoint = "https://fal.run/{model}",
            requiredEnv = listOf("FAL_AI_API_KEY"),
            defaultModel = "fal-ai/flux/schnell",
            fallbackModels = listOf("fal-ai/fast-sdxl")
        ),
        AssetProviderSpec(
            providerId = "replicate",
            displayName = "Replicate",
            schema = AssetProviderSchema.REPLICATE,
            endpoint = "https://api.replicate.com/v1/predictions",
            requiredEnv = listOf("REPLICATE_API_TOKEN"),
            defaultModel = "black-forest-labs/flux-schnell",
            fallbackModels = listOf("stability-ai/sdxl")
        )
    )

    fun get(providerId: String): AssetProviderSpec? =
        specs.firstOrNull { it.providerId == providerId }

    fun all(): List<AssetProviderSpec> = specs
}
