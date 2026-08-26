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
import atropos.core.territory.TerritoryAssignment
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * The bridge-owned workspace boundary for thin clients.
 *
 * It deliberately exposes a bounded viewer/editor, not a shell-shaped file
 * API. All paths are workspace-relative, symlink-free, and checked against
 * the durable territory assignments when assignments exist. With no explicit
 * assignment, the repository root is the operator's local workspace boundary.
 */
internal class BridgeWorkspaceHandler(
    private val repoRoot: Path,
    private val territory: TerritoryService = TerritoryService(TerritoryStore(repoRoot)),
    private val agency: TypedToolExecutor = TypedToolExecutor(
        BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))
    ),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun tree(request: HttpRequest): HttpResponse {
        val relative = request.query["path"].orEmpty().trim()
        val root = resolve(relative) ?: return outside(relative)
        if (!Files.isDirectory(root)) return HttpResponse.badRequest("Workspace tree path is not a directory.", "Use a directory-relative path.")
        val depth = request.query["depth"]?.toIntOrNull()?.coerceIn(0, MAX_TREE_DEPTH) ?: MAX_TREE_DEPTH
        val entries = mutableListOf<String>()
        walk(root, depth, entries)
        return HttpResponse.json(
            JsonWriter.obj(
                "ok" to JsonWriter.bool(true),
                "root" to JsonWriter.str(relative.ifBlank { "." }),
                "entries" to JsonWriter.arr(entries),
                "truncated" to JsonWriter.bool(entries.size >= MAX_TREE_ENTRIES)
            )
        )
    }

    fun file(request: HttpRequest): HttpResponse = when (request.method.uppercase()) {
        "GET" -> read(request)
        "PUT" -> write(request)
        else -> HttpResponse.methodNotAllowed(request.method, "/v1/workspace/file")
    }

    fun territory(request: HttpRequest): HttpResponse {
        val relative = request.query["path"].orEmpty().trim()
        val normalized = normalize(relative) ?: return outside(relative)
        val assignments = territory.getAll().filter { it.allows(normalized) }
        val explicit = territory.getAll().isNotEmpty()
        val allowed = if (explicit) assignments.isNotEmpty() else resolve(normalized) != null
        return HttpResponse.json(
            JsonWriter.obj(
                "ok" to JsonWriter.bool(true),
                "path" to JsonWriter.str(normalized),
                "surface" to JsonWriter.str(request.query["surface"].orEmpty().trim().ifBlank { "workspace" }),
                "withinWorkspace" to JsonWriter.bool(resolve(normalized) != null),
                "allowed" to JsonWriter.bool(allowed),
                "explicitAssignments" to JsonWriter.bool(explicit),
                "assignments" to JsonWriter.arr(assignments.map(::assignmentJson))
            )
        )
    }

    private fun read(request: HttpRequest): HttpResponse {
        val relative = request.query["path"].orEmpty().trim()
        val path = resolve(relative) ?: return outside(relative)
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_FILE_BYTES) {
            return HttpResponse.refusal(413, "file-unavailable", "File is missing, not regular, or exceeds the ${MAX_FILE_BYTES / 1024} KiB read limit.", "Choose a regular workspace file within the size limit.")
        }
        val content = redactionFilter.redact(Files.readString(path, StandardCharsets.UTF_8))
        return HttpResponse.json(JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "path" to JsonWriter.str(normalize(relative)!!),
            "content" to JsonWriter.str(content),
            "size" to JsonWriter.num(Files.size(path))
        ))
    }

    private fun write(request: HttpRequest): HttpResponse {
        val relative = request.query["path"].orEmpty().trim().ifBlank { field(request.body, "path") }
        val content = if (request.query["content"] != null) request.query["content"].orEmpty() else field(request.body, "content")
        val normalized = normalize(relative) ?: return outside(relative)
        val path = resolve(normalized) ?: return outside(relative)
        if (content.toByteArray(StandardCharsets.UTF_8).size > MAX_FILE_BYTES) {
            return HttpResponse.refusal(413, "payload-too-large", "Workspace file exceeds the ${MAX_FILE_BYTES / 1024} KiB limit.", "Send a smaller file.")
        }
        val assignments = territory.getAll()
        val matching = assignments.filter { it.allows(normalized) }
        if ((assignments.isNotEmpty() && matching.none { !it.readOnly }) || isProtected(normalized)) {
            return HttpResponse.refusal(403, "territory-denied", "Workspace file write is outside the writable territory.", "Request a writable territory for this relative path.")
        }
        if (hasSymlinkAncestor(path)) return HttpResponse.refusal(403, "symlink-denied", "Workspace file writes cannot cross symbolic links.", "Use a real workspace path.")
        val execution = agency.execute(ActionProposal(
            id = "bridge-workspace-write-${normalized.replace('/', '-')}",
            actionClass = PolicyActionClass.FILE_MUTATION,
            actor = ActionActor.HumanOwner,
            cwd = repoRoot.toString(),
            targetPaths = listOf(normalized),
            metadata = mapOf("operation" to "workspace_file_write")
        )) {
            Files.createDirectories(path.parent)
            val temporary = Files.createTempFile(path.parent, ".workspace-", ".tmp")
            try {
                Files.writeString(temporary, content, StandardCharsets.UTF_8)
                try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
                catch (_: Exception) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING) }
            } finally { Files.deleteIfExists(temporary) }
            "written"
        }
        if (!execution.executed) return HttpResponse.refusal(403, "policy-refused", "Workspace file write was refused by the bounded agency gate.", "Use a writable local workspace and an allowed path.")
        return HttpResponse.json(JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "path" to JsonWriter.str(normalized),
            "size" to JsonWriter.num(content.toByteArray(StandardCharsets.UTF_8).size.toLong())
        ))
    }

    private fun walk(path: Path, remainingDepth: Int, output: MutableList<String>) {
        if (output.size >= MAX_TREE_ENTRIES) return
        val children = runCatching { Files.list(path).use { it.sorted(compareBy<Path> { it.fileName.toString() }).toList() } }.getOrDefault(emptyList())
        for (child in children) {
            if (output.size >= MAX_TREE_ENTRIES || Files.isSymbolicLink(child) || child.fileName.toString() in setOf(".git", ".atropos")) continue
            val relative = repoRoot.relativize(child).toString().replace('\\', '/')
            output += JsonWriter.obj(
                "path" to JsonWriter.str(relative),
                "kind" to JsonWriter.str(if (Files.isDirectory(child)) "directory" else "file"),
                "size" to JsonWriter.num(if (Files.isRegularFile(child)) runCatching { Files.size(child) }.getOrDefault(0L) else 0L)
            )
            if (remainingDepth > 0 && Files.isDirectory(child)) walk(child, remainingDepth - 1, output)
        }
    }

    private fun resolve(raw: String): Path? {
        val normalized = normalize(raw) ?: return if (raw.isBlank()) repoRoot else null
        val path = repoRoot.resolve(normalized).normalize()
        return path.takeIf { it.startsWith(repoRoot) && !isProtected(normalized) }
    }

    private fun normalize(raw: String): String? {
        val value = raw.replace('\\', '/').trim().trimStart('/')
        if (value.isBlank()) return null
        val segments = value.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
        return segments.joinToString("/")
    }

    private fun hasSymlinkAncestor(path: Path): Boolean {
        var current: Path? = path
        while (current != null && current.startsWith(repoRoot)) {
            if (Files.isSymbolicLink(current)) return true
            current = current.parent
        }
        return false
    }

    private fun isProtected(path: String): Boolean = path == ".git" || path.startsWith(".git/") || path == ".atropos" || path.startsWith(".atropos/")

    private fun outside(path: String) = HttpResponse.refusal(403, "outside-territory", "Workspace path is outside the repository territory or contains traversal.", "Send a workspace-relative path without '..' or a leading slash.")

    private fun assignmentJson(assignment: TerritoryAssignment): String = JsonWriter.obj(
        "id" to JsonWriter.str(assignment.id),
        "ownerId" to JsonWriter.str(assignment.ownerId),
        "ownerRole" to JsonWriter.str(assignment.ownerRole),
        "allowedPrefix" to JsonWriter.str(assignment.allowedPrefix),
        "readOnly" to JsonWriter.bool(assignment.readOnly),
        "maxFileSizeBytes" to JsonWriter.num(assignment.maxFileSizeBytes)
    )

    private fun field(body: String, key: String): String = Regex("\\\"" + Regex.escape(key) + "\\\"\\s*:\\s*\\\"((?:[^\\\"\\\\]|\\\\.)*)\\\"")
        .find(body)?.groupValues?.getOrNull(1)
        ?.replace("\\\\n", "\n")?.replace("\\\\\"", "\"").orEmpty()

    private companion object {
        const val MAX_FILE_BYTES = 512 * 1024
        const val MAX_TREE_ENTRIES = 2_000
        const val MAX_TREE_DEPTH = 8
    }
}
