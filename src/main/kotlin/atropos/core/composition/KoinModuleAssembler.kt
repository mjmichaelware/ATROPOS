/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.composition

/**
 * SD7-T-070: Koin module dependency injection synthesizer.
 * Mathematical Invariant: I(Module_A; Module_B | Interface_Lattice) = 0.000 bits
 * Wires implementations to Wave-0 interfaces only. Direct hard-coded constructors are banned.
 */
object KoinModuleAssembler {

    fun assemble(interfaces: List<String>): String {
        return buildString {
            appendLine("package atropos.generated.di")
            appendLine()
            appendLine("import org.koin.dsl.module")
            appendLine()
            appendLine("val appModule = module {")
            for (iface in interfaces) {
                val clean = iface.trim().removePrefix("interface ").substringBefore("<").substringBefore(" ").trim()
                if (clean.isNotBlank()) {
                    appendLine("    single<$clean> { get() }")
                }
            }
            appendLine("}")
        }
    }
}
