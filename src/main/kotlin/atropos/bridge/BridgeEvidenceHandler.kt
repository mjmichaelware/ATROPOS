/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.bridge.queue.ConversationWorkRunner
import atropos.core.artifact.ArtifactHasher
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path

internal class BridgeEvidenceHandler(
    private val work: ConversationWorkRunner?,
    private val repoRoot: Path = Path.of("").toAbsolutePath().normalize(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    /**
     * B0-5: the evidence index, for a ledger browser.
     *
     * One entry per queue item that produced evidence, carrying the same
     * address the per-id read serves. Entries without evidence are omitted —
     * "no evidence" is their ordinary state, not an error — and the count says
     * how many were surveyed so absence of rows cannot masquerade as absence
     * of work.
     */
    fun listEvidence(limit: Int): HttpResponse {
        val runner = work ?: return HttpResponse.refusal(
            503,
            "queue-unwired",
            "No work queue is wired to this bridge.",
            "Start the engine with a queue to survey evidence."
        )
        val entries = runCatching { runner.list(limit.coerceIn(1, 100)) }
            .getOrElse { emptyList() }
        val withEvidence = entries.filter { !it.evidence.isNullOrBlank() }
        return HttpResponse.json(
            JsonWriter.obj(
                "ok" to JsonWriter.bool(true),
                "surveyed" to JsonWriter.num(entries.size.toLong()),
                "count" to JsonWriter.num(withEvidence.size.toLong()),
                "items" to JsonWriter.arr(
                    withEvidence.map { entry ->
                        JsonWriter.obj(
                            "id" to JsonWriter.str(entry.id),
                            "task" to JsonWriter.str(entry.task),
                            "evidence" to JsonWriter.str(entry.evidence.orEmpty()),
                            "state" to JsonWriter.str(entry.state),
                            "updatedAt" to JsonWriter.str(entry.updatedAt)
                        )
                    }
                )
            )
        )
    }

    fun getEvidence(request: HttpRequest): HttpResponse {
        if (work == null) {
            return HttpResponse.refusal(
                501,
                "queue-not-wired",
                "Work queue is not wired to the bridge.",
                ""
            )
        }
        val id = request.query["id"].orEmpty().trim()
        if (id.isBlank()) {
            return listEvidence(request)
        }

        val entry = work.find(id)
            ?: return HttpResponse.refusal(
                404,
                "queue-entry-unknown",
                "No queue entry matches '$id'.",
                ""
            )

        val evidencePathStr = entry.evidence
        if (evidencePathStr.isNullOrBlank()) {
            return HttpResponse.refusal(
                404,
                "evidence-missing",
                "Queue entry '$id' does not have an associated evidence file.",
                ""
            )
        }

        val targetPath = repoRoot.resolve(evidencePathStr).normalize()
        
        if (!isSafeToRead(targetPath)) {
            return HttpResponse.refusal(
                403,
                "access-denied",
                "Evidence file path escapes the repository boundary or contains symbolic links.",
                ""
            )
        }

        if (!Files.isRegularFile(targetPath)) {
            return HttpResponse.refusal(
                404,
                "evidence-file-missing",
                "The evidence file at '$evidencePathStr' could not be found.",
                ""
            )
        }

        val bytes = try {
            Files.readAllBytes(targetPath)
        } catch (e: Exception) {
            return HttpResponse.refusal(
                500,
                "read-error",
                "Failed to read evidence file: ${redactionFilter.compact(e.message ?: e.javaClass.simpleName)}",
                ""
            )
        }

        val limit = 100_000
        val isTruncated = bytes.size > limit
        val rawContent = if (isTruncated) {
            val truncatedStr = String(bytes, 0, limit, Charsets.UTF_8)
            "$truncatedStr\n\n[TRUNCATED: evidence file is larger than $limit bytes]"
        } else {
            String(bytes, Charsets.UTF_8)
        }

        val truncationMarker = "[TRUNCATED: evidence file is larger than $limit bytes]"
        val redacted = if (isTruncated) {
            val bounded = redactionFilter.redact(String(bytes, 0, limit, Charsets.UTF_8))
                .replace(Regex("<redacted:[^>]+>"), "[REDACTED]")
            "$bounded\n\n$truncationMarker"
        } else {
            redactionFilter.redact(rawContent)
                .replace(Regex("<redacted:[^>]+>"), "[REDACTED]")
        }

        // A bridge client receives a stable evidence address and digest, not a
        // terminal handle. This keeps terminals first-class and evidence-linkable
        // while preserving the no-PTY-over-the-bridge boundary.
        val contentHash = ArtifactHasher.sha256Bytes(redacted.toByteArray(Charsets.UTF_8))

        return HttpResponse.json(
            JsonWriter.obj(
                "ok" to JsonWriter.bool(true),
                "id" to JsonWriter.str(id),
                "path" to JsonWriter.str(redactionFilter.redact(evidencePathStr)),
                "truncated" to JsonWriter.bool(isTruncated),
                "content" to JsonWriter.str(redacted),
                "evidenceHash" to JsonWriter.str(contentHash),
                "evidenceLink" to JsonWriter.str("/v1/evidence?id=$id"),
                "terminal" to JsonWriter.str("bridge-evidence")
            )
        )
    }

    private fun listEvidence(request: HttpRequest): HttpResponse {
        val limit = request.query["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val offset = request.query["offset"]?.toIntOrNull()?.coerceIn(0, 1_000) ?: 0
        val entries = work?.list(limit, offset).orEmpty().filter { !it.evidence.isNullOrBlank() }
        return HttpResponse.json(
            JsonWriter.obj(
                "ok" to JsonWriter.bool(true),
                "limit" to JsonWriter.num(limit.toLong()),
                "offset" to JsonWriter.num(offset.toLong()),
                "count" to JsonWriter.num(entries.size.toLong()),
                "entries" to JsonWriter.arr(entries.map { entry ->
                    JsonWriter.obj(
                        "id" to JsonWriter.str(entry.id),
                        "state" to JsonWriter.str(entry.state),
                        "evidence" to JsonWriter.str(redactionFilter.redact(entry.evidence.orEmpty())),
                        "updatedAt" to JsonWriter.str(entry.updatedAt),
                        "evidenceLink" to JsonWriter.str("/v1/evidence?id=${entry.id}")
                    )
                })
            )
        )
    }

    private fun isSafeToRead(target: Path): Boolean {
        val normalizedTarget = target.toAbsolutePath().normalize()
        val normalizedRoot = repoRoot.toAbsolutePath().normalize()
        if (!normalizedTarget.startsWith(normalizedRoot)) return false

        var current: Path? = normalizedTarget
        while (current != null && current.startsWith(normalizedRoot) && current != normalizedRoot) {
            if (Files.isSymbolicLink(current)) return false
            current = current.parent
        }

        return try {
            normalizedTarget.toRealPath().startsWith(normalizedRoot.toRealPath())
        } catch (_: Exception) {
            false
        }
    }
}
