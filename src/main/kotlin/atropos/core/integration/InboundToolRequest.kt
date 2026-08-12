/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDecision
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.PolicyActionClass
import atropos.core.security.RedactionFilter

/**
 * An inbound request from an external caller — MCP, or a computer-use actuator.
 *
 * `SUP.INT.MCP-TERRITORY-BRIDGE`: "External callers receive exactly the same
 * bounds as internal agents." `SUP.INT.COMPUTER-USE-BRIDGE`: "No direct OS or
 * browser handle escapes the gate."
 *
 * Both atoms reduce to one rule, which this type exists to make unavoidable: an
 * external request is not a special kind of call, it is an [atropos.core.policy.ActionProposal]
 * that happens to have arrived over a socket. Converting it here — before any
 * executor sees it — is what stops "MCP support" from becoming a second,
 * weaker execution path beside the gate.
 */
data class InboundToolRequest(
    val source: InboundSource,
    /** Caller-supplied identity. Never trusted as authority, only recorded. */
    val callerId: String,
    val operation: String,
    val paths: List<String>,
    val requiresNetwork: Boolean = false,
    val targetSurface: String? = null,
    val territoryGrantId: String? = null
)

enum class InboundSource { MCP, COMPUTER_USE }

/**
 * Converts an inbound request into something the gate can judge, or refuses.
 *
 * Refusal here is not policy — policy still runs afterwards. This only rejects
 * requests that could never be expressed as a bounded proposal at all, so that
 * everything reaching the gate is a real proposal rather than a shape the gate
 * has to special-case.
 */
class InboundToolBridge(
    private val allowedOperations: Set<String>,
    private val networkPermitted: Boolean = false
) {
    fun admit(request: InboundToolRequest): InboundAdmission {
        if (request.callerId.isBlank()) {
            return InboundAdmission.Refused("an inbound request must identify its caller")
        }
        if (request.operation !in allowedOperations) {
            return InboundAdmission.Refused(
                "'${request.operation}' is not exposed to ${request.source.name.lowercase()} callers"
            )
        }
        if (request.paths.isEmpty()) {
            // A pathless request cannot be territory-bounded, and admitting it
            // would hand the gate something it cannot judge.
            return InboundAdmission.Refused("an inbound request must declare the paths it touches")
        }
        if (request.paths.any { it.contains("..") }) {
            return InboundAdmission.Refused("path traversal is not admissible from an external caller")
        }
        if (request.requiresNetwork && !networkPermitted) {
            return InboundAdmission.Refused("network access is not granted to external callers")
        }
        return InboundAdmission.Admitted(
            operation = request.operation,
            territory = request.paths,
            actorId = "${request.source.name.lowercase()}:${request.callerId}"
        )
    }
}

sealed class InboundAdmission {
    /**
     * Admitted to the gate — not permitted to run. The distinction is the whole
     * point: this type carries no authority of its own.
     */
    data class Admitted(
        val operation: String,
        val territory: List<String>,
        val actorId: String
    ) : InboundAdmission()

    data class Refused(val reason: String) : InboundAdmission()

    val admitted: Boolean get() = this is Admitted
}

/** The common proposal-to-gate boundary for all external integration sources. */
class InboundActionProposalBridge(
    private val admission: InboundToolBridge,
    private val gate: (ActionProposal) -> AgencyDecision = BoundedAgencyGate()::evaluate,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun judge(request: InboundToolRequest): InboundGateResult {
        val admitted = admission.admit(request)
        if (admitted is InboundAdmission.Refused) {
            return InboundGateResult.Refused(admitted.reason)
        }
        val accepted = admitted as InboundAdmission.Admitted
        val proposal = ActionProposal(
            id = "inbound-${request.source.name.lowercase()}-${redactionFilter.stableFingerprint(request.callerId)}",
            actionClass = if (request.requiresNetwork) PolicyActionClass.NETWORK else PolicyActionClass.FILE_MUTATION,
            actor = ActionActor.HierarchyNode(request.source.name.lowercase(), request.callerId),
            targetPaths = accepted.territory,
            networkTarget = if (request.requiresNetwork) "external:${request.operation}" else null,
            metadata = mapOf(
                "operation" to accepted.operation,
                "source" to request.source.name.lowercase(),
                "territoryGrantId" to redactionFilter.redact(request.territoryGrantId.orEmpty()),
                "targetSurface" to redactionFilter.redact(request.targetSurface.orEmpty())
            )
        )
        return InboundGateResult.Judged(proposal, gate(proposal))
    }
}

/** MCP has no execution authority of its own; it only reaches the common gate boundary. */
class McpTerritoryBridge(
    allowedOperations: Set<String>,
    networkPermitted: Boolean = false,
    gate: (ActionProposal) -> AgencyDecision = BoundedAgencyGate()::evaluate
) {
    private val bridge = InboundActionProposalBridge(InboundToolBridge(allowedOperations, networkPermitted), gate)

    fun judge(request: InboundToolRequest): InboundGateResult =
        if (request.source != InboundSource.MCP) {
            InboundGateResult.Refused("MCP bridge accepts MCP requests only")
        } else bridge.judge(request)
}

/** Computer-use is a proposal adapter; the target surface and grant are mandatory. */
class ComputerUseTerritoryBridge(
    allowedOperations: Set<String>,
    networkPermitted: Boolean = false,
    gate: (ActionProposal) -> AgencyDecision = BoundedAgencyGate()::evaluate
) {
    private val bridge = InboundActionProposalBridge(InboundToolBridge(allowedOperations, networkPermitted), gate)

    fun judge(request: InboundToolRequest): InboundGateResult {
        if (request.source != InboundSource.COMPUTER_USE) {
            return InboundGateResult.Refused("computer-use bridge accepts computer-use requests only")
        }
        if (request.targetSurface.isNullOrBlank() || request.territoryGrantId.isNullOrBlank()) {
            return InboundGateResult.Refused("computer-use requires a named target surface and territory grant")
        }
        return bridge.judge(request)
    }
}

sealed interface InboundGateResult {
    data class Judged(val proposal: ActionProposal, val decision: AgencyDecision) : InboundGateResult
    data class Refused(val reason: String) : InboundGateResult
}
