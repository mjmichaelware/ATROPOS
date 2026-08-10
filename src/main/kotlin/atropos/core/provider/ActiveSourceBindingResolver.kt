package atropos.core.provider

import atropos.core.AtroposRepoRootLocator
import java.nio.file.InvalidPathException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

data class ActiveSourceBindingSelection(
    val binding: SourceBinding?,
    val refusalReason: String? = null
) {
    val accepted: Boolean get() = binding != null && refusalReason == null
}

class ActiveSourceBindingResolver(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val env: Map<String, String> = System.getenv()
) {
    fun resolve(): ActiveSourceBindingSelection {
        val rawKind = env["ATROPOS_SOURCE_BINDING_KIND"] ?: env["ATROPOS_SOURCE_BINDING"]
        val kind = rawKind?.trim()?.lowercase()?.replace('-', '_')
            ?: return ActiveSourceBindingSelection(SourceBinding.localPath(repoRoot))
        val uri = env["ATROPOS_SOURCE_BINDING_URI"]?.trim().orEmpty()
        val ref = env["ATROPOS_SOURCE_BINDING_REF"]?.trim()?.takeIf { it.isNotBlank() }
        val sha = env["ATROPOS_SOURCE_BINDING_SHA256"]?.trim()?.takeIf { it.isNotBlank() }

        return when (kind) {
            "local", "local_path" -> {
                val path = resolveFilePath(uri) ?: return invalidPath("local_path")
                refuseSymbolicPath(path, "local_path")
                    ?: ActiveSourceBindingSelection(SourceBinding.localPath(path))
            }
            "git" -> {
                if (uri.isBlank()) {
                    ActiveSourceBindingSelection(null, "git source binding requires ATROPOS_SOURCE_BINDING_URI")
                } else {
                    ActiveSourceBindingSelection(SourceBinding.git(uri, ref))
                }
            }
            "archive" -> {
                if (uri.isBlank()) {
                    ActiveSourceBindingSelection(null, "archive source binding requires ATROPOS_SOURCE_BINDING_URI")
                } else {
                    val path = resolveFilePath(uri) ?: return invalidPath("archive")
                    refuseSymbolicPath(path, "archive")
                        ?: ActiveSourceBindingSelection(SourceBinding.archive(path, sha))
                }
            }
            "http", "http_bundle" -> {
                if (uri.isBlank()) {
                    ActiveSourceBindingSelection(null, "http_bundle source binding requires ATROPOS_SOURCE_BINDING_URI")
                } else if (sha.isNullOrBlank()) {
                    ActiveSourceBindingSelection(null, "http_bundle source binding requires ATROPOS_SOURCE_BINDING_SHA256")
                } else {
                    ActiveSourceBindingSelection(SourceBinding.httpBundle(uri, sha))
                }
            }
            else -> ActiveSourceBindingSelection(null, "unsupported source binding kind: $rawKind")
        }
    }

    private fun resolveFilePath(raw: String): Path? = runCatching {
        if (raw.isBlank()) repoRoot
        else Path.of(raw).let { path ->
            if (path.isAbsolute) path.normalize() else repoRoot.resolve(path).normalize()
        }
    }.getOrNull()

    private fun refuseSymbolicPath(path: Path, label: String): ActiveSourceBindingSelection? {
        if (!hasSymbolicComponent(path)) return null
        return ActiveSourceBindingSelection(null, "$label source binding crosses a symbolic path component")
    }

    private fun invalidPath(label: String): ActiveSourceBindingSelection =
        ActiveSourceBindingSelection(null, "$label source binding contains an invalid path")

    private fun hasSymbolicComponent(path: Path): Boolean {
        var current: Path? = path.toAbsolutePath().normalize()
        while (current != null) {
            if (Files.isSymbolicLink(current)) return true
            current = current.parent
        }
        return false
    }
}
