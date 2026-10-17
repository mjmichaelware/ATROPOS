/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.composition

/**
 * SD7-T-072: FastAPI Depends dependency injection synthesizer.
 * Mathematical Invariant: I(Module_A; Module_B | Interface_Lattice) = 0.000 bits
 * Functional dependency injection with Depends(); global state banned.
 */
object FastApiDependsAssembler {

    fun assemble(interfaces: List<String>): String {
        return buildString {
            appendLine("# Wave-0 FastAPI Dependency Injection Assembly")
            appendLine("from typing import Annotated")
            appendLine("from fastapi import Depends")
            appendLine()
            for (iface in interfaces) {
                val clean = iface.trim().removePrefix("class ").substringBefore("(").substringBefore(":").trim()
                if (clean.isNotBlank()) {
                    appendLine("def get_${clean.lowercase()}() -> $clean:")
                    appendLine("    # Clean room provider instantiation")
                    appendLine("    raise NotImplementedError(\"Injected via container lattice\")")
                    appendLine()
                    appendLine("${clean}Dep = Annotated[$clean, Depends(get_${clean.lowercase()})]")
                }
            }
        }
    }
}
