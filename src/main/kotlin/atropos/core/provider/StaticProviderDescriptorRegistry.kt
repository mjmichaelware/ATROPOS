package atropos.core.provider

class StaticProviderDescriptorRegistry : ProviderDescriptorRegistry {
    private val descriptors = listOf(
        d("ollama","Ollama",CostMode.FREE,0,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN,ApiCapability.LOCAL_TOOL),e("OLLAMA_HOST","OLLAMA_MODEL"),f("groq","gemini"), ctx = 131072),
        d("groq","Groq",CostMode.FREE,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("GROQ_API_KEY"),f("gemini","openrouter","github_models"), ctx = 131072),
        d("cerebras","Cerebras",CostMode.FREE,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("CEREBRAS_API_KEY"),f("groq","sambanova"), ctx = 131072),
        d("sambanova","SambaNova",CostMode.FREE,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("SAMBANOVA_API_KEY"),f("groq","cerebras"), ctx = 262144),
        d("gemini","Google Gemini",CostMode.COOLDOWN_OK,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN,ApiCapability.LARGE_CONTEXT,ApiCapability.VISION),e("GEMINI_API_KEY"),f("groq","openrouter","github_models"), ctx = 1048576),
        d("github_models","GitHub Models",CostMode.COOLDOWN_OK,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN,ApiCapability.CI),e("GITHUB_MODELS_TOKEN"),f("openrouter","groq","cloudflare_ai"), ctx = 128000),
        d("openrouter","OpenRouter",CostMode.FREE,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("OPENROUTER_API_KEY"),f("github_models","huggingface","deepinfra"), ctx = 131072),
        d("together","Together AI",CostMode.PAID_LOCKED,6,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("TOGETHER_API_KEY"),f("openrouter","groq"), ctx = 32768),
        d("fireworks","Fireworks AI",CostMode.PAID_LOCKED,6,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("FIREWORKS_API_KEY"),f("openrouter","groq"), ctx = 32768),
        d("azure_openai","Azure OpenAI",CostMode.PAID_LOCKED,8,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("AZURE_OPENAI_API_KEY","AZURE_OPENAI_ENDPOINT"),f("openrouter","groq"), ctx = 128000),
        d("cloudflare_ai","Cloudflare AI",CostMode.COOLDOWN_OK,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.EMBED,ApiCapability.EDGE),e("CLOUDFLARE_API_TOKEN","CLOUDFLARE_ACCOUNT_ID"),f("groq","ollama"), ctx = 128000),
        d("cloudflare_workers","Cloudflare Workers",CostMode.COOLDOWN_OK,1,c(ApiCapability.EDGE,ApiCapability.STORAGE,ApiCapability.SECRET),e("CLOUDFLARE_API_TOKEN","CLOUDFLARE_ACCOUNT_ID"),f("github_actions"), ctx = 0),
        d("google_drive","Google Drive",CostMode.COOLDOWN_OK,1,c(ApiCapability.STORAGE),e("GOOGLE_APPLICATION_CREDENTIALS"), ctx = 0),
        d("jina","Jina Reader",CostMode.COOLDOWN_OK,1,c(ApiCapability.READER,ApiCapability.WEB,ApiCapability.EMBED),e("JINA_API_KEY"),f("serpapi"), ctx = 0),
        d("deepinfra","DeepInfra",CostMode.CREDIT_POOL,2,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.EMBED),e("DEEPINFRA_API_KEY"),f("openrouter","huggingface"), ctx = 32768),
        d("huggingface","Hugging Face",CostMode.CREDIT_POOL,2,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.EMBED,ApiCapability.VISION,ApiCapability.ASSET),e("HUGGINGFACE_API_KEY"),f("deepinfra","openrouter"), ctx = 32768),
        d("nvidia","NVIDIA NIM",CostMode.CREDIT_POOL,2,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR),e("NVIDIA_API_KEY"),f("deepinfra","huggingface"), ctx = 131072),
        d("siliconflow","SiliconFlow",CostMode.CREDIT_POOL,2,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.ASSET),e("SILICONFLOW_API_KEY"),f("openrouter","huggingface"), ctx = 32768),
        d("supabase","Supabase",CostMode.OPTIONAL_FREE,2,c(ApiCapability.DATABASE,ApiCapability.VECTOR_DB,ApiCapability.EDGE,ApiCapability.STORAGE),e("SUPABASE_URL","SUPABASE_ANON_KEY"), ctx = 0),
        d("pinecone","Pinecone",CostMode.OPTIONAL_FREE,2,c(ApiCapability.VECTOR_DB),e("PINECONE_API_KEY"), ctx = 0),
        d("github_actions","GitHub Actions",CostMode.COOLDOWN_OK,2,c(ApiCapability.CI,ApiCapability.EDGE),e("GITHUB_TOKEN"), ctx = 0),
        d("google_cloud_free","Google Cloud Free Tier",CostMode.OPTIONAL_FREE,2,c(ApiCapability.SECRET,ApiCapability.STORAGE,ApiCapability.EDGE),e("GOOGLE_APPLICATION_CREDENTIALS"), ctx = 0),
        d("deepseek","DeepSeek",CostMode.FREE,1,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("DEEPSEEK_API_KEY"),f("groq","openrouter"), ctx = 65536),
        d("deepseek_direct","DeepSeek Direct",CostMode.PAID_LOCKED,6,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR),e("DEEPSEEK_API_KEY"),f("openrouter","groq"), ctx = 65536),
        d("cohere","Cohere",CostMode.PAID_LOCKED,7,c(ApiCapability.CHAT,ApiCapability.PLAN,ApiCapability.EMBED),e("COHERE_API_KEY"),f("jina","gemini"), ctx = 128000),
        d("mistral","Mistral",CostMode.PAID_LOCKED,7,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR),e("MISTRAL_API_KEY"),f("openrouter","groq"), ctx = 131072),
        d("anthropic","Anthropic",CostMode.PAID_LOCKED,9,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN,ApiCapability.LARGE_CONTEXT),e("ANTHROPIC_API_KEY"),f("gemini","groq"), ctx = 200000),
        d("openai","OpenAI",CostMode.PAID_LOCKED,9,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN,ApiCapability.VISION,ApiCapability.EMBED),e("OPENAI_API_KEY"),f("gemini","groq"), ctx = 128000),
        d("xai","xAI",CostMode.PAID_LOCKED,9,c(ApiCapability.CHAT,ApiCapability.PLAN),e("XAI_API_KEY"),f("gemini","groq"), ctx = 131072),
        d("aws_bedrock","AWS Bedrock",CostMode.PAID_LOCKED,9,c(ApiCapability.CHAT,ApiCapability.CODE,ApiCapability.REPAIR,ApiCapability.PLAN),e("AWS_ACCESS_KEY_ID","AWS_SECRET_ACCESS_KEY","AWS_REGION"),f("openrouter","groq"), ctx = 200000),
        d("fal","Fal.ai",CostMode.CREDIT_POOL,3,c(ApiCapability.ASSET,ApiCapability.VISION),e("FAL_AI_API_KEY"),f("huggingface","replicate"), ctx = 0),
        d("replicate","Replicate",CostMode.CREDIT_POOL,3,c(ApiCapability.ASSET,ApiCapability.VISION),e("REPLICATE_API_TOKEN"),f("fal","huggingface"), ctx = 0),
        d("serpapi","SerpAPI",CostMode.CREDIT_POOL,3,c(ApiCapability.WEB),e("SERPAPI_API_KEY"),f("jina"), ctx = 0)
    )
    override fun getAll() = descriptors
    override fun getById(id: String) = descriptors.find { it.id == id } ?: if (id == "deepseek") descriptors.find { it.id == "deepseek_direct" } else null
    override fun getFreeEligible() = descriptors.filter { it.isFreeEligible() }
    override fun getPaidLocked() = descriptors.filter { it.isPaid() }
    override fun getByCapability(capability: ApiCapability) = descriptors.filter { it.hasCapability(capability) }
    private fun d(id:String,name:String,cost:CostMode,tier:Int,caps:Set<ApiCapability>,env:List<String> = emptyList(),fb:List<String> = emptyList(),ctx:Int = ProviderContextWindow.DEFAULT_CONTEXT_WINDOW_TOKENS) =
        ProviderDescriptor(id,name,cost,tier,caps,fb,fb, contextWindowTokens = ctx)
    private fun c(vararg caps: ApiCapability) = caps.toSet()
    private fun e(vararg names: String) = names.toList()
    private fun f(vararg ids: String) = ids.toList()
}
