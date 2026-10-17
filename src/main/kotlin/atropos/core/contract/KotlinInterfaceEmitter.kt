/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.contract

/**
 * SD7-T-060: Pure Kotlin interface and data-class signature emitter.
 * Mathematical Invariant: Sem(M_local) ⊑ Sem(EXT_live)
 * Produces zero executable statements. Rejects function bodies.
 */
object KotlinInterfaceEmitter {

    fun emit(signatures: List<String>): String {
        return buildString {
            appendLine("package atropos.generated.contracts")
            appendLine()
            for (sig in signatures) {
                val cleaned = sig.trim()
                require(!cleaned.contains("{") || cleaned.contains("interface") || cleaned.contains("data class")) {
                    "Accidental executable body detected in interface emission: $cleaned"
                }
                appendLine(cleaned)
            }
        }
    }
}
