/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Machine-readable attestation that a provider includes in its response
 * to confirm which context it was operating under.
 *
 * Every provider response must include a valid [ContextAttestation] block
 * before the response is accepted for display, execution, or completion.
 */
data class ContextAttestation(
    /** Must match the envelope's systemIdentity (typically "ATROPOS"). */
    val systemIdentity: String,
    /** Must match the envelope's repository. */
    val repository: String,
    /** The task or DAG node ID assigned to this provider call. */
    val taskOrNodeId: String,
    /** The role the provider was assigned (e.g. "worker"). */
    val role: String,
    /** Must match the envelope's contextVersion. */
    val contextVersion: String,
    /** Must match the envelope's canonicalContextHash. */
    val contextHash: String
) {
    /** Verify this attestation against the sent [envelope]. */
    fun matches(envelope: ContextEnvelope): Boolean =
        systemIdentity == envelope.systemIdentity &&
        repository == envelope.repository &&
        contextVersion == envelope.contextVersion &&
        contextHash == envelope.canonicalContextHash

    /** Verify this attestation matches with a specific [taskOrNodeId] override. */
    fun matches(envelope: ContextEnvelope, expectedTaskOrNodeId: String): Boolean =
        matches(envelope) && taskOrNodeId == expectedTaskOrNodeId
}
