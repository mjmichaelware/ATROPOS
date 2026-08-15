/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.EvidenceStore
import atropos.core.evaluation.EvidenceKind
import java.time.Instant

/**
 * 211–217 Lakehouse ledgers P20-LH01 … LH07 — evidence / memory / proposal / amendment as CAS objects with structural manifests
 */
interface LakehouseLedgerView<T> {
    fun store(value: T): String
    fun get(hash: String): T?
}

class EvidenceCasLedger(private val store: EvidenceStore = EvidenceStore()) : LakehouseLedgerView<String> {
    override fun store(value: String): String {
        return store.put(value, EvidenceKind.RAW)
    }

    override fun get(hash: String): String? {
        return store.get(hash)
    }
}

class ObservationCasLedger(private val store: EvidenceStore = EvidenceStore()) : LakehouseLedgerView<RuntimeObservation> {
    override fun store(value: RuntimeObservation): String {
        require(value.complete) { "Cannot store incomplete observation" }
        val manifest = buildString {
            appendLine("type=observation")
            appendLine("id=${value.id}")
            appendLine("timestamp=${value.timestamp}")
            appendLine("runtimeId=${value.runtimeId}")
            appendLine("projectId=${value.projectId}")
            value.goalId?.let { appendLine("goalId=$it") }
            value.nodeId?.let { appendLine("nodeId=$it") }
            appendLine("authorityFingerprint=${value.authorityFingerprint}")
            appendLine("environmentFingerprint=${value.environmentFingerprint}")
            value.exitCode?.let { appendLine("exitCode=$it") }
            appendLine("boundedOutput=${value.boundedOutput.replace("\n", "\\n")}")
            appendLine("artifactHashes=${value.artifactHashes.joinToString(",")}")
            appendLine("frequency=${value.frequency}")
            appendLine("severity=${value.severity.name}")
            value.invariantBroken?.let { appendLine("invariantBroken=$it") }
            value.requirementBlocked?.let { appendLine("requirementBlocked=$it") }
        }
        return store.put(manifest, EvidenceKind.EXECUTION_EVENT)
    }

    override fun get(hash: String): RuntimeObservation? {
        val raw = store.get(hash) ?: return null
        val lines = raw.lines().associate {
            val idx = it.indexOf('=')
            if (idx != -1) it.substring(0, idx) to it.substring(idx + 1) else "" to ""
        }
        if (lines["type"] != "observation") return null
        return RuntimeObservation(
            id = lines["id"] ?: "",
            timestamp = Instant.parse(lines["timestamp"] ?: return null),
            runtimeId = lines["runtimeId"] ?: "",
            projectId = lines["projectId"] ?: "",
            goalId = lines["goalId"],
            nodeId = lines["nodeId"],
            authorityFingerprint = lines["authorityFingerprint"] ?: "",
            environmentFingerprint = lines["environmentFingerprint"] ?: "",
            exitCode = lines["exitCode"]?.toIntOrNull(),
            boundedOutput = (lines["boundedOutput"] ?: "").replace("\\n", "\n"),
            artifactHashes = lines["artifactHashes"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
            frequency = lines["frequency"]?.toIntOrNull() ?: 1,
            severity = ObservationSeverity.valueOf(lines["severity"] ?: "INFO"),
            invariantBroken = lines["invariantBroken"],
            requirementBlocked = lines["requirementBlocked"]
        )
    }
}

class ProposalCasLedger(private val store: EvidenceStore = EvidenceStore()) : LakehouseLedgerView<ImprovementProposal> {
    override fun store(value: ImprovementProposal): String {
        require(value.isComplete()) { "Cannot store incomplete proposal" }
        val manifest = buildString {
            appendLine("type=proposal")
            appendLine("id=${value.id}")
            appendLine("proposedBy=${value.proposedBy}")
            appendLine("summary=${value.summary}")
            appendLine("necessity=${value.necessity.joinToString(",")}")
            appendLine("baseline=${value.baseline}")
            appendLine("target=${value.target}")
            appendLine("guardrails=${value.guardrails.joinToString(",")}")
            appendLine("territory=${value.territory.joinToString(",")}")
            appendLine("risk=${value.risk}")
            appendLine("rollback=${value.rollback}")
            appendLine("metricName=${value.metric.name}")
            appendLine("metricBaseline=${value.metric.baselineValue}")
            appendLine("metricTarget=${value.metric.targetValue}")
            appendLine("metricLowerIsBetter=${value.metric.lowerIsBetter}")
            appendLine("createdAt=${value.createdAt}")
            appendLine("state=${value.state.name}")
            appendLine("failureCount=${value.failureCount}")
        }
        return store.put(manifest, EvidenceKind.METRIC_SNAPSHOT)
    }

    override fun get(hash: String): ImprovementProposal? {
        val raw = store.get(hash) ?: return null
        val lines = raw.lines().associate {
            val idx = it.indexOf('=')
            if (idx != -1) it.substring(0, idx) to it.substring(idx + 1) else "" to ""
        }
        if (lines["type"] != "proposal") return null
        return ImprovementProposal(
            id = lines["id"] ?: "",
            proposedBy = lines["proposedBy"] ?: "",
            summary = lines["summary"] ?: "",
            necessity = lines["necessity"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
            baseline = lines["baseline"] ?: "",
            target = lines["target"] ?: "",
            guardrails = lines["guardrails"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
            territory = lines["territory"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
            risk = lines["risk"] ?: "",
            rollback = lines["rollback"] ?: "",
            metric = MetricDeclaration(
                name = lines["metricName"] ?: "",
                baselineValue = lines["metricBaseline"]?.toDoubleOrNull() ?: 0.0,
                targetValue = lines["metricTarget"]?.toDoubleOrNull() ?: 0.0,
                lowerIsBetter = lines["metricLowerIsBetter"]?.toBoolean() ?: false
            ),
            createdAt = Instant.parse(lines["createdAt"] ?: return null),
            state = ProposalState.valueOf(lines["state"] ?: "OPEN"),
            failureCount = lines["failureCount"]?.toIntOrNull() ?: 0
        )
    }
}

class AmendmentCasLedger(private val store: EvidenceStore = EvidenceStore()) : LakehouseLedgerView<AuthorityAmendment> {
    override fun store(value: AuthorityAmendment): String {
        val manifest = buildString {
            appendLine("type=amendment")
            appendLine("id=${value.id}")
            appendLine("proposalId=${value.proposalId}")
            appendLine("sha256=${value.sha256}")
            appendLine("supersedes=${value.supersedes}")
            appendLine("acceptedBy=${value.acceptedBy}")
            appendLine("acceptedAt=${value.acceptedAt}")
            appendLine("evidenceHashes=${value.evidenceHashes.joinToString(",")}")
        }
        return store.put(manifest, EvidenceKind.VERIFIER_FINDING)
    }

    override fun get(hash: String): AuthorityAmendment? {
        val raw = store.get(hash) ?: return null
        val lines = raw.lines().associate {
            val idx = it.indexOf('=')
            if (idx != -1) it.substring(0, idx) to it.substring(idx + 1) else "" to ""
        }
        if (lines["type"] != "amendment") return null
        return AuthorityAmendment(
            id = lines["id"] ?: "",
            proposalId = lines["proposalId"] ?: "",
            sha256 = lines["sha256"] ?: "",
            supersedes = lines["supersedes"] ?: "",
            acceptedBy = lines["acceptedBy"] ?: "",
            acceptedAt = Instant.parse(lines["acceptedAt"] ?: return null),
            evidenceHashes = lines["evidenceHashes"]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        )
    }
}
