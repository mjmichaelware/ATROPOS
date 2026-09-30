package atropos.core.provider

class StaticProviderDescriptorRegistry : ProviderDescriptorRegistry {
    private val descriptors = listOf(
        d("ollama","Ollama",CostMode.FREE,0,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN,ApiCapability.LOCAL_TOOL),e("OLLAMA_HOST","OLLAMA_MODEL"),f("groq","gemini")),
        d("groq","Groq",CostMode.FREE,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("GROQ_API_KEY"),f("gemini","openrouter","github_models")),
        d("gemini","Google Gemini",CostMode.COOLDOWN_OK,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN,ApiCapability.LARGE_CONTEXT,ApiCapability.VISION),e("GEMINI_API_KEY"),f("groq","openrouter","github_models")),
        d("github_models","GitHub Models",CostMode.COOLDOWN_OK,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN,ApiCapability.CI),e("GITHUB_MODELS_TOKEN"),f("openrouter","groq","cloudflare_ai")),
        d("openrouter","OpenRouter",CostMode.FREE,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("OPENROUTER_API_KEY"),f("github_models","huggingface","deepinfra")),
        d("together","Together AI",CostMode.PAID_LOCKED,6,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("TOGETHER_API_KEY"),f("openrouter","groq")),
        d("fireworks","Fireworks AI",CostMode.PAID_LOCKED,6,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("FIREWORKS_API_KEY"),f("openrouter","groq")),
        d("azure_openai","Azure OpenAI",CostMode.PAID_LOCKED,8,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("AZURE_OPENAI_API_KEY","AZURE_OPENAI_ENDPOINT"),f("openrouter","groq")),
        d("cloudflare_ai","Cloudflare AI",CostMode.COOLDOWN_OK,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.EMBED,ApiCapability.EDGE),e("CLOUDFLARE_API_TOKEN","CLOUDFLARE_ACCOUNT_ID"),f("groq","ollama")),
        d("cloudflare_workers","Cloudflare Workers",CostMode.COOLDOWN_OK,1,c(ApiCapability.EDGE,ApiCapability.STORAGE,ApiCapability.SECRET),e("CLOUDFLARE_API_TOKEN","CLOUDFLARE_ACCOUNT_ID"),f("github_actions")),
        d("google_drive","Google Drive",CostMode.COOLDOWN_OK,1,c(ApiCapability.STORAGE),e("GOOGLE_APPLICATION_CREDENTIALS")),
        d("jina","Jina Reader",CostMode.COOLDOWN_OK,1,c(ApiCapability.READER,ApiCapability.WEB,ApiCapability.EMBED),e("JINA_API_KEY"),f("serpapi")),
        d("cerebras","Cerebras",CostMode.CREDIT_POOL,2,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR),e("CEREBRAS_API_KEY"),f("groq","sambanova")),
        d("deepinfra","DeepInfra",CostMode.CREDIT_POOL,2,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.EMBED),e("DEEPINFRA_API_KEY"),f("openrouter","huggingface")),
        d("huggingface","Hugging Face",CostMode.CREDIT_POOL,2,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.EMBED,ApiCapability.VISION,ApiCapability.ASSET),e("HUGGINGFACE_API_KEY"),f("deepinfra","openrouter")),
        d("nvidia","NVIDIA NIM",CostMode.CREDIT_POOL,2,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR),e("NVIDIA_API_KEY"),f("deepinfra","huggingface")),
        d("sambanova","SambaNova",CostMode.CREDIT_POOL,2,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR),e("SAMBANOVA_API_KEY"),f("groq","cerebras")),
        d("siliconflow","SiliconFlow",CostMode.CREDIT_POOL,2,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.ASSET),e("SILICONFLOW_API_KEY"),f("openrouter","huggingface")),
        d("supabase","Supabase",CostMode.OPTIONAL_FREE,2,c(ApiCapability.DATABASE,ApiCapability.VECTOR_DB,ApiCapability.EDGE,ApiCapability.STORAGE),e("SUPABASE_URL","SUPABASE_ANON_KEY")),
        d("pinecone","Pinecone",CostMode.OPTIONAL_FREE,2,c(ApiCapability.VECTOR_DB),e("PINECONE_API_KEY")),
        d("github_actions","GitHub Actions",CostMode.COOLDOWN_OK,2,c(ApiCapability.CI,ApiCapability.EDGE),e("GITHUB_TOKEN")),
        d("google_cloud_free","Google Cloud Free Tier",CostMode.OPTIONAL_FREE,2,c(ApiCapability.SECRET,ApiCapability.STORAGE,ApiCapability.EDGE),e("GOOGLE_APPLICATION_CREDENTIALS")),
        d("deepseek_direct","DeepSeek Direct",CostMode.PAID_LOCKED,6,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR),e("DEEPSEEK_API_KEY"),f("openrouter","groq")),
        d("cohere","Cohere",CostMode.PAID_LOCKED,7,c(ApiCapability.CHAT,ApiCapability.PLAN,ApiCapability.EMBED),e("COHERE_API_KEY"),f("jina","gemini")),
        d("mistral","Mistral",CostMode.PAID_LOCKED,7,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR),e("MISTRAL_API_KEY"),f("openrouter","groq")),
        d("anthropic","Anthropic",CostMode.PAID_LOCKED,9,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN,ApiCapability.LARGE_CONTEXT),e("ANTHROPIC_API_KEY"),f("gemini","groq")),
        d("openai","OpenAI",CostMode.PAID_LOCKED,9,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN,ApiCapability.VISION,ApiCapability.EMBED),e("OPENAI_API_KEY"),f("gemini","groq")),
        d("xai","xAI",CostMode.PAID_LOCKED,9,c(ApiCapability.CHAT,ApiCapability.PLAN),e("XAI_API_KEY"),f("gemini","groq")),
        d("aws_bedrock","AWS Bedrock",CostMode.PAID_LOCKED,9,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("AWS_ACCESS_KEY_ID","AWS_SECRET_ACCESS_KEY","AWS_REGION"),f("openrouter","groq")),
        d("fal","Fal.ai",CostMode.CREDIT_POOL,3,c(ApiCapability.ASSET,ApiCapability.VISION),e("FAL_AI_API_KEY"),f("huggingface","replicate")),
        d("replicate","Replicate",CostMode.CREDIT_POOL,3,c(ApiCapability.ASSET,ApiCapability.VISION),e("REPLICATE_API_TOKEN"),f("fal","huggingface")),
        d("serpapi","SerpAPI",CostMode.CREDIT_POOL,3,c(ApiCapability.WEB),e("SERPAPI_API_KEY"),f("jina")),
        d("serpapi","SerpAPI",CostMode.CREDIT_POOL,3,c(ApiCapability.WEB),e("SERPAPI_API_KEY"),f("jina")),
        d("deepseek_direct","DeepSeek Direct",CostMode.PAID_LOCKED,6,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR),e("DEEPSEEK_API_KEY"),f("openrouter","groq")),
        d("cohere","Cohere",CostMode.PAID_LOCKED,7,c(ApiCapability.CHAT,ApiCapability.PLAN,ApiCapability.EMBED),e("COHERE_API_KEY"),f("jina","gemini")),
        d("mistral","Mistral",CostMode.PAID_LOCKED,7,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR),e("MISTRAL_API_KEY"),f("openrouter","groq")),
        d("anthropic","Anthropic",CostMode.PAID_LOCKED,9,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN,ApiCapability.LARGE_CONTEXT),e("ANTHROPIC_API_KEY"),f("gemini","groq")),
        d("openai","OpenAI",CostMode.PAID_LOCKED,9,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN,ApiCapability.VISION,ApiCapability.EMBED),e("OPENAI_API_KEY"),f("gemini","groq")),
        d("xai","xAI",CostMode.PAID_LOCKED,9,c(ApiCapability.CHAT,ApiCapability.PLAN),e("XAI_API_KEY"),f("gemini","groq")),
        d("aws_bedrock","AWS Bedrock",CostMode.PAID_LOCKED,9,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("AWS_ACCESS_KEY_ID","AWS_SECRET_ACCESS_KEY","AWS_REGION"),f("openrouter","groq")),
        d("fal","Fal.ai",CostMode.CREDIT_POOL,3,c(ApiCapability.ASSET,ApiCapability.VISION),e("FAL_AI_API_KEY"),f("huggingface","replicate")),
        d("replicate","Replicate",CostMode.CREDIT_POOL,3,c(ApiCapability.ASSET,ApiCapability.VISION),e("REPLICATE_API_TOKEN"),f("fal","huggingface")),
        d("serpapi","SerpAPI",CostMode.CREDIT_POOL,3,c(ApiCapability.WEB),e("SERPAPI_API_KEY"),f("jina"))
    )
    override fun getAll() = descriptors
    override fun getById(id: String) = descriptors.find { it.id == id }
    override fun getFreeEligible() = descriptors.filter { it.isFreeEligible() }
    override fun getPaidLocked() = descriptors.filter { it.isPaid() }
    override fun getByCapability(capability: ApiCapability) = descriptors.filter { it.hasCapability(capability) }
    private fun d(id:String,name:String,cost:CostMode,tier:Int,caps:Set<ApiCapability>,env:List<String> = emptyList(),fb:List<String> = emptyList()) =
        ProviderDescriptor(id,name,cost,tier,caps,fb,fb)
    private fun c(vararg caps: ApiCapability) = caps.toSet()
    private fun e(vararg names: String) = names.toList()
    private fun f(vararg ids: String) = ids.toList()
}
