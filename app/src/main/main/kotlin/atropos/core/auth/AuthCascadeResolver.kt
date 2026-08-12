/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

/**
 * Resolves a key across the layered authority documents.
 *
 * `SUP.AUTH.CASCADE-PRECEDENCE`: "Core architectural invariants are
 * non-overridable by construction; P(core-dilution)=0. Competitors allow
 * project-level override of safety rules."
 *
 * That is the whole design. An ordinary key takes the value from the
 * highest-precedence document that defines it, exactly as a normal config
 * cascade would. A key in [CORE_KEYS] cannot be overridden at all — a lower
 * layer that tries produces an [AuthorityViolation] rather than a new value,
 * because a safety invariant that a project file can turn off is not an
 * invariant.
 */
class AuthCascadeResolver(
    private val coreKeys: Set<String> = CORE_KEYS
) {
    /**
     * @param layers ordered strongest first, e.g. Agents.md, Swarm.md,
     *   settings.json, territory-local.
     */
    fun resolve(key: String, layers: List<AuthorityLayer>): CascadeResolution {
        val defining = layers.filter { it.values.containsKey(key) }
        if (defining.isEmpty()) return CascadeResolution.Undefined(key)

        val winner = defining.minByOrNull { it.rank }
            ?: return CascadeResolution.Undefined(key)

        if (key in coreKeys) {
            val overriders = defining.filter { it.rank > winner.rank }
            if (overriders.isNotEmpty()) {
                return CascadeResolution.Violation(
                    key = key,
                    heldBy = winner.name,
                    attemptedBy = overriders.map { it.name },
                    reason = "$key is a core invariant and cannot be overridden by " +
                        overriders.joinToString(", ") { it.name }
                )
            }
        }

        return CascadeResolution.Resolved(
            key = key,
            value = winner.values.getValue(key),
            source = winner.name,
            final = key in coreKeys
        )
    }

    /** A snapshot for the status matrix: every key, its winner, and finality. */
    fun snapshot(layers: List<AuthorityLayer>): List<CascadeResolution> =
        layers.flatMap { it.values.keys }.distinct().sorted().map { resolve(it, layers) }

    companion object {
        /**
         * The keys no project-level file may weaken.
         *
         * Derived from the invariants §6 and `P20-H01` name as immutable:
         * human authority, territory at dispatch, the agency gate, independent
         * verification, secret policy, and the free-space gate.
         */
        val CORE_KEYS: Set<String> = setOf(
            "humanAuthority",
            "territoryAtDispatch",
            "boundedAgencyGate",
            "independentVerification",
            "secretPolicy",
            "freeSpaceGate"
        )
    }
}

data class AuthorityLayer(
    val name: String,
    /** Lower wins. */
    val rank: Int,
    val values: Map<String, String>
)

sealed class CascadeResolution {
    data class Resolved(
        val key: String,
        val value: String,
        val source: String,
        /** True when no lower layer may ever change this. */
        val final: Boolean
    ) : CascadeResolution()

    data class Violation(
        val key: String,
        val heldBy: String,
        val attemptedBy: List<String>,
        val reason: String
    ) : CascadeResolution()

    data class Undefined(val key: String) : CascadeResolution()
}
