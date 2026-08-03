package atropos.core.factory

import atropos.core.memory.LocalMemoryStore
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
    private val timeoutMillis: Int = 750
) {
    fun collect(root: Path, prompt: String): FactoryResearchReport {
        val log = mutableListOf<String>()
        val pointers = mutableListOf<String>()
        val hashes = mutableListOf<String>()
        val query = prompt.trim().take(160)
        val localMemory = memory ?: LocalMemoryStore(root.resolve(".atropos/memory").toFile())

        runCatching { localMemory.search(query, limit = 4) }
            .onSuccess { hits ->
                log += "st_memory=PASS hits=${hits.size}"
                pointers += hits.map { it.record.id }
            }
            .onFailure { log += "st_memory=SKIPPED_SOFT_FAIL:${safeReason(it)}" }

        runCatching { localMemory.search(query, limit = 8) }
            .onSuccess { hits -> log += "lt_memory=PASS records=${hits.size}" }
            .onFailure { log += "lt_memory=SKIPPED_SOFT_FAIL:${safeReason(it)}" }

        val lakehouse = System.getenv("ATROPOS_LAKEHOUSE_URL")
        if (lakehouse.isNullOrBlank()) {
            log += "lakehouse=SKIPPED_SOFT_FAIL:ATROPOS_LAKEHOUSE_URL unset"
        } else {
            fetchBounded(lakehouse).fold(
                onSuccess = { body ->
                    hashes += sha256(body)
                    log += "lakehouse=PASS bytes=${body.toByteArray(StandardCharsets.UTF_8).size}"
                },
                onFailure = { log += "lakehouse=SKIPPED_SOFT_FAIL:${safeReason(it)}" }
            )
        }

        val boundedUrl = System.getenv("ATROPOS_FACTORY_RESEARCH_URL")
        if (boundedUrl.isNullOrBlank()) {
            log += "bounded_fetch=SKIPPED_SOFT_FAIL:ATROPOS_FACTORY_RESEARCH_URL unset"
        } else {
            fetchBounded(boundedUrl).fold(
                onSuccess = { body -> hashes += sha256(body); log += "bounded_fetch=PASS bytes=${body.length}" },
                onFailure = { log += "bounded_fetch=SKIPPED_SOFT_FAIL:${safeReason(it)}" }
            )
        }
        log += "provider_suggestions=SKIPPED_SOFT_FAIL:no provider proposal required for deterministic local scaffold"

        val specGraphRoot = System.getenv("SPECGRAPH_ROOT")
        val specGraphStatus = if (!specGraphRoot.isNullOrBlank() && java.nio.file.Files.exists(Path.of(specGraphRoot))) {
            "AVAILABLE_NOT_INVOKED:internal adapter boundary remains authoritative"
        } else {
            "SKIPPED_SOFT_FAIL:SpecGraph unavailable; internal DAG fallback required"
        }
        return FactoryResearchReport(log, pointers, hashes, specGraphStatus)
    }

    fun researchOpenAtoms(atomIds: List<String>, promptFingerprint: String): List<String> =
        atomIds.map { "atom=$it prompt_fingerprint=$promptFingerprint research=bounded_channels_attempted" }

    private fun fetchBounded(rawUrl: String): Result<String> = runCatching {
        val connection = URI(rawUrl).toURL().openConnection() as HttpURLConnection
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

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
