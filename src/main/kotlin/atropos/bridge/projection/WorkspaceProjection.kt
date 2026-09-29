/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.project.ProjectRegistry
import atropos.core.project.RepositoryBinding
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path

/**
 * Projects the workspace project tree and file access onto the wire.
 *
 * `F-WEB-004` requires the explorer tree from local project + optional GitHub tree via user token.
 * `F-WEB-005` requires editor tabs to read/write via bridge file API.
 *
 * This projection reads the existing ProjectRegistry and provides file tree/file content
 * endpoints. The workspace root is the repository root of the bound project.
 */
class WorkspaceProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    /**
     * Renders the project tree for the given repository binding.
     */
    fun renderTree(binding: RepositoryBinding): String {
        val repoRoot = Path.of(binding.repoRoot).normalize()
        if (!Files.isDirectory(repoRoot)) {
            return JsonWriter.obj(
                "ok" to JsonWriter.bool(false),
                "error" to JsonWriter.str("Repository root not found: $repoRoot"),
                "tree" to JsonWriter.arr()
            )
        }

        val tree = buildTree(repoRoot, repoRoot, maxDepth = 3)
        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "repoRoot" to JsonWriter.str(redactionFilter.redact(binding.repoRoot)),
            "tree" to JsonWriter.arr(tree.map { it.toJson() })
        )
    }

    /**
     * Reads a file from the workspace.
     */
    fun readFile(binding: RepositoryBinding, relativePath: String): String {
        val repoRoot = Path.of(binding.repoRoot).normalize()
        val filePath = repoRoot.resolve(relativePath).normalize()

        // Security: ensure the path is within the repo root
        if (!filePath.startsWith(repoRoot) || !Files.isRegularFile(filePath)) {
            return JsonWriter.obj(
                "ok" to JsonWriter.bool(false),
                "error" to JsonWriter.str("File not found or access denied: $relativePath")
            )
        }

        val content = Files.readString(filePath)
        val size = Files.size(filePath)
        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "path" to JsonWriter.str(relativePath),
            "content" to JsonWriter.str(content),
            "size" to JsonWriter.num(size)
        )
    }

    /**
     * Writes a file to the workspace.
     */
    fun writeFile(binding: RepositoryBinding, relativePath: String, content: String): String {
        val repoRoot = Path.of(binding.repoRoot).normalize()
        val filePath = repoRoot.resolve(relativePath).normalize()

        // Security: ensure the path is within the repo root
        if (!filePath.startsWith(repoRoot)) {
            return JsonWriter.obj(
                "ok" to JsonWriter.bool(false),
                "error" to JsonWriter.str("Access denied: $relativePath")
            )
        }

        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, content)
        val size = Files.size(filePath)
        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "path" to JsonWriter.str(relativePath),
            "size" to JsonWriter.num(size)
        )
    }

    private fun buildTree(root: Path, current: Path, maxDepth: Int): List<TreeNode> {
        if (maxDepth <= 0) return emptyList()
        if (!Files.isDirectory(current)) return emptyList()

        return Files.list(current)
            .filter { Files.isRegularFile(it) || Files.isDirectory(it) }
            .map { path ->
                val relative = root.relativize(path).toString
                val isDir = Files.isDirectory(path)
                val children = if (isDir) buildTree(root, path, maxDepth - 1) else emptyList()
                val size = if (isDir) 0L else runCatching { Files.size(path) }.getOrDefault(0L)
                TreeNode(relative, isDir, size, children)
            }
            .sortedBy { it.name }
            .toList()
    }

    data class TreeNode(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val children: List<TreeNode>
    ) {
        fun toJson(): String {
            return JsonWriter.obj(
                "name" to JsonWriter.str(name),
                "isDirectory" to JsonWriter.bool(isDirectory),
                "size" to JsonWriter.num(size),
                "children" to JsonWriter.arr(children.map { it.toJson() })
            )
        }
    }
}