/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.PolicyActionClass
import atropos.core.policy.TypedToolExecutor
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import kotlin.streams.toList

internal class BridgeFilesHandler(
    private val repoRoot: Path = Path.of("").toAbsolutePath().normalize(),
    private val agency: TypedToolExecutor = TypedToolExecutor(
        BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))
    ),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val uploadsRoot = repoRoot.resolve(".atropos/uploads").normalize()

    fun upload(request: HttpRequest): HttpResponse {
        val session = request.query["session"].orEmpty().trim()
        if (session.isBlank() || !session.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            return HttpResponse.badRequest("Invalid or missing session id.", "POST /v1/files?session=<id>&filename=<name>")
        }
        val filename = request.query["filename"].orEmpty().trim()
        if (filename.isBlank() || !filename.matches(Regex("^[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9]+$"))) {
            return HttpResponse.badRequest("Invalid or missing filename (must be a portable identifier with extension).", "POST /v1/files?session=<id>&filename=<name>")
        }
        
        // Base64 decode body
        val bytes = try {
            val cleanBody = request.body.replace(Regex("\\s+"), "")
            Base64.getDecoder().decode(cleanBody)
        } catch (e: Exception) {
            return HttpResponse.badRequest("Request body must be valid Base64 encoded file contents.", "Encode files before POSTing.")
        }
        if (bytes.size > MAX_UPLOAD_BYTES) {
            return HttpResponse.refusal(
                413,
                "payload-too-large",
                "Uploaded file exceeds the ${MAX_UPLOAD_BYTES / 1024} KiB limit.",
                ""
            )
        }

        // Territory check and normalization
        val sessionDir = uploadsRoot.resolve(session).normalize()
        val targetPath = sessionDir.resolve(filename).normalize()

        // Verify bounds: targetPath must stay under uploadsRoot
        if (!targetPath.startsWith(uploadsRoot)) {
            return HttpResponse.refusal(403, "access-denied", "File write path escapes uploads boundary.", "")
        }

        // Prohibit symlinks in uploads root to target
        var current: Path? = targetPath
        while (current != null && current.startsWith(uploadsRoot)) {
            if (Files.isSymbolicLink(current)) {
                return HttpResponse.refusal(403, "access-denied", "Symbolic links are prohibited in upload path.", "")
            }
            current = current.parent
        }

        val relativeTarget = targetPath.relativeTo(repoRoot).toString()
        val execution = agency.execute(
            ActionProposal(
                id = "bridge-upload-${session}-${filename}",
                actionClass = PolicyActionClass.FILE_MUTATION,
                actor = ActionActor.HumanOwner,
                cwd = repoRoot.toString(),
                targetPaths = listOf(relativeTarget),
                metadata = mapOf("operation" to "attested_upload", "session" to session)
            )
        ) {
            Files.createDirectories(targetPath.parent)
            val temporary = Files.createTempFile(targetPath.parent, ".upload-", ".tmp")
            try {
                Files.write(temporary, bytes)
                try {
                    Files.move(temporary, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: Exception) {
                    Files.move(temporary, targetPath, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
            "written"
        }
        if (!execution.executed) {
            return HttpResponse.refusal(
                403,
                "policy-refused",
                redactionFilter.compact(
                    execution.refusalReason ?: "Upload refused by bounded agency policy."
                ),
                "Declare a valid repository-scoped upload target."
            )
        }

        // Calculate SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        val sha256 = hashBytes.joinToString("") { "%02x".format(it) }
        // The content hash identifies bytes; the envelope hash attests the
        // bridge-bound identity as well, so a client cannot detach those bytes
        // from their session and filename without changing the attestation.
        val envelopeSha256 = sha256("$session\n$filename\n$sha256\n${bytes.size}")

        return HttpResponse.json(
            JsonWriter.obj(
                "ok" to JsonWriter.bool(true),
                "filename" to JsonWriter.str(filename),
                "sha256" to JsonWriter.str(sha256),
                "attested" to JsonWriter.bool(true),
                "envelopeSha256" to JsonWriter.str(envelopeSha256),
                "size" to JsonWriter.num(bytes.size.toLong())
            )
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_UPLOAD_BYTES = 512 * 1024
    }

    fun list(request: HttpRequest): HttpResponse {
        val session = request.query["session"].orEmpty().trim()
        if (session.isBlank() || !session.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            return HttpResponse.badRequest("Invalid or missing session id.", "GET /v1/files?session=<id>")
        }

        val sessionDir = uploadsRoot.resolve(session).normalize()
        if (!sessionDir.startsWith(uploadsRoot)) {
            return HttpResponse.refusal(403, "access-denied", "Session path escapes uploads boundary.", "")
        }

        // Check symlinks
        var current: Path? = sessionDir
        while (current != null && current.startsWith(uploadsRoot)) {
            if (Files.isSymbolicLink(current)) {
                return HttpResponse.refusal(403, "access-denied", "Symbolic links are prohibited in path.", "")
            }
            current = current.parent
        }

        if (!Files.isDirectory(sessionDir)) {
            return HttpResponse.json(
                JsonWriter.obj(
                    "count" to JsonWriter.num(0),
                    "files" to JsonWriter.arr(emptyList())
                )
            )
        }

        val files = Files.list(sessionDir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { runCatching { Files.size(it) <= MAX_UPLOAD_BYTES }.getOrDefault(false) }
                .map { path ->
                    val bytes = Files.readAllBytes(path)
                    val digest = MessageDigest.getInstance("SHA-256")
                    val hashBytes = digest.digest(bytes)
                    val sha256 = hashBytes.joinToString("") { "%02x".format(it) }
                    JsonWriter.obj(
                        "filename" to JsonWriter.str(path.fileName.toString()),
                        "size" to JsonWriter.num(bytes.size.toLong()),
                        "sha256" to JsonWriter.str(sha256)
                    )
                }
                .toList()
        }

        return HttpResponse.json(
            JsonWriter.obj(
                "count" to JsonWriter.num(files.size.toLong()),
                "files" to JsonWriter.arr(files)
            )
        )
    }
}
