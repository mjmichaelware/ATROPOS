package atropos.core.agent

import java.nio.file.Path

data class SelfHostMutation(
    val path: Path,
    val content: String
)

/** Parses the constrained path::content payload emitted by the self-host DAG. */
class SelfHostMutationPayloadParser {
    fun parse(payload: String?): SelfHostMutation? {
        val explicit = payload?.trim().orEmpty().split("::", limit = 2)
        if (explicit.size != 2 || explicit[0].isBlank()) return null
        val path = runCatching { Path.of(explicit[0].trim()) }.getOrNull() ?: return null
        if (path.isAbsolute || path.any { it.toString() == ".." }) return null
        val content = explicit[1].trim()
        if (content.isBlank()) return null
        return SelfHostMutation(path.normalize(), content)
    }
}
