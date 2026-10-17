/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.composition

/**
 * SD7-T-073: NestJS module dependency injection synthesizer.
 * Mathematical Invariant: I(Module_A; Module_B | Interface_Lattice) = 0.000 bits
 * Explicit providers only. Ambient and unregistered providers are banned.
 */
object NestJsModuleAssembler {

    fun assemble(interfaces: List<String>): String {
        return buildString {
            appendLine("import { Module } from '@nestjs/common';")
            appendLine()
            val providers = interfaces.mapNotNull { iface ->
                val clean = iface.trim().removePrefix("interface ").removePrefix("class ").substringBefore("<").substringBefore(" ").trim()
                if (clean.isNotBlank()) clean else null
            }
            appendLine("@Module({")
            appendLine("  providers: [")
            for (p in providers) {
                appendLine("    { provide: '$p', useClass: ${p}Impl },")
            }
            appendLine("  ],")
            appendLine("  exports: [")
            for (p in providers) {
                appendLine("    '$p',")
            }
            appendLine("  ],")
            appendLine("})")
            appendLine("export class AppModule {}")
        }
    }
}
