/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.endpoint

/**
 * Binds manifests to the operations they describe, and reports the gap (J009-wire).
 *
 * The registry's value is in what it refuses to hide. An [OperationRegistry] can
 * list twenty endpoints while only three have declared contracts, and without a
 * join across the two there is no way to see that — each side looks healthy
 * alone. [coverage] exists to make the unmanifested set nameable, because an
 * operation that is invocable but undeclared is precisely the one that will be
 * called with assumptions nobody wrote down.
 *
 * A manifest for an id the operation registry does not know about is reported
 * too. That direction usually means an endpoint was renamed or removed and its
 * contract was left behind, which is how a registry starts describing a surface
 * that no longer exists.
 */
class EndpointManifestRegistry(
    private val operations: OperationRegistry,
    manifests: List<EndpointManifest>,
    private val validator: EndpointManifestValidator = EndpointManifestValidator()
) {
    private val byId: Map<String, EndpointManifest> = manifests.associateBy { it.id }
    private val declared: List<EndpointManifest> = manifests

    fun manifestFor(endpointId: String): EndpointManifest? = byId[endpointId]

    fun all(): List<EndpointManifest> = declared

    /** Manifests that fail validation. Empty means every declared contract is complete. */
    fun invalid(): List<EndpointManifestValidation> =
        validator.validateAll(declared).filterNot { it.complete }

    fun coverage(): EndpointManifestCoverage {
        val operationIds = operations.getAll().map { it.id }.toSet()
        return EndpointManifestCoverage(
            declaredIds = byId.keys.sorted(),
            unmanifestedIds = operationIds.minus(byId.keys).sorted(),
            orphanedManifestIds = byId.keys.minus(operationIds).sorted(),
            invalid = invalid()
        )
    }
}

data class EndpointManifestCoverage(
    val declaredIds: List<String>,
    /** Invocable operations with no declared contract. */
    val unmanifestedIds: List<String>,
    /** Contracts for operations that no longer exist. */
    val orphanedManifestIds: List<String>,
    val invalid: List<EndpointManifestValidation>
) {
    val complete: Boolean
        get() = unmanifestedIds.isEmpty() && orphanedManifestIds.isEmpty() && invalid.isEmpty()

    fun evidenceLine(): String =
        "endpoint_manifest_coverage complete=$complete declared=${declaredIds.size} " +
            "unmanifested=${unmanifestedIds.joinToString(",").ifBlank { "none" }} " +
            "orphaned=${orphanedManifestIds.joinToString(",").ifBlank { "none" }} " +
            "invalid=${invalid.joinToString(",") { it.id }.ifBlank { "none" }}"
}
