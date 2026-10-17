/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.contract

/**
 * SD7-T-063: Pure Go interface emitter.
 * Mathematical Invariant: Sem(M_local) ⊑ Sem(EXT_live)
 * Interface definitions only. Concrete types and struct fields with state are rejected.
 */
object GoInterfaceEmitter {

    fun emit(signatures: List<String>): String {
        return buildString {
            appendLine("package contracts")
            appendLine()
            for (sig in signatures) {
                val cleaned = sig.trim()
                require(!cleaned.contains("struct {") && !cleaned.contains("struct{")) {
                    "Concrete struct type detected in Go interface emitter: $cleaned"
                }
                appendLine(cleaned)
            }
        }
    }
}
