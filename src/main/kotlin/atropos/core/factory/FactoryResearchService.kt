package atropos.core.factory

import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemorySearchHit
import atropos.dloi.DloiLookupResult
import atropos.dloi.DloiService
import atropos.dloi.HigZeroGuard
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest

data class FactoryResearchReport(
    val channelLog: List<String>,
    val memoryPointers: List<String>,
    val fetchHashes: List<String>,
    val specGraphStatus: String
) {
    fun render(): String = buildString {
        channelLog.forEach { appendLine(it) }
        if (memoryPointers.isNotEmpty()) appendLine("memory_pointers=${memoryPointers.joinToString(",")}")
        if (fetchHashes.isNotEmpty()) appendLine("fetch_hashes=${fetchHashes.joinToString(",")}")
        appendLine("specgraph=$specGraphStatus")
    }
}

/** Bounded, soft-failing research channels for factory requirements. */
class FactoryResearchService(
    private val memory: LocalMemoryStore? = null,
    private val maxFetchBytes: Int = 32 * 1024,
    private val timeoutMillis: Int = 750,
    private val dLoI: HigZeroGuard? = null,
    private val minimumScopedRelevance: Int = 2,
    private val operatorId: String? = System.getenv("ATROPOS_OPERATOR_ID")?.trim()?.takeIf { it.isNotBlank() }
) {
    init {
        require(minimumScopedRelevance > 0) { "scoped memory relevance threshold must be positive" }
    }

    fun collect(
        root: Path,
        prompt: String,
        projectId: String = root.fileName?.toString().orEmpty(),
        providerSuggestionsRequired: Boolean = false,
        promptSpans: String = "none",
        promptArtifactMemoryStatus: String = "UNRECORDED",
        providerSuggestionsPredicate: ((FactoryResearchReport) -> Boolean)? = null
    ): FactoryResearchReport {
        val log = mutableListOf<String>()
        val pointers = mutableListOf<String>()
        val hashes = mutableListOf<String>()
        val query = prompt.trim().take(160)
        val localMemory = memory ?: LocalMemoryStore(root.resolve(".atropos/memory").toFile())
        log += "prompt_artifact_memory=$promptArtifactMemoryStatus"

        if (operatorId == null) {
            log += "st_memory=SKIPPED_SOFT_FAIL:user_scope_unset"
            log += "lt_memory=SKIPPED_SOFT_FAIL:user_scope_unset"
        } else {
            runCatching { localMemory.search(query, limit = 16) }
                .onSuccess { hits ->
                    val scoped = hits.filter { it.score >= minimumScopedRelevance && it.isInFactoryScope(projectId, root, operatorId) }
                    log += "st_memory=PASS scoped_hits=${scoped.size} rejected=${hits.size - scoped.size}"
                    pointers += scoped.map { it.record.id }
                }
                .onFailure { log += "st_memory=SKIPPED_SOFT_FAIL:${safeReason(it)}" }

            runCatching { localMemory.search(query, limit = 24) }
                .onSuccess { hits ->
                    val scoped = hits.filter { it.score >= minimumScopedRelevance && it.isInFactoryScope(projectId, root, operatorId) }
                    log += "lt_memory=PASS scoped_records=${scoped.size} rejected=${hits.size - scoped.size}"
                }
                .onFailure { log += "lt_memory=SKIPPED_SOFT_FAIL:${safeReason(it)}" }
        }

        val dLoIResult = runCatching {
            (dLoI ?: HigZeroGuard(DloiService(root))).resolveTask(query)
        }.getOrElse { failure ->
            DloiLookupResult.NoMatch(query, safeReason(failure))
        }
        when (dLoIResult) {
            is DloiLookupResult.Resolved -> {
                val resolution = dLoIResult.resolution
                hashes += sha256(resolution.provenance)
                log += "dloi=PASS address=${resolution.coordinate.documentId}#${resolution.coordinate.sectionId ?: "none"}@L${resolution.coordinate.lineStart}-${resolution.coordinate.lineEnd}"
                log += "dloi_route=PASS exact_address=true; address_only=true; no_cosine_rag=true"
            }
            is DloiLookupResult.NoMatch -> {
                log += "dloi=SKIPPED_SOFT_FAIL:no_exact_match:${safeReasonText(dLoIResult.reason)}"
                log += "dloi_route=SKIPPED_SOFT_FAIL:no_exact_match; no_cosine_rag"
            }
        }

        val lakehouse = System.getenv("ATROPOS_LAKEHOUSE_URL")
        if (lakehouse.isNullOrBlank()) {
            log += "lakehouse=SKIPPED_SOFT_FAIL:ATROPOS_LAKEHOUSE_URL unset"
            log += "lakehouse_route=SKIPPED_SOFT_FAIL:lakehouse_unavailable; dLoI_attempted=true"
        } else {
            fetchBounded(lakehouse).fold(
                onSuccess = { body ->
                    hashes += "url_sha256=${sha256(lakehouse)}"
                    hashes += "body_sha256=${sha256(body)}"
                    log += "lakehouse=PASS bytes=${body.toByteArray(StandardCharsets.UTF_8).size}"
                    log += "lakehouse_route=PASS address_only=true; no_cosine_rag=true"
                },
                onFailure = {
                    log += "lakehouse=SKIPPED_SOFT_FAIL:${safeReason(it)}"
                    log += "lakehouse_route=SKIPPED_SOFT_FAIL:lakehouse_request_failed; no_cosine_rag"
                }
            )
        }

        val boundedUrl = System.getenv("ATROPOS_FACTORY_RESEARCH_URL")
        if (boundedUrl.isNullOrBlank()) {
            log += "bounded_fetch=SKIPPED_SOFT_FAIL:ATROPOS_FACTORY_RESEARCH_URL unset"
        } else {
            fetchBounded(boundedUrl).fold(
                onSuccess = {
                    hashes += "url_sha256=${sha256(boundedUrl)}"
                    hashes += "body_sha256=${sha256(it)}"
                    log += "bounded_fetch=PASS bytes=${it.length}"
                },
                onFailure = { log += "bounded_fetch=SKIPPED_SOFT_FAIL:${safeReason(it)}" }
            )
        }
        val specGraphRoot = System.getenv("SPECGRAPH_ROOT")
        val specGraphStatus = if (!specGraphRoot.isNullOrBlank() && java.nio.file.Files.exists(Path.of(specGraphRoot))) {
            "AVAILABLE_SOFT_SKIP:adapter_not_bound; internal DAG fallback required"
        } else {
            "SKIPPED_SOFT_FAIL:SpecGraph unavailable; internal DAG fallback required"
        }
        val beforeSuggestions = FactoryResearchReport(
            channelLog = log.toList(),
            memoryPointers = pointers.toList(),
            fetchHashes = hashes.toList(),
            specGraphStatus = specGraphStatus
        )
        // The legacy flag is retained for source compatibility, but confidence
        // remains the sole authority for provider suggestions. Callers cannot
        // bypass the ordered local/DLOI/lakehouse/fetch research stack.
        val shouldSuggest = providerSuggestionsPredicate?.invoke(beforeSuggestions) == true
        if (shouldSuggest) {
            log += "provider_suggestions=SKIPPED_SOFT_FAIL:provider_not_configured; attempted_after_channels=true; prompt_spans=$promptSpans"
        } else {
            log += "provider_suggestions=SKIPPED_SOFT_FAIL:confidence_threshold_met"
        }

        return FactoryResearchReport(log, pointers, hashes, specGraphStatus)
    }

    fun researchOpenAtoms(
        atomIds: List<String>,
        promptFingerprint: String,
        promptSpans: String = "none"
    ): List<String> = atomIds.map {
        "atom=$it prompt_fingerprint=$promptFingerprint prompt_spans=$promptSpans research=bounded_channels_attempted"
    }

    private fun fetchBounded(rawUrl: String): Result<String> = runCatching {
        val uri = URI(rawUrl)
        require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
            "bounded research permits HTTP(S) only"
        }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.instanceFollowRedirects = false
        connection.inputStream.use { input ->
            val bytes = input.readNBytes(maxFetchBytes + 1)
            require(bytes.size <= maxFetchBytes) { "bounded research response exceeded ${maxFetchBytes} bytes" }
            String(bytes, StandardCharsets.UTF_8)
        }
    }

    private fun safeReason(failure: Throwable): String =
        failure.javaClass.simpleName.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(80)

    private fun safeReasonText(reason: String): String =
        reason.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(80)

    private fun MemorySearchHit.isInFactoryScope(projectId: String, root: Path, operatorId: String): Boolean {
        val tags = record.tags.map { it.lowercase() }.toSet()
        val repository = root.fileName?.toString().orEmpty()
        val userMatch = record.body.contains("operator_id=$operatorId") || tags.contains("operator-${operatorId.lowercase()}")
        val projectMatch = tags.contains(projectId.lowercase()) || record.body.contains("project_id=$projectId")
        val repositoryMatch = record.body.contains("repository=$repository")
        return tags.contains("factory") && userMatch && (projectMatch || repositoryMatch)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
