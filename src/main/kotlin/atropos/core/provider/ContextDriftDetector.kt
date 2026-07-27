/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Detects drift between a sent [ContextEnvelope] and a received
 * [ContextAttestation], returning a [TypedContextFailure] if any
 * field does not match.
 */
object ContextDriftDetector {

    /**
     * Compare the sent envelope against the provider's returned attestation.
     *
     * Returns a non-null [TypedContextFailure] if drift is detected,
     * or null if the attestation matches the envelope within acceptable
     * tolerances.
     */
    fun detect(
        envelope: ContextEnvelope,
        attestation: ContextAttestation
    ): TypedContextFailure? {
        // Identity
        if (attestation.systemIdentity != envelope.systemIdentity) {
            return TypedContextFailure.MissingIdentity(
                providerId = envelope.providerId,
                reason = "attested systemIdentity '${attestation.systemIdentity}' != expected '${envelope.systemIdentity}'"
            )
        }

        // Repository
        if (attestation.repository != envelope.repository) {
            return TypedContextFailure.WrongRepository(
                providerId = envelope.providerId,
                reason = "attested repository '${attestation.repository}' != expected '${envelope.repository}'"
            )
        }

        // Role
        if (attestation.role.isNotBlank() && envelope.hierarchyRole.isNotBlank() &&
            attestation.role != envelope.hierarchyRole
        ) {
            return TypedContextFailure.RoleConfusion(
                providerId = envelope.providerId,
                reason = "attested role '${attestation.role}' != assigned '${envelope.hierarchyRole}'"
            )
        }

        // Context version
        if (attestation.contextVersion != envelope.contextVersion) {
            return TypedContextFailure.StaleContext(
                providerId = envelope.providerId,
                reason = "attested contextVersion '${attestation.contextVersion}' != expected '${envelope.contextVersion}'"
            )
        }

        // Hash
        if (attestation.contextHash != envelope.canonicalContextHash) {
            return TypedContextFailure.HashMismatch(
                providerId = envelope.providerId,
                reason = "attested contextHash '${attestation.contextHash.take(16)}…' != expected '${envelope.canonicalContextHash.take(16)}…'"
            )
        }

        // Task/node ID
        val expectedId = envelope.nodeId.ifBlank { envelope.task.take(64) }
        if (attestation.taskOrNodeId.isNotBlank() && expectedId.isNotBlank() &&
            attestation.taskOrNodeId != expectedId
        ) {
            val field = if (envelope.nodeId.isNotBlank()) "DAG node" else "task"
            return TypedContextFailure.WrongTask(
                providerId = envelope.providerId,
                reason = "attested $field '${attestation.taskOrNodeId}' != assigned '$expectedId'"
            )
        }

        return null
    }
}
