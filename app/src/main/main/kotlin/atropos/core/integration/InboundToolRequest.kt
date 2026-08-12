/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

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
    val requiresNetwork: Boolean = false
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
