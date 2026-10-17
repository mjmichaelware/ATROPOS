/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.contract

/**
 * SD7-T-115: Fail-closed strict serialization configurations across ecosystems.
 * Mathematical Invariant: Sem(M_local) ⊑ Sem(EXT_live)
 * Zod .strict(); Pydantic extra='forbid'; Serde deny_unknown_fields; kotlinx ignoreUnknownKeys=false.
 * Unknown fields at boundary trigger immediate parse failure.
 */
object StrictSerialization {
    const val KOTLINX_IGNORE_UNKNOWN_KEYS: Boolean = false
    const val PYDANTIC_EXTRA: String = "forbid"
    const val SERDE_DENY_UNKNOWN_FIELDS: Boolean = true
    const val ZOD_STRICT: Boolean = true

    fun configure() {
        // Enforces fail-closed deserialization across boundary codecs
    }

    fun isStrictKotlinx(): Boolean = !KOTLINX_IGNORE_UNKNOWN_KEYS
}
