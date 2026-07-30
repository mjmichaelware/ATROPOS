package atropos.core.dag

import atropos.core.planning.NodeResult
import java.nio.file.Files
import java.nio.file.Path

class DagNodeFileMutationExecutor(
    private val store: DagStore,
    private val finisher: DagNodeFinisher,
    private val normalizeCandidatePath: (String) -> Path?,
    private val territoryViolation: (DagNode, List<String>) -> String?
) {
    fun execute(node: DagNode, original: DagNode): DagNodeExecutionResult {
        val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
        try {
            if (original.actionPayload.isNullOrBlank()) {
                return finisher.fail(running, original, "no action payload")
            }

            val parsed = parse(original.actionPayload)
                ?: return finisher.fail(running, original, "unsupported file mutation payload")
            if (parsed.content.isBlank()) {
                return finisher.fail(running, original, "file mutation refused: empty content")
            }
            val territoryFailure = territoryViolation(original, listOf(parsed.path.toString()))
            if (territoryFailure != null) {
                return finisher.fail(running, original, territoryFailure)
            }
            parsed.path.parent?.let(Files::createDirectories)
            Files.writeString(parsed.path, parsed.content + "\n")
            finisher.complete(
                running,
                NodeResult(
                    nodeId = original.id,
                    success = true,
                    message = "file mutation applied",
                    finalState = DagNodeState.COMPLETE,
                    result = parsed.path.toString()
                ),
                relatedPaths = listOf(parsed.path.toString())
            )
            return DagNodeExecutionResult(original.id, DagNodeState.COMPLETE, true, "file mutation applied", parsed.path.toString())
        } catch (e: Exception) {
            return finisher.fail(running, original, e.message ?: "file mutation failed")
        }
    }

    private fun parse(payload: String): ParsedFileMutation? {
        val explicit = payload.split("::", limit = 2)
        if (explicit.size == 2 && explicit[0].isNotBlank()) {
            return ParsedFileMutation(normalizeCandidatePath(explicit[0].trim()) ?: return null, explicit[1].trim())
        }

        val naturalLanguage = Regex(
            """Write .*? to (?<path>(?:/tmp|src|docs|scripts|ops|\.atropos)[A-Za-z0-9_./-]+) containing exactly one line: (?<content>.+)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(payload)
        if (naturalLanguage != null) {
            val path = normalizeCandidatePath(naturalLanguage.groups["path"]?.value.orEmpty()) ?: return null
            val content = naturalLanguage.groups["content"]?.value?.trim().orEmpty()
            return ParsedFileMutation(path, content)
        }
        return null
    }

    private data class ParsedFileMutation(
        val path: Path,
        val content: String
    )
}
