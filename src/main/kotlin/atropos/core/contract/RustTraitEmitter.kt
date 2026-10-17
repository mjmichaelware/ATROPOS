/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.contract

/**
 * SD7-T-061: Pure Rust trait emitter producing zero impl blocks.
 * Mathematical Invariant: Sem(M_local) ⊑ Sem(EXT_live)
 * Traits only in Wave-0. Implementation blocks are strictly rejected.
 */
object RustTraitEmitter {

    fun emit(signatures: List<String>): String {
        return buildString {
            appendLine("//! Wave-0 Rust Trait Contracts - Zero implementation blocks permitted.")
            appendLine()
            for (sig in signatures) {
                val cleaned = sig.trim()
                require(!cleaned.contains("impl ") && !cleaned.contains("impl<")) {
                    "Impl leakage detected in Wave-0 Rust trait emission: $cleaned"
                }
                appendLine(cleaned)
            }
        }
    }
}
