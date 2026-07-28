package atropos.core.agent

import atropos.ast.AstSymbolGraph
import atropos.ast.AstSymbolKind
import atropos.core.memory.LocalMemoryStore
import atropos.dloi.DloiLookupResult
import atropos.dloi.HigZeroGuard
import java.nio.file.Path

class AgentRunSourceResolver(
    private val repoRoot: Path,
    private val higZeroGuard: HigZeroGuard,
    private val astSymbolGraph: AstSymbolGraph,
    private val memoryStore: LocalMemoryStore
) {
    fun resolveSourceEvidence(task: String): SourceEvidence =
        when (val result = higZeroGuard.resolveTask(task)) {
            is DloiLookupResult.Resolved -> {
                val provenance = result.resolution.provenance
                memoryStore.rememberSourceDecision(
                    subjectId = provenance,
                    title = "agent source resolution",
                    body = "task=${task.trim()}\nprovenance=$provenance",
                    tags = listOf("agent", "source", "dloi")
                )
                SourceEvidence.Resolved(provenance)
            }

            is DloiLookupResult.NoMatch -> {
                memoryStore.rememberSourceDecision(
                    subjectId = "unresolved",
                    title = "agent source unresolved",
                    body = "task=${task.trim()}\nreason=${result.reason}",
                    tags = listOf("agent", "source", "dloi", "unresolved")
                )
                SourceEvidence.Unresolved(result.reason)
            }
        }

    fun impactedSymbolEvidence(changedFiles: List<String>): List<String> =
        runCatching {
            astSymbolGraph.impactedByPaths(changedFiles)
                .filter { it.kind != AstSymbolKind.FILE }
                .map { "${repoRoot.relativize(it.file)}:${it.qualifiedName}" }
                .distinct()
                .sorted()
                .take(20)
        }.getOrDefault(emptyList())
}
