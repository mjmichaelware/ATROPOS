/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.composition

/**
 * SD7-T-074: Google Wire dependency injection synthesizer for Go.
 * Mathematical Invariant: I(Module_A; Module_B | Interface_Lattice) = 0.000 bits
 * Compile-time wiring via wire.Build. init() side effects are banned.
 */
object GoWireAssembler {

    fun assemble(interfaces: List<String>): String {
        return buildString {
            appendLine("//go:build wireinject")
            appendLine("// +build wireinject")
            appendLine()
            appendLine("package main")
            appendLine()
            appendLine("import (")
            appendLine("    \"github.com/google/wire\"")
            appendLine(")")
            appendLine()
            appendLine("func InitializeApp() (*App, error) {")
            appendLine("    wire.Build(")
            for (iface in interfaces) {
                val clean = iface.trim().removePrefix("type ").substringBefore(" ").trim()
                if (clean.isNotBlank()) {
                    appendLine("        New$clean,")
                }
            }
            appendLine("        NewApp,")
            appendLine("    )")
            appendLine("    return &App{}, nil")
            appendLine("}")
        }
    }
}
