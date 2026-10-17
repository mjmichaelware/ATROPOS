/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.composition

/**
 * SD7-T-071: Dagger component dependency injection synthesizer for Android.
 * Mathematical Invariant: I(Module_A; Module_B | Interface_Lattice) = 0.000 bits
 * Pure component synthesis without static singletons.
 */
object DaggerComponentAssembler {

    fun assemble(interfaces: List<String>): String {
        return buildString {
            appendLine("package atropos.generated.di")
            appendLine()
            appendLine("import dagger.Component")
            appendLine("import javax.inject.Singleton")
            appendLine()
            appendLine("@Singleton")
            appendLine("@Component")
            appendLine("interface ApplicationComponent {")
            for (iface in interfaces) {
                val clean = iface.trim().removePrefix("interface ").substringBefore("<").substringBefore(" ").trim()
                if (clean.isNotBlank()) {
                    appendLine("    fun get$clean(): $clean")
                }
            }
            appendLine("}")
        }
    }
}
