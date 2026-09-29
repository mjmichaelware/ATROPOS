/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-016: Capability Attenuation Sandbox
 *
 * Implements capability-based security with attenuation,
 * allowing fine-grained delegation of permissions.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path

object CapabilityAttenuation {
    sealed class Capability {
        data class Read(val path: String) : Capability()
        data class Write(val path: String) : Capability()
        data class Execute(val command: String) : Capability()
        data class Network(val host: String, val port: Int) : Capability()
        data class Delegate(val capability: Capability, val constraints: List<String>) : Capability()
    }

    data class AttenuatedCapability(
        val base: Capability,
        val constraints: List<String> = emptyList()
    ) {
        fun canExercise(other: Capability): Boolean = when {
            this.base == other -> true
            this.base is Capability.Delegate -> this.base.constraints.all { it.isNotEmpty() }
            else -> false
        }

        fun attenuate(newConstraints: List<String>): AttenuatedCapability {
            return copy(constraints = constraints + newConstraints)
        }
    }

    /**
     * Attenuates a capability with new constraints.
     */
    fun attenuate(base: Capability, constraints: List<String>): AttenuatedCapability {
        return AttenuatedCapability(base, constraints)
    }

    /**
     * Checks if an attenuated capability can perform an operation.
     */
    fun canPerform(attenuated: AttenuatedCapability, operation: Capability): Boolean {
        return attenuated.canExercise(operation)
    }

    /**
     * Creates a delegated capability with constraints.
     */
    fun delegate(base: Capability, constraints: List<String>): Capability {
        return Capability.Delegate(base, constraints)
    }

    /**
     * Persists capability sandbox to CAS.
     */
    fun persistSandbox(casDir: Path, capabilities: List<AttenuatedCapability>): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("capability-sandbox.json")
        val json = capabilities.map { c ->
            """
                {
                    "base": "${c.base}",
                    "constraints": [${c.constraints.joinToString(", ") { "\"$it\"" }}]
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show sandbox.
     */
    fun showSandbox(capabilities: List<AttenuatedCapability>): String {
        return capabilities.joinToString("\n") { "  ${it.base} [${it.constraints.joinToString(", ")}]" }
    }
}