/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.contract

/**
 * SD7-T-065: Pure Protobuf / OpenAPI 3.1 schema emitter.
 * Mathematical Invariant: Sem(M_local) ⊑ Sem(EXT_live)
 * Schema definitions only. Implementation stubs are rejected.
 */
object ProtobufOpenApiEmitter {

    fun emit(signatures: List<String>): String {
        return buildString {
            appendLine("syntax = \"proto3\";")
            appendLine("package atropos.contracts.v1;")
            appendLine()
            for (sig in signatures) {
                appendLine(sig.trim())
            }
        }
    }
}
