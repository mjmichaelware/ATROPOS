package atropos.core.factory

import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemorySearchHit
import atropos.dloi.DloiDocument
import atropos.dloi.DloiLookupResult
import atropos.dloi.DloiService
import atropos.dloi.HigZeroGuard
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest

data class FactoryResearchReport(
    val channelLog: List<String>,
    val memoryPointers: List<String>,
    val fetchHashes: List<String>,
    val specGraphStatus: String,
    val promptFingerprint: String = ""
) {
    /**
     * Research is allowed to continue when optional channels are unavailable,
     * but the lifecycle must expose that degraded outcome instead of claiming
     * an unconditional success.
     */
    fun lifecycleState(): String = if (
        channelLog.any {
        it.contains("SKIPPED_SOFT_FAIL") &&
                !it.startsWith("provider_suggestions=SKIPPED_SOFT_FAIL:confidence_threshold_met")
        } || specGraphStatus.contains("SKIPPED_SOFT_FAIL", ignoreCase = true) ||
        specGraphStatus.contains("SOFT_SKIP", ignoreCase = true)
    ) {
        "COMPLETED_WITH_SOFT_FAILS"
    } else {
        "COMPLETED"
    }

    fun render(): String = buildString {
        appendLine("prompt_fingerprint=${promptFingerprint.ifBlank { "UNRECORDED" }}")
        appendLine("research_state=${lifecycleState()}")
        channelLog.forEach { appendLine(it) }
        if (memoryPointers.isNotEmpty()) appendLine("memory_pointers=${memoryPointers.joinToString(",")}")
        if (fetchHashes.isNotEmpty()) appendLine("fetch_hashes=${fetchHashes.joinToString(",")}")
        appendLine("specgraph=$specGraphStatus")
        if (specGraphStatus.startsWith("PASS:")) {
            appendLine("internal DAG fallback=NOT_REQUIRED: SpecGraph atomization succeeded")
        }
    }
}

