/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import atropos.core.memory.MemoryLayerLattice

/**
 * ATOM-VERSION-027: Command Surface Versioning & Deprecation Engine.
 *
 * Provides structured versioning and sunset paths for CLI commands,
 * aliases, and parameter flags without breaking backwards compatibility.
 */
class CommandDeprecationRegistry(
    private val memoryLattice: MemoryLayerLattice = MemoryLayerLattice()
) {
    data class DeprecatedCommand(
        val verb: String,
        val canonical: String,
        val deprecatedSinceVersion: String,
        val sunsetVersion: String,
        val migrationHint: String
    ) {
        fun warningNotice(): String =
            "[DEPRECATION WARNING] '$verb' is deprecated since v$deprecatedSinceVersion " +
            "and will sunset in v$sunsetVersion. Use '$canonical' instead: $migrationHint"
    }

    private val deprecations = mutableMapOf<String, DeprecatedCommand>()

    init {
        bootstrapDeprecations()
    }

    fun register(dep: DeprecatedCommand) {
        deprecations[dep.verb.trim().lowercase()] = dep
    }

    fun checkDeprecation(input: String): DeprecatedCommand? {
        val clean = input.trim().lowercase()
        val match = deprecations[clean] ?: deprecations[clean.removePrefix("/")]
        if (match != null) {
            emitDeprecationReceipt(match)
        }
        return match
    }

    fun allDeprecations(): List<DeprecatedCommand> =
        deprecations.values.sortedBy { it.verb }

    private fun emitDeprecationReceipt(dep: DeprecatedCommand) {
        val timestamp = System.currentTimeMillis()
        val payload = "$timestamp:COMMAND_DEPRECATED_INVOKED:${dep.verb}:${dep.canonical}:${dep.sunsetVersion}"
        val sha256 = MemoryLayerLattice.sha256(payload)
        val json = "{\"timestamp\":$timestamp,\"sha256\":\"$sha256\",\"action\":\"DEPRECATION_TRIGGERED\"," +
            "\"actor\":\"CommandDeprecationRegistry\",\"surface\":\"CLI\",\"before\":\"${dep.verb}\",\"after\":\"${dep.canonical}\"}"
        memoryLattice.put(
            layer = MemoryLayerLattice.LayerType.EVIDENCE_LAYER,
            key = "deprecation_${dep.verb}_$timestamp",
            content = json
        )
    }

    private fun bootstrapDeprecations() {
        register(
            DeprecatedCommand(
                verb = "/usage",
                canonical = "/help",
                deprecatedSinceVersion = "10.0.0",
                sunsetVersion = "11.0.0",
                migrationHint = "Run /help for the 4-tier trie cheatsheet"
            )
        )

        register(
            DeprecatedCommand(
                verb = "/timeline",
                canonical = "/history",
                deprecatedSinceVersion = "10.0.0",
                sunsetVersion = "11.0.0",
                migrationHint = "Run /history to navigate session turns"
            )
        )

        register(
            DeprecatedCommand(
                verb = "/changes",
                canonical = "/diff",
                deprecatedSinceVersion = "10.0.0",
                sunsetVersion = "11.0.0",
                migrationHint = "Run /diff to view syntax-colored hunks"
            )
        )

        register(
            DeprecatedCommand(
                verb = "/app",
                canonical = "/factory",
                deprecatedSinceVersion = "9.8.0",
                sunsetVersion = "11.0.0",
                migrationHint = "Run /factory for app factory commands"
            )
        )
    }
}
