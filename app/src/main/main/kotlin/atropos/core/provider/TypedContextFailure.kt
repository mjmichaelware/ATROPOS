/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Typed context failures for provider response attestation.
 *
 * Every failure carries a [providerId], a human-readable [reason] and
 * optionally a [retryable] flag.  Non-retryable failures should prevent
 * automatic retry on the same provider.
 */
sealed class TypedContextFailure(
    open val providerId: String,
    open val reason: String,
    open val retryable: Boolean = true
) {
    /** Provider returned Greek mythology content when context was about ATROPOS software. */
    data class MythologyAnswer(
        override val providerId: String,
        override val reason: String,
        override val retryable: Boolean = true
    ) : TypedContextFailure(providerId, reason, retryable)

    /** Provider response did not include a system identity field. */
    data class MissingIdentity(
        override val providerId: String,
        override val reason: String,
        override val retryable: Boolean = true
    ) : TypedContextFailure(providerId, reason, retryable)

    /** Provider response did not include a context attestation block. */
    data class MissingAttestation(
        override val providerId: String,
        override val reason: String,
        override val retryable: Boolean = true
    ) : TypedContextFailure(providerId, reason, retryable)

    /** Provider response included an attestation block but it was malformed. */
    data class MalformedAttestation(
        override val providerId: String,
        override val reason: String,
        override val retryable: Boolean = true
    ) : TypedContextFailure(providerId, reason, retryable)

    /** Provider claimed a role that does not match what was assigned. */
    data class RoleConfusion(
        override val providerId: String,
        override val reason: String,
        override val retryable: Boolean = false
    ) : TypedContextFailure(providerId, reason, retryable)

    /** Provider claimed a different repository than the current one. */
    data class WrongRepository(
        override val providerId: String,
        override val reason: String,
        override val retryable: Boolean = false
    ) : TypedContextFailure(providerId, reason, retryable)

    /** Provider claimed a task that does not match the assigned task. */
    data class WrongTask(
        override val providerId: String,
        override val reason: String,
        override val retryable: Boolean = false
    ) : TypedContextFailure(providerId, reason, retryable)

    /** Provider claimed a DAG node that does not match the assigned node. */
    data class WrongDagNode(
        override val providerId: String,
        override val reason: String,
        override val retryable: Boolean = false
    ) : TypedContextFailure(providerId, reason, retryable)

    /** Provider attested with a stale context version or hash. */
    data class StaleContext(
        override val providerId: String,
        override val reason: String,
        override val retryable: Boolean = true
    ) : TypedContextFailure(providerId, reason, retryable)

    /** Provider attested with a hash that does not match the sent envelope. */
    data class HashMismatch(
        override val providerId: String,
        override val reason: String,
        override val retryable: Boolean = false
    ) : TypedContextFailure(providerId, reason, retryable)

    /** Provider operated outside its assigned territory. */
    data class TerritoryMismatch(
        override val providerId: String,
        override val reason: String,
        override val retryable: Boolean = false
    ) : TypedContextFailure(providerId, reason, retryable)

    /** Provider violated the active policy. */
    data class PolicyMismatch(
        override val providerId: String,
        override val reason: String,
        override val retryable: Boolean = false
    ) : TypedContextFailure(providerId, reason, retryable)
}
