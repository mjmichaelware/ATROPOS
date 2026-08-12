/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.bridge.queue.ConversationWorkRunner
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path

internal class BridgeEvidenceHandler(
    private val work: ConversationWorkRunner?,
    private val repoRoot: Path = Path.of("").toAbsolutePath().normalize(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
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
            return HttpResponse.badRequest("Evidence request needs an 'id'.", "GET /v1/evidence?id=<queue-id>")
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
                "Failed to read evidence file: ${e.message}",
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

        val redacted = redactionFilter.redact(rawContent)

        return HttpResponse.json(
            JsonWriter.obj(
                "ok" to JsonWriter.bool(true),
                "id" to JsonWriter.str(id),
                "path" to JsonWriter.str(evidencePathStr),
                "truncated" to JsonWriter.bool(isTruncated),
                "content" to JsonWriter.str(redacted)
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
