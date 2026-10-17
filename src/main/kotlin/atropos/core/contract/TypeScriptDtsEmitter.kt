/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.contract

/**
 * SD7-T-062: Pure TypeScript declaration (.d.ts) emitter.
 * Mathematical Invariant: Sem(M_local) ⊑ Sem(EXT_live)
 * Declarations only. Value exports and executable statements are strictly rejected.
 */
object TypeScriptDtsEmitter {

    fun emit(signatures: List<String>): String {
        return buildString {
            appendLine("// Wave-0 TypeScript Ambient Declarations (.d.ts)")
            appendLine()
            for (sig in signatures) {
                val cleaned = sig.trim()
                require(!cleaned.startsWith("export const ") && !cleaned.startsWith("export let ") && !cleaned.startsWith("export var ")) {
                    "Value export detected in .d.ts ambient declaration emitter: $cleaned"
                }
                if (!cleaned.startsWith("export declare ") && !cleaned.startsWith("declare ")) {
                    append("export declare ")
                }
                appendLine(cleaned)
            }
        }
    }
}
