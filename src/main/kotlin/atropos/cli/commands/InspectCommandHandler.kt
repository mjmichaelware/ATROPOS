/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.multimodal.InspectionService
import atropos.core.multimodal.ViewportCapture
import atropos.cli.ui.design.AgentInspector
import atropos.cli.ui.design.PolicyInspector
import atropos.cli.ui.design.ProviderInspector
import atropos.cli.ui.design.RecoveryInspector
import atropos.cli.ui.design.RuntimeInspector
import atropos.cli.ui.design.SourceAuthorityInspector
import atropos.core.agent.GoalRunStore
import atropos.core.auth.AuthBootstrap
import atropos.core.provider.ProviderTruthService
import atropos.core.recovery.RestartCoordinator

/**
 * `/inspect` — Phase 17 drift inspection over files, the DAG, and viewports.
 *
 * Every inspection renders through [verdict] so PASS and FAIL read identically
 * across subcommands. Findings are always printed, including on a pass: an
 * inspection that passed with remarks is exactly the case where the remarks
 * matter, and a surface that hides them trains the operator to trust "PASS"
 * more than the evidence behind it.
 */
class InspectCommandHandler(
    private val inspectionService: InspectionService = InspectionService(),
    private val repoRoot: java.nio.file.Path = atropos.core.AtroposRepoRootLocator.resolve(),
    private val evidenceCollector: atropos.core.evidence.EvidenceCollector = atropos.core.evidence.EvidenceCollector(repoRoot),
    private val previewService: atropos.core.preview.LivePreviewService = atropos.core.preview.LivePreviewService(repoRoot)
) {
    private val goalRuns = GoalRunStore(repoRoot)
    private val providerTruth = ProviderTruthService()
    private val recovery = RestartCoordinator(repoRoot = repoRoot, goalRunStore = goalRuns)
    fun handle(args: List<String>): String = when (args.firstOrNull()) {
        "file" -> file(args)
        "dag" -> dag(args)
        "viewport" -> viewport(args)
        "full" -> "Full inspection: ${inspectionService.runFullInspection(args.drop(1)).summary}"
        "report" -> "Inspection report: ${inspectionService.report().summary}"
        "evidence" -> evidence(args)
        "preview" -> preview(args)
        "preview-patch" -> previewPatch(args)
        "runtime" -> runtime()
        "agent" -> agent()
        "provider" -> provider(args)
        "policy" -> policy(args)
        "authority" -> authority()
        "recovery" -> recovery()
        else -> recent()
    }

    private fun file(args: List<String>): String {
        if (args.size < 2) return "usage: /inspect file <path> [ref-snapshot-id]"
        val result = inspectionService.inspectFileForDrift(args[1], args.getOrNull(2))
        return "Inspection: ${verdict(result.id, result.passed, result.findings)}"
    }

    private fun dag(args: List<String>): String {
        val expected = args.getOrNull(1)?.toIntOrNull() ?: 0
        val result = inspectionService.verifyDAGState(expected)
        return "DAG inspection: ${verdict(result.id, result.passed, result.findings)}"
    }

    private fun viewport(args: List<String>): String {
        if (args.size < 3) return "usage: /inspect viewport <content> <expected-pattern>"
        val capture = ViewportCapture(content = args[1], width = DEFAULT_WIDTH, height = DEFAULT_HEIGHT)
        val result = inspectionService.verifyViewportContent(capture, args.drop(2).joinToString(" "))
        return "Viewport inspection: ${verdict(result.id, result.passed, result.findings)}"
    }

    private fun recent(): String {
        val inspections = inspectionService.recent(RECENT_LIMIT)
        if (inspections.isEmpty()) return "no inspections recorded"
        return inspections.joinToString("\n") {
            "  ${it.id}: ${it.kind.name} ${if (it.passed) "PASS" else "FAIL"} sev=${it.severity}"
        }
    }

    private fun evidence(args: List<String>): String {
        val subjectId = args.getOrNull(1)
        val runId = args.getOrNull(2)
        val bundle = evidenceCollector.collect(subjectId = subjectId, runId = runId)
        return "Collected evidence: ${bundle.summary}"
    }

    private fun preview(args: List<String>): String {
        if (args.size < 2) return "usage: /inspect preview <changed-file1> [changed-file2...]"
        val impacts = previewService.inspectUI(args.drop(1))
        return "UI components impacted: ${impacts.size} component(s)"
    }

    private fun previewPatch(args: List<String>): String {
        if (args.size < 2) return "usage: /inspect preview-patch <unified-diff>"
        val result = previewService.hotReload(args.drop(1).joinToString(" "))
        return "Preview patch: ${if (result.ok) "PASS" else "FAIL"}: ${result.message}"
    }

    private fun runtime(): String {
        val latest = inspectionService.recent(1).firstOrNull()
            ?: return "Runtime: no recorded inspection observation"
        return RuntimeInspector.inspectRuntime(latest)
    }

    private fun agent(): String {
        val run = goalRuns.latest() ?: return "Agent: no durable goal run recorded"
        return AgentInspector.inspectAgent(run.id, "${run.status.name.lowercase()} ${run.task.take(120)}")
    }

    private fun provider(args: List<String>): String {
        val snapshot = providerTruth.snapshot()
        val requested = args.getOrNull(1)?.takeIf { it.isNotBlank() } ?: snapshot.selectedProvider
        val record = snapshot.records.firstOrNull { it.id.equals(requested, ignoreCase = true) }
            ?: return "Provider: no descriptor for $requested"
        return ProviderInspector.inspectProvider(record, record.id == snapshot.selectedProvider)
    }

    private fun policy(args: List<String>): String {
        val proposalId = args.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: return "Policy: usage /inspect policy <proposal-id> <verdict>"
        val verdict = args.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "unknown"
        return PolicyInspector.inspectPolicy(proposalId, verdict) +
            " (proposal evidence must be supplied by the governance owner)"
    }

    private fun authority(): String = when (val result = AuthBootstrap(repoRoot).boot()) {
        is atropos.core.auth.AuthBootResult.Booted -> result.documents.joinToString("\n") {
            SourceAuthorityInspector.inspectAuthority(it.path, it.sha256)
        }.ifBlank { "SourceAuthority: no governing documents loaded" }
        is atropos.core.auth.AuthBootResult.Refused ->
            "SourceAuthority: REFUSED path=${result.cause.path} reason=${result.cause.reason}"
    }

    private fun recovery(): String {
        val snapshot = recovery.latestSnapshot()
            ?: return "Recovery: no persisted recovery snapshot"
        return RecoveryInspector.inspectRecovery(snapshot)
    }

    private fun verdict(id: String, passed: Boolean, findings: List<String>): String =
        "$id ${if (passed) "PASS" else "FAIL"}: ${findings.joinToString("; ")}"

    private companion object {
        const val DEFAULT_WIDTH = 80
        const val DEFAULT_HEIGHT = 24
        const val RECENT_LIMIT = 5
    }
}
