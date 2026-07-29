package atropos.core.multimodal

import atropos.core.AtroposRepoRootLocator
import atropos.core.dag.DagService
import atropos.core.dag.DAGNodeState
import atropos.core.director.DirectorService
import atropos.core.director.ObservationKind
import atropos.core.director.DriftSeverity
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class InspectionService(
    private val snapshotService: SnapshotService = SnapshotService(),
    private val dagService: DagService = DagService(),
    private val directorService: DirectorService? = null,
    private val repoRoot: Path = AtroposRepoRootLocator.resolve()
) {
    private val inspectionHistory = mutableListOf<MultimodalInspection>()

    fun inspectFileForDrift(filePath: String, referenceSnapshotId: String? = null): MultimodalInspection {
        val current = try {
            snapshotService.captureFile(filePath)
        } catch (e: IllegalArgumentException) {
            return failedInspection("FILE_NOT_FOUND", "file $filePath not found", filePath)
        }

        val refId = referenceSnapshotId ?: findPriorSnapshot(filePath)?.id

        return if (refId != null) {
            val result = snapshotService.compareSnapshots(current.id, refId)
            recordAndNotify(result)
            result
        } else {
            val finding = "no prior snapshot for $filePath; baseline captured as ${current.id}"
            val inspection = MultimodalInspection(
                kind = InspectionKind.DRIFT_DETECTION,
                severity = InspectionSeverity.INFO,
                sourceSnapshotId = current.id,
                findings = listOf(finding),
                passed = true
            )
            recordAndNotify(inspection)
            inspection
        }
    }

    fun inspectAllFiles(filePaths: List<String>): InspectionReport {
        val inspections = filePaths.map { inspectFileForDrift(it) }
        return InspectionReport(inspections = inspections)
    }

    fun verifyDAGState(expectedCompletions: Int): MultimodalInspection {
        val allNodes = dagService.getAllNodes()
        val completed = allNodes.count { it.state == DAGNodeState.COMPLETED }
        val failed = allNodes.count { it.state == DAGNodeState.FAILED }
        val pending = allNodes.count { it.state == DAGNodeState.PENDING || it.state == DAGNodeState.RUNNABLE }
        val passed = completed >= expectedCompletions
        val inspection = MultimodalInspection(
            kind = InspectionKind.STATE_VERIFICATION,
            severity = if (passed) InspectionSeverity.INFO else InspectionSeverity.WARNING,
            sourceSnapshotId = "dag-state",
            findings = listOf(
                "DAG: $completed completed, $failed failed, $pending pending ($expectedCompletions expected)"
            ),
            matchScore = if (allNodes.isEmpty()) 0.0 else completed.toDouble() / allNodes.size.toDouble(),
            passed = passed
        )
        recordAndNotify(inspection)
        return inspection
    }

    fun verifyViewportContent(viewport: ViewportCapture, expectedPattern: String): MultimodalInspection {
        val contains = viewport.content.contains(expectedPattern, ignoreCase = true)
        val ref = snapshotService.captureViewport(viewport)
        val inspection = MultimodalInspection(
            kind = InspectionKind.LAYOUT_CONFORMANCE,
            severity = if (contains) InspectionSeverity.INFO else InspectionSeverity.WARNING,
            sourceSnapshotId = ref.id,
            findings = if (contains) listOf("viewport contains expected pattern: $expectedPattern")
                else listOf("viewport does not contain expected pattern: $expectedPattern"),
            passed = contains
        )
        recordAndNotify(inspection)
        return inspection
    }

    fun runFullInspection(fileSnapshotPaths: List<String> = emptyList(), expectedDAGCompletions: Int = 0): InspectionReport {
        val inspections = mutableListOf<MultimodalInspection>()

        if (fileSnapshotPaths.isNotEmpty()) {
            inspections += inspectAllFiles(fileSnapshotPaths).inspections
        }
        if (expectedDAGCompletions > 0) {
            inspections += verifyDAGState(expectedDAGCompletions)
        }

        return InspectionReport(inspections = inspections)
    }

    fun report(): InspectionReport {
        return InspectionReport(inspections = inspectionHistory.toList())
    }

    fun recent(limit: Int = 20): List<MultimodalInspection> = inspectionHistory.takeLast(limit)

    private fun findPriorSnapshot(filePath: String): SnapshotReference? {
        return snapshotService.listSnapshots().lastOrNull { it.source == filePath && it.kind == SnapshotKind.FILE_SNAPSHOT }
    }

    private fun recordAndNotify(inspection: MultimodalInspection) {
        inspectionHistory += inspection
        if (!inspection.passed) {
            directorService?.observe(
                kind = ObservationKind.DIFF_DRIFT,
                severity = when (inspection.severity) {
                    InspectionSeverity.CRITICAL -> DriftSeverity.CRITICAL
                    InspectionSeverity.WARNING -> DriftSeverity.WARNING
                    else -> DriftSeverity.ADVISORY
                },
                source = "multimodal/inspection",
                details = inspection.findings.joinToString("; "),
                files = listOf(inspection.sourceSnapshotId)
            )
        }
    }

    private fun failedInspection(reason: String, detail: String, sourceId: String): MultimodalInspection {
        return MultimodalInspection(
            kind = InspectionKind.STATE_VERIFICATION,
            severity = InspectionSeverity.CRITICAL,
            sourceSnapshotId = sourceId,
            findings = listOf("$reason: $detail"),
            passed = false
        )
    }
}
