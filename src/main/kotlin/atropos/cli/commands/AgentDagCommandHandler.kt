package atropos.cli.commands

import atropos.bootstrap.BootstrapAcceptanceDag
import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposConfig
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagStore
import java.nio.file.Path

class AgentDagCommandHandler(
    private val ui: AnsiTerminalEngine,
    private val config: AtroposConfig,
    private val repoRoot: Path,
    private val dagService: DagExecutionService,
    private val dagStore: DagStore,
    private val invalid: (String) -> AgentCommandOutcome.Invalid
) {
    fun execute(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
                null, "list" -> {
                    val dags = dagService.listDags()
                    val text = dags.joinToString("\n") { "${it.id}: ${it.label} (${it.nodes.size} nodes)" }.ifEmpty { "no DAGs" }
                    ui.renderNotice(AgentCommandText.formatBlock("DAGS", text))
                    AgentCommandOutcome.Completed(text)
                }
                "create" -> create(args)
                "run" -> {
                    val dagId = args.getOrNull(1) ?: return invalid("usage: /agent dag run <dag-id>")
                    val result = dagService.evaluateDag(dagId)
                    ui.renderNotice(AgentCommandText.formatBlock("DAG RUN", result.message))
                    if (result.ok) AgentCommandOutcome.Completed(result.message) else AgentCommandOutcome.Invalid(result.message)
                }
                "show" -> {
                    val dagId = args.getOrNull(1) ?: return invalid("usage: /agent dag show <dag-id>")
                    val dag = dagService.readDag(dagId) ?: return invalid("DAG not found: $dagId")
                    ui.renderNotice(AgentCommandText.formatBlock("DAG", dag.render()))
                    AgentCommandOutcome.Completed(dag.render())
                }
                "status" -> status(args)
                "recover" -> {
                    val count = dagService.recoverStaleClaims()
                    ui.renderNotice(AgentCommandText.formatBlock("DAG RECOVER", "recovered $count stale claims"))
                    AgentCommandOutcome.Completed("recovered $count stale claims")
                }
                "node" -> {
                    val nodeId = args.getOrNull(1) ?: return invalid("usage: /agent dag node <node-id>")
                    val node = dagService.readNode(nodeId) ?: return invalid("node not found: $nodeId")
                    ui.renderNotice(AgentCommandText.formatBlock("DAG NODE", node.render()))
                    AgentCommandOutcome.Completed(node.render())
                }
                "delete" -> {
                    val dagId = args.getOrNull(1) ?: return invalid("usage: /agent dag delete <dag-id>")
                    dagStore.deleteDag(dagId)
                    ui.renderNotice(AgentCommandText.formatBlock("DAG DELETE", "deleted: $dagId"))
                    AgentCommandOutcome.Completed("deleted: $dagId")
                }
                "bootstrap" -> bootstrap()
                else -> invalid("usage: /agent dag [list|create|run|show|status|recover|node|delete|bootstrap]")
            }
    }

    private fun create(args: List<String>): AgentCommandOutcome {
        val label = args.getOrNull(1) ?: return invalid("usage: /agent dag create <label> [--node <id>,<dep1,dep2>,<action>]...")
        val nodes = mutableListOf<DagNode>()
        var idx = 2
        while (idx < args.size) {
            when (args[idx]) {
                "--node" -> {
                    val parts = args.getOrNull(idx + 1)?.split(",", limit = 3) ?: return invalid("invalid node spec")
                    val nodeId = parts.getOrElse(0) { "n${nodes.size + 1}" }
                    val deps = parts.getOrElse(1) { "" }.split("+").filter { it.isNotBlank() }
                    val action = runCatching { DagNodeAction.valueOf(parts.getOrElse(2) { "RUN_COMMAND" }.uppercase()) }
                        .getOrNull() ?: DagNodeAction.RUN_COMMAND
                    val now = java.time.Instant.now()
                    nodes.add(
                        DagNode(
                            id = nodeId,
                            label = nodeId,
                            dependencies = deps,
                            action = action,
                            actionPayload = null,
                            createdAt = now,
                            updatedAt = now,
                            metaFile = dagStore.dagDir().resolve("$nodeId.meta")
                        )
                    )
                    idx += 2
                }
                else -> break
            }
        }
        if (nodes.isEmpty()) return invalid("at least one --node required")
        val dag = dagService.createDag(label, nodes)
        ui.renderNotice(AgentCommandText.formatBlock("DAG CREATE", "${dag.id} with ${dag.nodes.size} nodes"))
        return AgentCommandOutcome.Completed("created: ${dag.id}")
    }

    private fun status(args: List<String>): AgentCommandOutcome {
        val dagId = args.getOrNull(1) ?: return invalid("usage: /agent dag status <dag-id>")
        val status = dagService.status(dagId) ?: return invalid("DAG not found: $dagId")
        val text = "${status.message}\ncompleted=${status.completedNodes} failed=${status.failedNodes} " +
            "blocked=${status.blockedNodes} pending=${status.pendingNodes} running=${status.runningNodes} ready=${status.readyNodes}"
        ui.renderNotice(AgentCommandText.formatBlock("DAG STATUS", text))
        return AgentCommandOutcome.Completed(status.message)
    }

    private fun bootstrap(): AgentCommandOutcome {
        ui.startSpinner("Running bootstrap acceptance DAG")
        return try {
            val result = BootstrapAcceptanceDag(config, repoRoot).createAndRun()
            val text = buildString {
                appendLine("Bootstrap acceptance: ${if (result.passed) "PASSED" else "FAILED"}")
                appendLine("nodes attempted: ${result.nodesAttempted}")
                appendLine("nodes passed: ${result.nodesPassed}")
                appendLine("nodes failed: ${result.nodesFailed}")
                if (result.details.isNotEmpty()) {
                    appendLine()
                    appendLine("details:")
                    result.details.forEach { appendLine("  $it") }
                }
            }.trimEnd()
            ui.renderNotice(AgentCommandText.formatBlock("BOOTSTRAP DAG", text))
            if (result.passed) AgentCommandOutcome.Completed(text) else AgentCommandOutcome.Invalid(text)
        } catch (e: Exception) {
            val message = "bootstrap DAG failed: ${e.message}"
            ui.renderError(message)
            AgentCommandOutcome.Invalid(message)
        } finally {
            ui.stopSpinner()
        }
    }
}
