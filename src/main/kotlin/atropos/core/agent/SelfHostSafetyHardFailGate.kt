package atropos.core.agent

import atropos.core.dag.DagNode
import atropos.core.security.RedactionFilter
import java.nio.file.Path

data class SelfHostSafetyFinding(
    val kind: String,
    val message: String
)

data class SelfHostSafetyReport(
    val passed: Boolean,
    val findings: List<SelfHostSafetyFinding>
) {
    fun evidenceLine(): String =
        if (passed) {
            "self_host_safety passed=true"
        } else {
            "self_host_safety passed=false findings=" +
                findings.joinToString(";") { "${it.kind}:${it.message}" }
        }
}

class SelfHostSafetyHardFailGate(
    private val repoRoot: Path,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun inspect(record: GoalRunRecord, node: DagNode): SelfHostSafetyReport {
        val findings = mutableListOf<SelfHostSafetyFinding>()
        findings += contextDrift(record)
        findings += secretLeak(record, node)
        findings += outOfTerritory(node)
        findings += selfVerification(node)
        findings += fakeSuccess(record, node)
        findings += policyBypass(record, node)
        return SelfHostSafetyReport(
            passed = findings.isEmpty(),
            findings = findings
        )
    }

    private fun contextDrift(record: GoalRunRecord): List<SelfHostSafetyFinding> {
        val evidence = record.evidence.joinToString("\n").lowercase()
        val markers = listOf(
            "context_preflight_failed",
            "cradle_verification_failed",
            "context attestation failed",
            "attestation mismatch",
            "identity mismatch",
            "context drift",
            "mythology",
            "mythological response"
        )
        if (markers.none { it in evidence }) return emptyList()
        return listOf(SelfHostSafetyFinding("context_drift", "prior context or identity failure recorded"))
    }

    private fun secretLeak(record: GoalRunRecord, node: DagNode): List<SelfHostSafetyFinding> {
        val combined = buildString {
            appendLine(record.task)
            record.evidence.forEach(::appendLine)
            appendLine(node.actionPayload.orEmpty())
            appendLine(node.result.orEmpty())
            appendLine(node.failureReason.orEmpty())
        }
        val report = redactionFilter.report(combined)
        if (!report.changed) return emptyList()
        return listOf(SelfHostSafetyFinding("secret_leak", "redactable secret material present in self-host state"))
    }

    private fun outOfTerritory(node: DagNode): List<SelfHostSafetyFinding> {
        if (node.territory.isEmpty()) {
            return listOf(SelfHostSafetyFinding("territory", "node declared no territory"))
        }
        val candidates = (node.expectedOutputs + mutationPayloadPath(node.actionPayload)).filter { it.isNotBlank() }
        val outOfBounds = candidates.filterNot { candidate ->
            val normalized = candidate.replace('\\', '/').trimStart('/')
            node.territory.any { territory ->
                normalized == territory.trimEnd('/') || normalized.startsWith(territory.trimEnd('/') + "/")
            }
        }
        if (outOfBounds.isEmpty()) return emptyList()
        return listOf(SelfHostSafetyFinding("territory", "out-of-territory path ${outOfBounds.joinToString(",")}"))
    }

    private fun selfVerification(node: DagNode): List<SelfHostSafetyFinding> {
        val lower = normalizedSafetyText(node.label + "\n" + node.actionPayload.orEmpty())
        val attempted = listOf(
            "self approve",
            "self approval",
            "self verif",
            "self verification",
            "approve own",
            "without verifiedcompletiongate",
            "bypass verifiedcompletiongate"
        ).any { it in lower }
        if (!attempted) return emptyList()
        return listOf(SelfHostSafetyFinding("self_verification", "node attempts to approve its own work"))
    }

    private fun fakeSuccess(record: GoalRunRecord, node: DagNode): List<SelfHostSafetyFinding> {
        val lower = normalizedSafetyText(safetyText(record, node))
        val attempted = listOf(
            "fake success",
            "constant true",
            "placeholder green",
            "empty success",
            "stub pass",
            "success without evidence",
            "success true without evidence",
            "placeholder verified"
        ).any { it in lower }
        if (!attempted) return emptyList()
        return listOf(SelfHostSafetyFinding("fake_success", "self-host state references fake or evidence-free success"))
    }

    private fun policyBypass(record: GoalRunRecord, node: DagNode): List<SelfHostSafetyFinding> {
        val lower = normalizedSafetyText(safetyText(record, node))
        val attempted = listOf(
            "policy bypass",
            "bypass policy",
            "bypass the policy",
            "without boundedagencygate",
            "without typedtoolexecutor",
            "raw provider prose execution",
            "execute raw provider prose"
        ).any { it in lower }
        if (!attempted) return emptyList()
        return listOf(SelfHostSafetyFinding("policy_bypass", "self-host state attempts to bypass bounded agency or typed execution"))
    }

    private fun safetyText(record: GoalRunRecord, node: DagNode): String =
        buildString {
            appendLine(record.task)
            record.evidence.forEach(::appendLine)
            appendLine(node.label)
            appendLine(node.actionPayload.orEmpty())
            appendLine(node.result.orEmpty())
            appendLine(node.failureReason.orEmpty())
        }.lowercase()

    private fun normalizedSafetyText(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun mutationPayloadPath(payload: String?): List<String> {
        val body = payload?.trim().orEmpty()
        if (!body.contains("::")) return emptyList()
        return listOf(body.substringBefore("::").trim())
    }
}
