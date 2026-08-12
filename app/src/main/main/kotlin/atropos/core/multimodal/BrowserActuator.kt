package atropos.core.multimodal

import atropos.core.AtroposRepoRootLocator
import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.PolicyActionClass
import java.nio.file.Path

class BrowserActuator(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val snapshotService: SnapshotService = SnapshotService(SnapshotStore(repoRoot), repoRoot),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))
) {
    fun capture(request: BrowserEvidenceRequest): BrowserEvidenceResult {
        val policy = agencyGate.evaluate(
            ActionProposal(
                id = "browser-${request.actorId}",
                actionClass = PolicyActionClass.NETWORK,
                actor = ActionActor.SystemService("browser-actuator"),
                networkTarget = request.url,
                metadata = mapOf("timeoutMillis" to request.timeoutMillis.toString())
            )
        )
        if (policy.disposition != AgencyDisposition.ALLOWED) {
            return BrowserEvidenceResult(
                status = BrowserEvidenceStatus.POLICY_BLOCKED,
                message = "refused: ${policy.reason.ifBlank { "not auto-allowed by policy" }}"
            )
        }

        return BrowserEvidenceResult(
            status = BrowserEvidenceStatus.UNSUPPORTED,
            message = "UNSUPPORTED: browser actuator has no installed local browser engine"
        )
    }

    fun captureStaticHtml(label: String, html: String, expectedText: String? = null): BrowserEvidenceResult {
        if (html.isBlank()) {
            return BrowserEvidenceResult(
                status = BrowserEvidenceStatus.FAILED,
                message = "static preview evidence refused: blank html"
            )
        }
        val snapshot = snapshotService.captureViewport(
            ViewportCapture(
                width = 1280,
                height = 720,
                content = html
            ),
            source = "static-preview:$label"
        )
        val errorState = html.contains("error", ignoreCase = true) ||
            html.contains("exception", ignoreCase = true) ||
            html.contains("stacktrace", ignoreCase = true)
        val inspection = when {
            errorState -> MultimodalInspection(
                kind = InspectionKind.STATE_VERIFICATION,
                severity = InspectionSeverity.CRITICAL,
                sourceSnapshotId = snapshot.id,
                findings = listOf("static preview contains visible error state"),
                matchScore = 0.0,
                passed = false
            )
            expectedText != null -> {
                val matched = html.contains(expectedText, ignoreCase = true)
                MultimodalInspection(
                    kind = InspectionKind.LAYOUT_CONFORMANCE,
                    severity = if (matched) InspectionSeverity.INFO else InspectionSeverity.WARNING,
                    sourceSnapshotId = snapshot.id,
                    findings = if (matched) {
                        listOf("static preview contains expected text: $expectedText")
                    } else {
                        listOf("static preview missing expected text: $expectedText")
                    },
                    matchScore = if (matched) 1.0 else 0.0,
                    passed = matched
                )
            }
            else -> null
        }
        return BrowserEvidenceResult(
            status = BrowserEvidenceStatus.CAPTURED,
            snapshot = snapshot,
            inspection = inspection,
            message = "static preview evidence captured"
        )
    }
}
