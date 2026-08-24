package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.integration.McpHostManager
import atropos.core.integration.MarkItDownIngestService
import atropos.core.dag.DocumentIngestionService

class McpCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val manager: McpHostManager = McpHostManager(
        AtroposRepoRootLocator.resolve(),
        localOnly = AtroposConfig.load().runtime.localOnly
    )
) {
    fun execute(tokens: List<String>) {
        when (tokens.getOrNull(1)?.lowercase()) {
            null, "list" -> uiEngine.renderBlock(manager.statuses().map { "${it.server.name} health=${it.health.name.lowercase()} ${it.reason}" })
            "test" -> uiEngine.renderBlock(manager.statuses().map { "${it.server.name} health=${it.health.name.lowercase()} ${it.reason}" })
            "call" -> call(tokens)
            "ingest" -> ingest(tokens)
            else -> uiEngine.renderError("usage: /mcp [list|test|call <server> <tool> [arguments-json]|ingest <path>]")
        }
    }

    private fun call(tokens: List<String>) {
        val server = tokens.getOrNull(2)
        val tool = tokens.getOrNull(3)
        if (server.isNullOrBlank() || tool.isNullOrBlank()) {
            uiEngine.renderError("usage: /mcp call <server> <tool> [arguments-json]")
            return
        }
        val arguments = tokens.drop(4).joinToString(" ").ifBlank { "{}" }
        runCatching { manager.callTool(server, tool, arguments) }
            .onSuccess { result ->
                uiEngine.renderNotice(
                    "MCP tool result server=$server tool=$tool evidence_sha256=${result.evidence.sha256}\n" +
                        result.response.take(8_000)
                )
            }
            .onFailure { uiEngine.renderError("MCP tool call refused: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun ingest(tokens: List<String>) {
        val source = tokens.getOrNull(2)
        if (source.isNullOrBlank()) {
            uiEngine.renderError("usage: /mcp ingest <repository-file>")
            return
        }
        val root = AtroposRepoRootLocator.resolve()
        runCatching {
            MarkItDownIngestService(
                repoRoot = root,
                ingest = DocumentIngestionService(repoRoot = root),
                call = { server, tool, args, paths -> manager.callTool(server, tool, args, territoryPaths = paths) }
            ).ingest(source)
        }.onSuccess { result ->
            uiEngine.renderNotice("MarkItDown ingested path=${result.markdownPath} sha256=${result.markdownSha256} evidence=${result.evidence.sha256} requirements=${result.requirements}")
        }.onFailure { failure -> uiEngine.renderError("MarkItDown ingest refused: ${failure.message ?: failure.javaClass.simpleName}") }
    }
}
