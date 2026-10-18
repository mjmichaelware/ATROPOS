/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.AtroposConfig
import atropos.core.paid.EmergencyPaidGate
import atropos.core.security.CredentialVault
import atropos.core.security.TokenIsolationVault
import java.time.Instant

/**
 * ProviderDaemon - Synchronous Provider Health Check & Real-Time Sync.
 *
 * Replaces asynchronous fire-and-forget health checks with synchronous,
 * blocking validation during bootstrap. If an API key is present in the
 * vault, its health state MUST initialize as READY immediately upon
 * successful signature validation.
 */
class ProviderDaemon(
    private val credentialVault: CredentialVault,
    private val registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val onboarding: ProviderOnboardingService,
    private val config: AtroposConfig
) {
    /** Maximum time to wait for provider health check (ms). */
    private val healthCheckTimeoutMs = 5000L

    /** Blocking boot-time health validation. Must complete before TUI renders. */
    fun validateAndSync(): ProviderHealthReport {
        val startTime = Instant.now()
        val report = mutableMapOf<String, ProviderHealth>()

        // Discover models and prune dead providers via live /v1/models probing
        ModelDiscoveryEngine.discoverAndPrune(healthCheckTimeoutMs)

        // Get all providers that have credentials in vault
        val providersWithCreds = registry.getAll().filter { descriptor ->
            val requiredEnv = descriptor.requiredEnv
            hasCredentials(descriptor) || (descriptor.requiredEnv.isEmpty() && descriptor.id == "ollama")
        }

        if (providersWithCreds.isEmpty()) {
            return ProviderHealthReport(
                timestamp = startTime,
                providers = emptyMap(),
                overallHealthy = false,
                message = "No providers have configured credentials. Run '/provider connect' to configure."
            )
        }

        // Synchronous health check for each provider with credentials
        for (descriptor in providersWithCreds) {
            val health = checkProviderHealthBlocking(descriptor)
            report[descriptor.id] = health
        }

        // Update onboarding with validated health states
        onboarding.refresh().forEach { provider ->
            val health = report[provider.providerId]
            if (health != null) {
                onboarding.recordLiveTest(
                    provider.providerId,
                    health.state == ProviderAvailabilityState.READY
                )
            }
        }

        val overallHealthy = report.values.any { it.state == ProviderAvailabilityState.READY }
        val message = if (overallHealthy) {
            "Providers validated: ${report.filter { (_, h) -> h.state == ProviderAvailabilityState.READY }.keys.joinToString(", ")}"
        } else {
            "No healthy providers. Check credentials and network."
        }

        return ProviderHealthReport(
            timestamp = startTime,
            providers = report,
            overallHealthy = overallHealthy,
            message = message
        )
    }

    /** Blocking health check with timeout. */
    private fun checkProviderHealthBlocking(descriptor: ProviderDescriptor): ProviderHealth {
        // Ollama is local-only, check process
        if (descriptor.id == "ollama") {
            return checkOllamaHealth()
        }

        // For remote providers, check if we have valid credentials
        val requiredEnv = descriptor.requiredEnv
        val hasValidCreds = hasCredentials(descriptor)

        if (!hasValidCreds) {
            return ProviderHealth(
                providerId = descriptor.id,
                state = ProviderAvailabilityState.OFFLINE
            )
        }

        // Never assume healthy just because credentials exist: check live discovery
        if (ModelDiscoveryEngine.isPruned(descriptor.id)) {
            return ProviderHealth(
                providerId = descriptor.id,
                state = ProviderAvailabilityState.OFFLINE
            )
        }

        if (ModelDiscoveryEngine.hasDiscoveredModels(descriptor.id)) {
            if (descriptor.billingClass() == BillingClass.PAID && !EmergencyPaidGate().isProviderUnlocked(descriptor.id)) {
                return ProviderHealth(
                    providerId = descriptor.id,
                    state = ProviderAvailabilityState.OFFLINE
                )
            }
            return ProviderHealth(
                providerId = descriptor.id,
                state = ProviderAvailabilityState.READY
            )
        }

        return ProviderHealth(
            providerId = descriptor.id,
            state = ProviderAvailabilityState.OFFLINE
        )
    }

    private fun hasCredentials(descriptor: ProviderDescriptor): Boolean {
        if (descriptor.id == "ollama") return true
        val required = descriptor.requiredEnv
        if (required.isEmpty()) return true

        return required.any { envVar ->
            // 1. Vault check
            credentialVault.has(envVar) ||
                runCatching { !TokenIsolationVault(AtroposConfig.configRoot().resolve("secrets")).readSecret(envVar).isNullOrBlank() }.getOrDefault(false) ||
                // 2. Process environment
                !System.getenv(envVar).isNullOrBlank() ||
                // 3. In-memory config keys
                configHasKey(envVar)
        }
    }

    private fun configHasKey(envVar: String): Boolean = when (envVar.uppercase()) {
        "GROQ_API_KEY" -> config.keys.groq.isNotBlank()
        "OPENAI_API_KEY" -> config.keys.openai.isNotBlank()
        "ANTHROPIC_API_KEY" -> config.keys.anthropic.isNotBlank()
        "XAI_API_KEY" -> config.keys.xai.isNotBlank()
        else -> false
    }

    private fun checkOllamaHealth(): ProviderHealth {
        try {
            val process = ProcessBuilder("ollama", "list")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            if (finished && process.exitValue() == 0) {
                return ProviderHealth(
                    providerId = "ollama",
                    state = ProviderAvailabilityState.READY
                )
            }
        } catch (_: Exception) {
            // Ollama not available
        }
        return ProviderHealth(
            providerId = "ollama",
            state = ProviderAvailabilityState.OFFLINE
        )
    }
}

data class ProviderHealthReport(
    val timestamp: Instant,
    val providers: Map<String, ProviderHealth>,
    val overallHealthy: Boolean,
    val message: String
)

/** Extension for easy access. */
fun ProviderDaemon.healthyProviderIds(): Set<String> = validateAndSync().providers
    .filter { (_, health) -> health.state == ProviderAvailabilityState.READY }
    .keys