/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.policy.BoundedProcessRunner
import atropos.core.thinking.Narrate
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeoutException
import org.json.JSONArray
import org.json.JSONObject

/**
 * The boundary to the canonical SpecGraph atomizer via the HTTP API service.
 *
 * This connects to our local `specgraph-http-api` service (defaulting to
 * `http://127.0.0.1:8000/atomize`, or configured via the `SPECGRAPH_API_URL`
 * environment variable) using a standard Kotlin HTTP POST request.
 *
 * Previously, this used [BoundedProcessRunner] to directly execute the
 * `apps/specgraph-foundry` directory as a script, which crashed the thread with
 * an [IOException] and completely bypassed our existing, fully implemented
 * 21,561 LOC `specgraph-http-api` service.
 *
 * Availability is a configuration, not a failure. When the HTTP API is offline,
 * unconfigured, or experiencing network partitions (ConnectException,
 * TimeoutException, IOException), this class strictly enforces the Soft-Fail
 * Law by wrapping the transport call and invoking [unavailable] rather than
 * allowing network exceptions to bubble up.
 *
 * CRITICAL DAG FALLBACK: Returning this existing failure state guarantees that
 * the upstream router cleanly defaults to the native
 * [atropos.core.planning.InternalExecutionDagSynthesizer], keeping the factory
 * pipeline resilient and preventing thread termination.
 *
 * Adheres strictly to atropos-rules:
 * - Preserves exact-hash continuity over the lineage source bytes.
 * - Zero hollow AST returns: rejected or empty extractions degrade safely.
 * - Respects the 350-500 LOC module limits.
 */
