package atropos.core.multimodal

import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

enum class SnapshotKind { TERMINAL_BUFFER, SCREEN_CAPTURE, FILE_SNAPSHOT, COMPOSITE_VIEWPORT, COMPOSE_FRAME }

enum class InspectionKind { DRIFT_DETECTION, STATE_VERIFICATION, LAYOUT_CONFORMANCE, SCREENSHOT_COMPARE, SYMBOL_MATCH }

enum class InspectionSeverity { INFO, ADVISORY, WARNING, CRITICAL }

data class SnapshotReference(
    val id: String = "snap-${UUID.randomUUID().toString().take(12)}",
    val kind: SnapshotKind,
    val source: String,
    val contentHash: String,
    val byteSize: Int,
    val capturedAt: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        fun hash(content: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(content).joinToString("") { "%02x".format(it) }.take(16)
        }
    }
}

data class MultimodalInspection(
    val id: String = "inspect-${UUID.randomUUID().toString().take(12)}",
    val kind: InspectionKind,
    val severity: InspectionSeverity,
    val sourceSnapshotId: String,
    val referenceSnapshotId: String? = null,
    val findings: List<String>,
    val matchScore: Double = 0.0,
    val passed: Boolean = false,
    val inspectedAt: Instant = Instant.now()
)

data class InspectionReport(
    val inspections: List<MultimodalInspection>,
    val timestamp: Instant = Instant.now()
) {
    val passed: Boolean get() = inspections.all { it.passed }
    val criticalCount: Int get() = inspections.count { it.severity == InspectionSeverity.CRITICAL }
    val warningCount: Int get() = inspections.count { it.severity == InspectionSeverity.WARNING }

    val summary: String get() {
        val total = inspections.size
        val passedCount = inspections.count { it.passed }
        return "Multimodal: $passedCount/$total passed, $criticalCount critical, $warningCount warnings"
    }
}

data class ViewportCapture(
    val id: String = "vp-${UUID.randomUUID().toString().take(12)}",
    val width: Int,
    val height: Int,
    val content: String,
    val cursorLine: Int = 0,
    val cursorCol: Int = 0,
    val visibleLines: IntRange = 0..0,
    val capturedAt: Instant = Instant.now()
)

data class ComposeFrameCapture(
    val id: String = "cf-${UUID.randomUUID().toString().take(12)}",
    val componentTree: String,
    val focusComponent: String? = null,
    val layoutNodes: List<String> = emptyList(),
    val renderTimeMs: Long = 0,
    val capturedAt: Instant = Instant.now()
)

enum class BrowserEvidenceStatus {
    CAPTURED,
    POLICY_BLOCKED,
    UNSUPPORTED,
    FAILED
}

data class BrowserEvidenceRequest(
    val url: String,
    val expectedText: String? = null,
    val actorId: String = "browser-evidence",
    val timeoutMillis: Long = 10_000
)

data class BrowserEvidenceResult(
    val status: BrowserEvidenceStatus,
    val snapshot: SnapshotReference? = null,
    val inspection: MultimodalInspection? = null,
    val message: String
) {
    val ok: Boolean get() = status == BrowserEvidenceStatus.CAPTURED && inspection?.passed != false
}
