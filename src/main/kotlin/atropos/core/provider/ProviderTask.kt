package atropos.core.provider

enum class ProviderTaskKind {
    CHAT_PROMPT, FAST_CODE_DRAFT, COMPILE_REPAIR, ARCHITECTURE_PLAN, LARGE_SOURCE_DOCS,
    WEB_DOCS_LOOKUP, EMBEDDINGS, VECTOR_MEMORY, DATABASE_STATE, EDGE_WORKER,
    REMOTE_COMPILE, ASSET_GENERATION, SCREENSHOT_REVIEW, SECRET_STORAGE, LOCAL_ONLY
}

data class ProviderTask(
    val kind: ProviderTaskKind,
    val capability: ApiCapability,
    val prompt: String,
    val interactive: Boolean = true,
    val maxLatencyMs: Long = if (interactive) 45_000 else 240_000,
    val localFirst: Boolean = true
)

class ProviderTaskClassifier {
    fun classify(prompt: String): ProviderTask {
        val lower = prompt.lowercase()
        return when {
            "compile error" in lower || "kotlinc" in lower || "stack trace" in lower || "unresolved reference" in lower ->
                ProviderTask(ProviderTaskKind.COMPILE_REPAIR, ApiCapability.REPAIR, prompt)
            "architecture" in lower || "blueprint" in lower || "phase" in lower || "tier" in lower ->
                ProviderTask(ProviderTaskKind.ARCHITECTURE_PLAN, ApiCapability.PLAN, prompt)
            "source document" in lower || "dloi" in lower || "lakehouse" in lower || "large context" in lower ->
                ProviderTask(ProviderTaskKind.LARGE_SOURCE_DOCS, ApiCapability.LARGE_CONTEXT, prompt, interactive = false)
            "search" in lower || "lookup" in lower || "docs" in lower || "url" in lower ->
                ProviderTask(ProviderTaskKind.WEB_DOCS_LOOKUP, ApiCapability.READER, prompt)
            "image" in lower || "asset" in lower || "screenshot" in lower || "photo" in lower ->
                ProviderTask(ProviderTaskKind.ASSET_GENERATION, ApiCapability.ASSET, prompt, interactive = false)
            "code" in lower || "function" in lower || "class" in lower || "fix" in lower || "implement" in lower ->
                ProviderTask(ProviderTaskKind.FAST_CODE_DRAFT, ApiCapability.CODE, prompt)
            else ->
                ProviderTask(ProviderTaskKind.CHAT_PROMPT, ApiCapability.CHAT, prompt)
        }
    }
}
