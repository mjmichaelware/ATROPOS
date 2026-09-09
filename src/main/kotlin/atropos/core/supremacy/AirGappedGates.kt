/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-031: Air-Gapped Full Gates Mode
 *
 * Implements full gates mode for air-gapped environments
 * where no network access is available.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path

object AirGappedGates {
    data class GateConfig(
        val allowNetwork: Boolean = false,
        val allowLocalOnly: Boolean = true,
        val allowedProviders: Set<String> = setOf("local"),
        val requireLocalVerification: Boolean = true,
        val allowedMcpTransports: Set<String> = setOf("stdio")
    )

    data class GateResult(
        val passed: Boolean,
        val reason: String?,
        val requiredCapabilities: Set<String>
    )

    private var config = GateConfig()

    /**
     * Updates the air-gapped gate configuration.
     */
    fun updateConfig(newConfig: GateConfig) {
        config = newConfig
    }

    /**
     * Checks if an operation is allowed in air-gapped mode.
     */
    fun checkOperation(
        operation: String,
        requiredCapabilities: Set<String>
    ): GateResult {
        val missing = requiredCapabilities - config.allowedProviders
        val passed = missing.isEmpty() && config.allowLocalOnly

        return GateResult(
            passed = passed,
            reason = if (passed) null else "Missing capabilities in air-gapped mode: $missing",
            requiredCapabilities = requiredCapabilities
        )
    }

    /**
     * Validates that a provider is allowed in air-gapped mode.
     */
    fun validateProvider(provider: String): Boolean {
        return provider in config.allowedProviders || config.allowLocalOnly && provider == "local"
    }

    /**
     * Validates that an MCP transport is allowed.
     */
    fun validateMcpTransport(transport: String): Boolean {
        return transport in config.allowedMcpTransports
    }

    /**
     * Persists air-gapped config to CAS.
     */
    fun persistConfig(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("air-gapped-config.json")
        val json = """
            {
                "allowNetwork": ${config.allowNetwork},
                "allowLocalOnly": ${config.allowLocalOnly},
                "allowedProviders": [${config.allowedProviders.joinToString(", ") { "\"$it\"" }}],
                "requireLocalVerification": ${config.requireLocalVerification},
                "allowedMcpTransports": [${config.allowedMcpTransports.joinToString(", ") { "\"$it\"" }}]
            }
        """.trimIndent()
        Files.writeString(file, json.trimIndent())
        return file
    }

    /**
     * CLI command to show air-gapped status.
     */
    fun showStatus(): String {
        return """
            Air-Gapped Gates Status:
              Network Allowed: ${config.allowNetwork}
              Local Only: ${config.allowLocalOnly}
              Allowed Providers: ${config.allowedProviders.joinToString(", ")}
              Local Verification Required: ${config.requireLocalVerification}
              Allowed MCP Transports: ${config.allowedMcpTransports.joinToString(", ")}
        """.trimIndent()
    }
}