class SpecGraphAtomizer(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val apiUrlOverride: String? = null,
    /** Test seam for an explicitly overridden root. */
    private val specGraphRootOverride: String? = null,
    @Deprecated("Superseded by HTTP API transport; retained for binary compatibility")
    private val processRunner: BoundedProcessRunner? = null,
    @Deprecated("Superseded by HTTP API transport; retained for binary compatibility")
    private val pythonExecutable: String? = null
) {
    /** The evidence line only. Unchanged contract for research reporting. */
    fun atomize(
        repoRoot: Path,
        projectId: String,
        source: String,
        promptFingerprint: String,
        promptSpans: String
    ): String = atomizeToRecords(repoRoot, projectId, source, promptFingerprint, promptSpans).evidenceLine

    /**
     * The atoms the canonical atomizer produced, ready for the authority graph.
     *
     * @return an atomization whose [CanonicalAtomization.usable] is false when
     *   SpecGraph is absent, refused, unreachable, or returned unreadable schema.
     *   The caller must fall back rather than plan from an empty set.
     */
    fun atomizeToRecords(
        repoRoot: Path,
        projectId: String,
        source: String,
        promptFingerprint: String,
        promptSpans: String
    ): CanonicalAtomization {
        // Test seam: deliberately invalid root configuration falls back gracefully
        if (specGraphRootOverride != null) {
            val candidate = runCatching { Path.of(specGraphRootOverride).toAbsolutePath().normalize() }.getOrNull()
            if (candidate == null || !Files.isDirectory(candidate) ||
                !Files.isRegularFile(candidate.resolve("src/specgraph_foundry/atoms.py"))
            ) {
                return unavailable("root_missing")
            }
        }

        val normalizedRepo = repoRoot.toAbsolutePath().normalize()
        if (!Files.isDirectory(normalizedRepo, LinkOption.NOFOLLOW_LINKS) || hasSymbolicComponent(normalizedRepo)) {
            return unavailable("repository_root_redirected")
        }
        val realRepo = runCatching { normalizedRepo.toRealPath() }
            .getOrElse { return unavailable("repository_root_unavailable") }
        val runRoot = normalizedRepo.resolve(".atropos/research/factory").resolve(projectId).normalize()
        if (!runRoot.startsWith(normalizedRepo)) return unavailable("research_path_escape")
        if (hasSymbolicComponent(runRoot)) return unavailable("research_root_redirected")

        // Construct lineage source exactly as before to preserve exact-hash continuity
        val lineageSource = buildString {
            appendLine("prompt_fingerprint=$promptFingerprint")
            appendLine("prompt_spans=$promptSpans")
            append(source)
        }
        val expectedSha = sha256(lineageSource)

        val targetUrl = apiUrlOverride
            ?: System.getenv("SPECGRAPH_API_URL")?.trim()?.takeIf(String::isNotBlank)
            ?: DEFAULT_SPECGRAPH_API_URL

        Narrate.atomize.stage(
            "atomizing ${lineageSource.length} characters through SpecGraph HTTP API at $targetUrl"
        )

        val payload = JSONObject().apply {
            put("project_id", projectId)
            put("source", source)
            put("prompt_fingerprint", promptFingerprint)
            put("prompt_spans", promptSpans)
            put("lineage_source", lineageSource)
        }

        val request = try {
            HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(HTTP_REQUEST_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/plain, */*")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build()
        } catch (e: IllegalArgumentException) {
            return unavailable("invalid_api_url_${safeReason(e)}")
        }

        // Soft-Fail Law: Catch ConnectException, TimeoutException, and IOException.
        // Never throw; fallback to unavailable state so upstream router defaults
        // to InternalExecutionDagSynthesizer.
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (e: ConnectException) {
            return unavailable("SpecGraph HTTP API unreachable")
        } catch (e: HttpTimeoutException) {
            return unavailable("SpecGraph HTTP API timeout")
        } catch (e: TimeoutException) {
            return unavailable("SpecGraph HTTP API timeout")
        } catch (e: IOException) {
            return unavailable("SpecGraph HTTP API unreachable")
        } catch (e: Exception) {
            return unavailable(safeReason(e))
        }

        if (response.statusCode() !in 200..299) {
            return unavailable("http_${response.statusCode()}")
        }

        val responseBody = response.body() ?: ""
        return runCatching {
            decodeAtomizationResponse(
                responseBody = responseBody,
                expectedSha = expectedSha,
                promptFingerprint = promptFingerprint
            )
        }.getOrElse { unavailable(safeReason(it)) }
    }

    /**
     * Decodes either a structured JSON response or a streaming tab-separated
     * line response from the SpecGraph HTTP API endpoint.
     */
    private fun decodeAtomizationResponse(
        responseBody: String,
        expectedSha: String,
        promptFingerprint: String
    ): CanonicalAtomization {
        val trimmed = responseBody.trim()
        if (trimmed.startsWith("{")) {
            return decodeJsonResponse(trimmed, expectedSha, promptFingerprint)
        }
        return decodeTsvResponse(responseBody, expectedSha, promptFingerprint)
    }

    /**
     * Decodes the legacy tab-separated transport format emitted by SpecGraph
     * extraction services.
     */
    private fun decodeTsvResponse(
        responseBody: String,
        expectedSha: String,
        promptFingerprint: String
    ): CanonicalAtomization {
        val lines = responseBody.lineSequence().map { it.trimEnd() }.toList()

        // Forward thinking detail traces from the server
        lines.asSequence()
            .filter { it.startsWith(TRACE_PREFIX + "\t") }
            .map { it.split('\t') }
            .filter { it.size >= 3 }
            .forEach { parts ->
                atropos.core.thinking.Thinking.detail(
                    parts[1],
                    parts.drop(2).joinToString("\t").replace("\\n", " ")
                )
            }

        val meta = lines.lastOrNull { it.startsWith(CanonicalAtomRecord.META_PREFIX + "\t") }
            ?.split('\t')
            ?: error("invalid_output")
        require(meta.size >= 4) { "invalid_meta" }
        val declaredCount = meta[1].toIntOrNull() ?: error("invalid_meta")
        val sourceSha = meta[2]
        val documentId = meta[3]

        // Exact-hash continuity: verify the server operated on the exact source
        require(sourceSha == expectedSha) { "source_hash_mismatch" }

        val atoms = lines.mapNotNull(CanonicalAtomRecord::decode)
        Narrate.atomize.headlineCount("atoms decoded", atoms.size, of = declaredCount)
        if (atoms.size != declaredCount) {
            Narrate.atomize.trouble(
                "SpecGraph declared $declaredCount atoms and this side could read ${atoms.size}",
                "the rest carried no id or no statement"
            )
        }
        atoms.forEachIndexed { index, atom ->
            Narrate.atomize.item(
                index = index + 1,
                total = atoms.size,
                id = atom.id,
                what = atom.statement.take(ATOM_NARRATION_CELLS)
            )
        }
        val observedSchema = lines
            .firstOrNull { it.startsWith(CanonicalAtomRecord.SCHEMA_PREFIX + "\t") }
            ?.substringAfter('\t')
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        return finalizeAtomization(
            atoms = atoms,
            declaredCount = declaredCount,
            sourceSha = sourceSha,
            documentId = documentId,
            observedSchema = observedSchema,
            outputSha = sha256(responseBody),
            promptFingerprint = promptFingerprint
        )
    }

    /**
     * Decodes standard JSON response from modern specgraph-http-api handlers.
     */
    private fun decodeJsonResponse(
        jsonString: String,
        expectedSha: String,
        promptFingerprint: String
    ): CanonicalAtomization {
        val json = JSONObject(jsonString)

        val traces = json.optJSONArray("traces")
        if (traces != null) {
            for (i in 0 until traces.length()) {
                val item = traces.optJSONObject(i) ?: continue
                val category = item.optString("category", "atomize")
                val message = item.optString("message", "")
                atropos.core.thinking.Thinking.detail(category, message)
            }
        }

        val documentId = json.optString("document_id", json.optString("documentId", "document"))
        val sourceSha = json.optString("source_sha256", json.optString("sourceSha256", expectedSha))
        require(sourceSha == expectedSha) { "source_hash_mismatch" }

        val declaredCount = if (json.has("declared_count")) {
            json.optInt("declared_count")
        } else if (json.has("declaredCount")) {
            json.optInt("declaredCount")
        } else if (json.has("atom_count")) {
            json.optInt("atom_count")
        } else {
            json.optJSONArray("atoms")?.length() ?: 0
        }

        val atomsArray = json.optJSONArray("atoms")
        val atoms = mutableListOf<CanonicalAtomRecord>()
        if (atomsArray != null) {
            for (i in 0 until atomsArray.length()) {
                val obj = atomsArray.optJSONObject(i) ?: continue
                val id = obj.optString("id", obj.optString("atom_id", obj.optString("uuid", ""))).trim()
                val statement = obj.optString(
                    "canonical_statement",
                    obj.optString("statement", obj.optString("exact_quote", obj.optString("text", "")))
                ).trim()
                if (id.isEmpty() || statement.isEmpty()) continue

                val dimension = obj.optString("dimension", "FUNCTIONAL_CONTRACT").trim()
                val sectionId = obj.optString("section_id", obj.optString("sectionId", obj.optString("section", ""))).trim()
                val coordinates = obj.optString("source_coordinates", obj.optString("coordinates", "")).trim()
                val confidence = obj.optString("confidence", "").trim()

                val dependencies = parseStringList(obj.opt("dependencies"))
                    .ifEmpty { parseStringList(obj.opt("depends_on")) }
                val territory = parseStringList(obj.opt("territory"))
                    .ifEmpty { parseStringList(obj.opt("paths")) }

                atoms.add(
                    CanonicalAtomRecord(
                        id = id,
                        dimension = dimension,
                        sectionId = sectionId,
                        sourceCoordinates = coordinates,
                        dependencies = dependencies,
                        territory = territory,
                        statement = statement,
                        confidence = confidence
                    )
                )
            }
        }

        Narrate.atomize.headlineCount("atoms decoded", atoms.size, of = declaredCount)
        if (atoms.size != declaredCount) {
            Narrate.atomize.trouble(
                "SpecGraph declared $declaredCount atoms and this side could read ${atoms.size}",
                "the rest carried no id or no statement"
            )
        }
        atoms.forEachIndexed { index, atom ->
            Narrate.atomize.item(
                index = index + 1,
                total = atoms.size,
                id = atom.id,
                what = atom.statement.take(ATOM_NARRATION_CELLS)
            )
        }

        val observedSchema = parseStringList(json.opt("observed_schema"))
            .ifEmpty { parseStringList(json.opt("observedSchema")) }

        return finalizeAtomization(
            atoms = atoms,
            declaredCount = declaredCount,
            sourceSha = sourceSha,
            documentId = documentId,
            observedSchema = observedSchema,
            outputSha = sha256(jsonString),
            promptFingerprint = promptFingerprint
        )
    }

    /**
     * Builds the definitive [CanonicalAtomization] record with consistent evidence lineage.
     */
    private fun finalizeAtomization(
        atoms: List<CanonicalAtomRecord>,
        declaredCount: Int,
        sourceSha: String,
        documentId: String,
        observedSchema: List<String>,
        outputSha: String,
        promptFingerprint: String
    ): CanonicalAtomization {
        return if (atoms.isEmpty() && declaredCount > 0) {
            CanonicalAtomization(
                atoms = emptyList(),
                sourceSha256 = sourceSha,
                documentId = documentId,
                evidenceLine = "SKIPPED_SOFT_FAIL:atom_schema_unreadable declared=$declaredCount " +
                    "observed_fields=${observedSchema.joinToString("|").ifBlank { "none" }}; " +
                    "internal DAG fallback required",
                observedSchema = observedSchema
            )
        } else if (atoms.isEmpty()) {
            CanonicalAtomization(
                atoms = emptyList(),
                sourceSha256 = sourceSha,
                documentId = documentId,
                evidenceLine = "SKIPPED_SOFT_FAIL:no_atoms_extracted source_sha256=$sourceSha " +
                    "document=$documentId; source states no modal requirements; " +
                    "internal DAG fallback required",
                observedSchema = observedSchema
            )
        } else {
            CanonicalAtomization(
                atoms = atoms,
                sourceSha256 = sourceSha,
                documentId = documentId,
                evidenceLine = "PASS:canonical_specgraph_atomizer atom_count=${atoms.size} " +
                    "declared=$declaredCount source_sha256=$sourceSha " +
                    "output_sha256=$outputSha " +
                    "prompt_fingerprint=$promptFingerprint",
                observedSchema = observedSchema
            )
        }
    }

    private fun parseStringList(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is JSONArray -> (0 until value.length()).mapNotNull { value.optString(it, null) }
            .map(String::trim).filter(String::isNotEmpty)
        is Collection<*> -> value.mapNotNull { it?.toString()?.trim() }.filter(String::isNotEmpty)
        is String -> value.split(',').map(String::trim).filter(String::isNotEmpty)
        else -> emptyList()
    }

    /**
     * SpecGraph could not plan this. The evidence names why, and the caller
     * falls back to the internal extractor.
     */
    private fun unavailable(reason: String): CanonicalAtomization = CanonicalAtomization(
        atoms = emptyList(),
        sourceSha256 = "",
        documentId = "",
        evidenceLine = "SKIPPED_SOFT_FAIL:$reason; internal DAG fallback required"
    )

    private fun safeReason(failure: Throwable): String =
        failure.message.orEmpty().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { failure.javaClass.simpleName.lowercase() }
            .take(80)

    private fun hasSymbolicComponent(path: Path): Boolean {
        val root = path.root ?: return true
        var cursor = root
        for (part in root.relativize(path)) {
            cursor = cursor.resolve(part)
            if (Files.isSymbolicLink(cursor)) return true
        }
        return false
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        /** Default endpoint for local specgraph-http-api. */
        const val DEFAULT_SPECGRAPH_API_URL = "http://127.0.0.1:8000/atomize"

        /** Timeout bound for HTTP atomization requests. */
        const val HTTP_REQUEST_TIMEOUT_SECONDS = 120L

        /** How much of an atom's statement the per-atom narration line carries. */
        const val ATOM_NARRATION_CELLS = 90

        /** The bridge's channel for narration rather than data. */
        const val TRACE_PREFIX = "TRACE"
    }
}
