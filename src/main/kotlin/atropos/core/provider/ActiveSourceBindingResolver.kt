package atropos.core.provider

import atropos.core.AtroposRepoRootLocator
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
                val path = uri.takeIf { it.isNotBlank() }?.let { Path.of(it) } ?: repoRoot
                ActiveSourceBindingSelection(SourceBinding.localPath(path))
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
                    ActiveSourceBindingSelection(SourceBinding.archive(Path.of(uri), sha))
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
}
