package atropos.core.agent

import atropos.core.dag.DagExecutionService
import atropos.core.recovery.RestartCoordinator
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class SelfHostEvidenceBundleExporter(
    private val repoRoot: Path,
    private val store: GoalRunStore,
    private val dagService: DagExecutionService,
    private val restartCoordinator: RestartCoordinator = RestartCoordinator(repoRoot),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val hasher: SelfHostFileHasher = SelfHostFileHasher()
) {
    private val bundleRoot = repoRoot.resolve(".atropos/self-hosting/evidence").normalize()

    fun export(goalId: String): SelfHostEvidenceBundleResult {
        val record = store.resolve(goalId)
            ?: return SelfHostEvidenceBundleResult(false, "goal not found: $goalId", null, null, null, null)
        val dag = record.dagId?.let { dagService.readDag(it) }
        val snapshot = restartCoordinator.latestSnapshot(record.id)
        val targetDir = bundleRoot.resolve(record.id)
        Files.createDirectories(targetDir)

        val markdownPath = targetDir.resolve("bundle.md")
        val jsonPath = targetDir.resolve("bundle.json")
        Files.writeString(markdownPath, renderMarkdown(record, dag, snapshot), StandardCharsets.UTF_8)
        Files.writeString(jsonPath, renderJson(record, dag, snapshot), StandardCharsets.UTF_8)

        return SelfHostEvidenceBundleResult(
            ok = true,
            message = "self-host evidence bundle exported",
            markdownPath = markdownPath,
            jsonPath = jsonPath,
            markdownSha256 = hasher.sha256(markdownPath),
            jsonSha256 = hasher.sha256(jsonPath)
        )
    }

    private fun renderMarkdown(
        record: GoalRunRecord,
        dag: atropos.core.dag.DagDefinition?,
        snapshot: atropos.core.recovery.StateSnapshot?
    ): String = buildString {
        appendLine("# ATROPOS Self-Host Evidence")
        appendLine()
        appendLine("- goal: `${escapeMarkdown(record.id)}`")
        appendLine("- task: `${escapeMarkdown(clean(record.task))}`")
        appendLine("- status: `${record.status}`")
        appendLine("- terminal: `${record.terminalCondition ?: "none"}`")
        appendLine("- phase: `${escapeMarkdown(record.activePhase ?: "none")}`")
        appendLine("- dag: `${escapeMarkdown(record.dagId ?: "none")}`")
        appendLine("- current node: `${escapeMarkdown(record.currentNodeId ?: "none")}`")
        appendLine("- baseline: `${escapeMarkdown(record.baselineCommit ?: "none")}`")
        appendLine("- dirty fingerprint: `${escapeMarkdown(record.dirtyStateFingerprint ?: "none")}`")
        appendLine()
        appendLine("## Territory")
        record.territory.forEach { appendLine("- `${escapeMarkdown(clean(it))}`") }
        if (record.territory.isEmpty()) appendLine("- `none`")
        appendLine()
        appendLine("## Evidence")
        record.evidence.forEachIndexed { index, evidence ->
            appendLine("${index + 1}. `${escapeMarkdown(clean(evidence))}`")
            appendLine("   - sha256: `${sha256Text(clean(evidence))}`")
        }
        if (record.evidence.isEmpty()) appendLine("No evidence recorded.")
        appendLine()
        appendLine("## DAG Nodes")
        dag?.nodes?.forEach { node ->
            appendLine("- `${escapeMarkdown(node.id)}` `${node.state}` `${escapeMarkdown(clean(node.label))}`")
            appendLine("  - action: `${node.action}`")
            appendLine("  - territory: `${escapeMarkdown(node.territory.joinToString(","))}`")
            appendLine("  - result: `${escapeMarkdown(clean(node.result ?: "none"))}`")
            appendLine("  - failure: `${escapeMarkdown(clean(node.failureReason ?: "none"))}`")
            node.expectedOutputs.forEach { output ->
                appendLine("  - output: `${escapeMarkdown(clean(output))}` sha256 `${outputHash(output)}`")
            }
        } ?: appendLine("No DAG loaded.")
        appendLine()
        appendLine("## Restart Snapshot")
        if (snapshot == null) {
            appendLine("No restart snapshot recorded.")
        } else {
            appendLine("- snapshot: `${escapeMarkdown(snapshot.id)}`")
            appendLine("- captured: `${snapshot.capturedAt}`")
            appendLine("- memory records: `${snapshot.memoryRecords}`")
            appendLine("- goals: `${snapshot.goalRuns.size}`")
            appendLine("- dags: `${snapshot.dags.size}`")
            appendLine("- nodes: `${snapshot.dagNodes.size}`")
            appendLine("- worktrees: `${snapshot.worktrees.size}`")
            snapshot.goalRuns.forEach { goal ->
                appendLine("- goal `${escapeMarkdown(goal.id)}` current `${escapeMarkdown(goal.currentNodeId ?: "none")}` evidence `${goal.evidenceCount}`")
                goal.territory.forEach { appendLine("  - territory: `${escapeMarkdown(clean(it))}`") }
                goal.evidenceHashes.forEach { appendLine("  - evidence sha256: `${it}`") }
            }
            snapshot.dagNodes.forEach { node ->
                appendLine("- node `${escapeMarkdown(node.nodeId)}` `${node.state}` `${node.action}`")
                appendLine("  - dag: `${escapeMarkdown(node.dagId)}`")
                appendLine("  - attempts: `${node.attempts}/${node.maxAttempts}`")
                node.territory.forEach { appendLine("  - territory: `${escapeMarkdown(clean(it))}`") }
                node.expectedOutputs.forEach { appendLine("  - expected output: `${escapeMarkdown(clean(it))}`") }
                node.resultHash?.let { appendLine("  - result sha256: `$it`") }
                node.failureHash?.let { appendLine("  - failure sha256: `$it`") }
                node.claimOwner?.let { appendLine("  - claim owner: `${escapeMarkdown(clean(it))}`") }
            }
        }
    }.let(redactionFilter::redact)

    private fun renderJson(
        record: GoalRunRecord,
        dag: atropos.core.dag.DagDefinition?,
        snapshot: atropos.core.recovery.StateSnapshot?
    ): String = buildString {
        appendLine("{")
        appendLine("  \"schema\": \"self-host-evidence-bundle-v1\",")
        appendLine("  \"redacted\": true,")
        appendLine("  \"hashAlgorithm\": \"SHA-256\",")
        appendLine("  \"goalId\": ${json(record.id)},")
        appendLine("  \"task\": ${json(clean(record.task))},")
        appendLine("  \"status\": ${json(record.status.name)},")
        appendLine("  \"terminal\": ${json(record.terminalCondition?.name ?: "none")},")
        appendLine("  \"phase\": ${json(record.activePhase ?: "none")},")
        appendLine("  \"dagId\": ${json(record.dagId ?: "none")},")
        appendLine("  \"currentNodeId\": ${json(record.currentNodeId ?: "none")},")
        appendLine("  \"baselineCommit\": ${json(record.baselineCommit ?: "none")},")
        appendLine("  \"dirtyStateFingerprint\": ${json(record.dirtyStateFingerprint ?: "none")},")
        appendLine("  \"territory\": [${record.territory.joinToString(",") { json(clean(it)) }}],")
        appendLine("  \"outputHashes\": [${renderOutputHashesJson(dag)}],")
        appendLine("  \"evidenceHashes\": [${record.evidence.joinToString(",") { json(sha256Text(clean(it))) }}],")
        appendLine("  \"evidence\": [${record.evidence.joinToString(",") { json(clean(it)) }}],")
        appendLine("  \"nodes\": [")
        val nodes = dag?.nodes.orEmpty()
        nodes.forEachIndexed { index, node ->
            append("    {")
            append("\"id\": ${json(node.id)}, ")
            append("\"state\": ${json(node.state.name)}, ")
            append("\"label\": ${json(clean(node.label))}, ")
            append("\"action\": ${json(node.action.name)}, ")
            append("\"territory\": [${node.territory.joinToString(",") { json(clean(it)) }}], ")
            append("\"result\": ${json(clean(node.result ?: "none"))}, ")
            append("\"failure\": ${json(clean(node.failureReason ?: "none"))}, ")
            append("\"outputs\": [")
            node.expectedOutputs.forEachIndexed { outputIndex, output ->
                append("{\"path\": ${json(clean(output))}, \"sha256\": ${json(outputHash(output))}}")
                if (outputIndex < node.expectedOutputs.lastIndex) append(", ")
            }
            append("]")
            append("}")
            if (index < nodes.lastIndex) append(",")
            appendLine()
        }
        appendLine("  ],")
        appendLine("  \"restartSnapshot\": ${renderSnapshotJson(snapshot)}")
        appendLine("}")
    }.let(redactionFilter::redact)

    private fun renderSnapshotJson(snapshot: atropos.core.recovery.StateSnapshot?): String {
        if (snapshot == null) return "null"
        return buildString {
            append("{")
            append("\"id\": ${json(snapshot.id)}, ")
            append("\"capturedAt\": ${json(snapshot.capturedAt.toString())}, ")
            append("\"memoryRecords\": ${snapshot.memoryRecords}, ")
            append("\"goalRuns\": ${snapshot.goalRuns.size}, ")
            append("\"dags\": ${snapshot.dags.size}, ")
            append("\"worktrees\": ${snapshot.worktrees.size}, ")
            append("\"goals\": [")
            snapshot.goalRuns.forEachIndexed { index, goal ->
                append("{")
                append("\"id\": ${json(goal.id)}, ")
                append("\"status\": ${json(goal.status)}, ")
                append("\"dagId\": ${json(goal.dagId ?: "none")}, ")
                append("\"currentNodeId\": ${json(goal.currentNodeId ?: "none")}, ")
                append("\"territory\": [${goal.territory.joinToString(",") { json(clean(it)) }}], ")
                append("\"evidenceHashes\": [${goal.evidenceHashes.joinToString(",") { json(it) }}]")
                append("}")
                if (index < snapshot.goalRuns.lastIndex) append(", ")
            }
            append("], ")
            append("\"nodes\": [")
            snapshot.dagNodes.forEachIndexed { index, node ->
                append("{")
                append("\"dagId\": ${json(node.dagId)}, ")
                append("\"nodeId\": ${json(node.nodeId)}, ")
                append("\"state\": ${json(node.state)}, ")
                append("\"action\": ${json(node.action)}, ")
                append("\"territory\": [${node.territory.joinToString(",") { json(clean(it)) }}], ")
                append("\"expectedOutputs\": [${node.expectedOutputs.joinToString(",") { json(clean(it)) }}], ")
                append("\"resultHash\": ${json(node.resultHash ?: "none")}, ")
                append("\"failureHash\": ${json(node.failureHash ?: "none")}, ")
                append("\"claimOwner\": ${json(clean(node.claimOwner ?: "none"))}, ")
                append("\"attempts\": ${node.attempts}, ")
                append("\"maxAttempts\": ${node.maxAttempts}")
                append("}")
                if (index < snapshot.dagNodes.lastIndex) append(", ")
            }
            append("]")
            append("}")
        }
    }

    private fun renderOutputHashesJson(dag: atropos.core.dag.DagDefinition?): String =
        dag?.nodes.orEmpty()
            .flatMap { node -> node.expectedOutputs }
            .distinct()
            .joinToString(",") { output ->
                "{\"path\": ${json(clean(output))}, \"sha256\": ${json(outputHash(output))}}"
            }

    private fun clean(value: String): String = redactionFilter.redact(value)

    private fun outputHash(path: String): String =
        hasher.sha256(repoRoot.resolve(path).normalize()) ?: "missing"

    private fun sha256Text(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun escapeMarkdown(value: String): String =
        value.replace("`", "'")

    private fun json(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