/** Bounded, soft-failing research channels for factory requirements. */
class FactoryResearchService(
    private val memory: LocalMemoryStore? = null,
    private val maxFetchBytes: Int = 32 * 1024,
    private val timeoutMillis: Int = 750,
    private val maxFetchRequests: Int = 2,
    private val maxQueryParameters: Int = 8,
    private val dLoI: HigZeroGuard? = null,
    private val minimumScopedRelevance: Int = 2,
    private val operatorId: String? = System.getenv("ATROPOS_OPERATOR_ID")?.trim()?.takeIf { it.isNotBlank() },
    private val specGraphAtomizer: SpecGraphAtomizer = SpecGraphAtomizer()
) {
    init {
        require(maxFetchBytes > 0) { "bounded research byte limit must be positive" }
        require(timeoutMillis > 0) { "bounded research timeout must be positive" }
        require(maxFetchRequests > 0) { "bounded research request limit must be positive" }
        require(maxQueryParameters >= 0) { "bounded research query limit cannot be negative" }
        require(minimumScopedRelevance > 0) { "scoped memory relevance threshold must be positive" }
    }

    fun collect(
        root: Path,
        prompt: String,
        projectId: String = root.fileName?.toString().orEmpty(),
        providerSuggestionsRequired: Boolean = false,
        promptSpans: String = "none",
        promptArtifactMemoryStatus: String = "UNRECORDED",
        providerSuggestionsPredicate: ((FactoryResearchReport) -> Boolean)? = null,
        promptFingerprint: String = ""
    ): FactoryResearchReport {
        // Narrated, so every research channel reaches a watching operator as
        // it resolves rather than arriving as a block after the run decided.
        // Only the self-host runner streamed before this, which is why
        // /thinking 3 showed nothing at all during a factory run -- the engine
        // was working and saying so only to itself.
        val log = atropos.core.thinking.NarratedSteps()
        val pointers = mutableListOf<String>()
        val hashes = mutableListOf<String>()
        val fetcher = BoundedResearchFetcher(
            maxBytes = maxFetchBytes,
            timeoutMillis = timeoutMillis,
            maxRequests = maxFetchRequests,
            maxQueryParameters = maxQueryParameters
        )
        val query = prompt.trim().take(160)
        val atomizationSource = prompt.trim()
        log += "prompt_fingerprint=${promptFingerprint.ifBlank { "UNRECORDED" }}"
        log += "prompt_artifact_memory=$promptArtifactMemoryStatus"
        val localMemory = memory ?: runCatching {
            LocalMemoryStore(root.resolve(".atropos/memory").toFile())
        }.getOrElse { failure ->
            val reason = safeReason(failure)
            log += "st_memory=SKIPPED_SOFT_FAIL:initialization_$reason"
            log += "lt_memory=SKIPPED_SOFT_FAIL:initialization_$reason"
            null
        }

        // Short-term run memory is scoped to this factory project and
        // repository; it must remain available even when no operator identity
        // is configured so the prompt artifact can feed the same run.
        if (localMemory != null) {
            runCatching { localMemory.search(query, limit = 16) }
                .onSuccess { hits ->
                    val scoped = hits.filter {
                        it.score >= minimumScopedRelevance &&
                            it.isInFactoryScope(projectId, root, operatorId, requireOperatorScope = false)
                    }
                    log += "st_memory=PASS scoped_hits=${scoped.size} rejected=${hits.size - scoped.size}"
                    pointers += scoped.map { it.record.id }
                }
                .onFailure { log += "st_memory=SKIPPED_SOFT_FAIL:${safeReason(it)}" }

            runCatching { localMemory.search(query, limit = 24) }
                .onSuccess { hits ->
                    val scoped = hits.filter {
                        it.score >= minimumScopedRelevance &&
                            it.isInFactoryScope(projectId, root, operatorId, requireOperatorScope = true)
                    }
                    val userScope = if (operatorId == null) "user_scope_unset" else "user_scope_bound"
                    log += "lt_memory=PASS scoped_records=${scoped.size} rejected=${hits.size - scoped.size} $userScope"
                    pointers += scoped.map { "lt:${it.record.id}" }
                }
                .onFailure { log += "lt_memory=SKIPPED_SOFT_FAIL:${safeReason(it)}" }
        }

        var dLoIResult = runCatching {
            (dLoI ?: HigZeroGuard(DloiService(root))).resolveTask(query)
        }.getOrElse { failure ->
            DloiLookupResult.NoMatch(query, safeReason(failure))
        }
        if (dLoIResult is DloiLookupResult.NoMatch) {
            // Fallback: try keyword search if no exact DLOI match found (AUD003)
            val dloiService = DloiService(root)
            val docs = dloiService.loadDocuments(ensureIndex = false)
            val terms = query.lowercase()
                .split(Regex("[^a-z0-9._-]+"))
                .filter { it.length >= 3 }
                .distinct()
            val ranked = docs.map { document ->
                val haystack = buildString {
                    append(document.id).append(' ')
                    append(document.sourceId).append(' ')
                    append(document.originalFilename).append(' ')
                    document.sections.forEach { append(it.id).append(' ').append(it.title).append(' ') }
                }.lowercase()
                document to terms.count { it in haystack }
            }
            val matchedDoc = ranked.maxWithOrNull(compareBy<Pair<DloiDocument, Int>> { it.second }
                .thenBy { it.first.id })?.takeIf { it.second > 0 }?.first
            if (matchedDoc != null && matchedDoc.sections.isNotEmpty()) {
                val sec = matchedDoc.sections.maxByOrNull { section ->
                    val sectionText = "${section.id} ${section.title}".lowercase()
                    terms.count { it in sectionText }
                } ?: matchedDoc.sections.first()
                val resolution = runCatching { dloiService.lookup("${matchedDoc.sourceId}#${sec.id}@L${sec.lineStart}-${sec.lineEnd}") }.getOrNull()
                if (resolution != null) {
                    dLoIResult = DloiLookupResult.Resolved(resolution)
                }
            }
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
            // Append target path query to lakehouse base URL (AUD002)
            val requestUrl = "$lakehouse/retrieval?query=" + java.net.URLEncoder.encode(query, "UTF-8")
            fetcher.fetch(requestUrl).fold(
                onSuccess = { body ->
                    hashes += "url_sha256=${sha256(requestUrl)}"
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
            fetcher.fetch(boundedUrl).fold(
                onSuccess = {
                    hashes += "url_sha256=${sha256(boundedUrl)}"
                    hashes += "body_sha256=${sha256(it)}"
                    log += "bounded_fetch=PASS bytes=${it.toByteArray(StandardCharsets.UTF_8).size}"
                },
                onFailure = { log += "bounded_fetch=SKIPPED_SOFT_FAIL:${safeReason(it)}" }
            )
        }
        val specGraphStatus = specGraphAtomizer.atomize(
            repoRoot = root,
            projectId = projectId,
            source = atomizationSource,
            promptFingerprint = promptFingerprint,
            promptSpans = promptSpans
        )
        val beforeSuggestions = FactoryResearchReport(
            channelLog = log.toList(),
            memoryPointers = pointers.toList(),
            fetchHashes = hashes.toList(),
            specGraphStatus = specGraphStatus,
            promptFingerprint = promptFingerprint
        )
        
        // Compute dynamic confidence score based on actual soft-failure rate of research channels (AUD005)
        val channels = listOf("st_memory", "lt_memory", "dloi", "lakehouse", "bounded_fetch", "specgraph")
        val failures = log.count { line -> channels.any { line.startsWith("$it=SKIPPED_SOFT_FAIL") } }
        val confidence = ((channels.size - failures).toDouble() / channels.size * 100).toInt()
        log += "confidence=$confidence"

        // The legacy flag is retained for source compatibility, but confidence
        // remains the sole authority for provider suggestions. Callers cannot
        // bypass the ordered local/DLOI/lakehouse/fetch research stack.
        val suggestionDecision = runCatching {
            providerSuggestionsPredicate?.invoke(beforeSuggestions) == true
        }
        if (suggestionDecision.isFailure) {
            log += "provider_suggestions=SKIPPED_SOFT_FAIL:predicate_${safeReason(suggestionDecision.exceptionOrNull()!!)}; attempted_after_channels=true; prompt_spans=$promptSpans"
        } else if (suggestionDecision.getOrDefault(false)) {
            // The predicate answers "are provider suggestions *wanted*", which
            // it decides from confidence alone. This function never calls a
            // provider, so "wanted" cannot become "obtained" here — and the
            // line it used to write, `PASS suggestion_triggered=true`, said it
            // had. A low-confidence run with no provider configured reported a
            // provider suggestion that never happened, which is exactly the
            // claim §0.6 forbids. Wanted-but-not-obtained is a soft fail, and
            // it names which of the two it was.
            log += "provider_suggestions=SKIPPED_SOFT_FAIL:provider_not_configured; " +
                "wanted=true; attempted_after_channels=true; prompt_spans=$promptSpans"
        } else {
            log += "provider_suggestions=SKIPPED_SOFT_FAIL:confidence_threshold_met"
        }

        return FactoryResearchReport(log, pointers, hashes, specGraphStatus, promptFingerprint)
    }

    fun researchOpenAtoms(
        atomIds: List<String>,
        promptFingerprint: String,
        promptSpans: String = "none",
        researchDocumentSha256: String = ""
    ): List<String> {
        atropos.core.thinking.Narrate.research.counted("atoms to research", atomIds.size)
        return atomIds.mapIndexed { index, id ->
            atropos.core.thinking.Narrate.research.item(
                index = index + 1,
                total = atomIds.size,
                id = id,
                what = "bounded research channels attempted"
            )
            "atom=$id prompt_fingerprint=$promptFingerprint prompt_spans=$promptSpans " +
                "research_sha256=$researchDocumentSha256 research=bounded_channels_attempted"
        }
    }

    private fun safeReason(failure: Throwable): String =
        failure.javaClass.simpleName.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(80)

    private fun safeReasonText(reason: String): String =
        reason.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(80)

    private fun MemorySearchHit.isInFactoryScope(
        projectId: String,
        root: Path,
        operatorId: String?,
        requireOperatorScope: Boolean
    ): Boolean {
        val tags = record.tags.map { it.lowercase() }.toSet()
        val repository = root.fileName?.toString().orEmpty()
        val userMatch = if (requireOperatorScope && operatorId != null) {
            (
                record.body.contains("operator_id=$operatorId") ||
                tags.contains("operator-${operatorId.lowercase()}")
            )
        } else {
            operatorId == null ||
                record.body.contains("operator_id=$operatorId") ||
                tags.contains("operator-${operatorId.lowercase()}")
        }
        val projectMatch = tags.contains(projectId.lowercase()) || record.body.contains("project_id=$projectId")
        val repositoryMatch = record.body.contains("repository=$repository")
        // Long-term memory requires the operator plus a project or repository
        // binding. Short-term run memory may use the current project alone.
        val resourceMatch = projectMatch || (operatorId != null && repositoryMatch)
        return tags.contains("factory") && userMatch && resourceMatch
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
