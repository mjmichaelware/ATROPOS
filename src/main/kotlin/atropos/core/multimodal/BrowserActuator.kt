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
                message = policy.reason
            )
        }

        return BrowserEvidenceResult(
            status = BrowserEvidenceStatus.UNSUPPORTED,
            message = "UNSUPPORTED: browser actuator has no installed local browser engine"
        )
    }

    fun captureStaticHtml(label: String, html: String, expectedText: String? = null): BrowserEvidenceResult {
        val snapshot = snapshotService.captureViewport(
            ViewportCapture(
                width = 1280,
                height = 720,
                content = html
            ),
            source = "static-preview:$label"
        )
        val inspection = expectedText?.let {
            MultimodalInspection(
                kind = InspectionKind.LAYOUT_CONFORMANCE,
                severity = if (html.contains(it, ignoreCase = true)) InspectionSeverity.INFO else InspectionSeverity.WARNING,
                sourceSnapshotId = snapshot.id,
                findings = if (html.contains(it, ignoreCase = true)) {
                    listOf("static preview contains expected text: $it")
                } else {
                    listOf("static preview missing expected text: $it")
                },
                matchScore = if (html.contains(it, ignoreCase = true)) 1.0 else 0.0,
                passed = html.contains(it, ignoreCase = true)
            )
        }
        return BrowserEvidenceResult(
            status = BrowserEvidenceStatus.CAPTURED,
            snapshot = snapshot,
            inspection = inspection,
            message = "static preview evidence captured"
        )
    }
}
