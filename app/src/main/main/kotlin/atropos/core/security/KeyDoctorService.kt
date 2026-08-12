package atropos.core.security

import atropos.core.AtroposRepoRootLocator
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.StaticProviderDescriptorRegistry
import java.io.File

data class KeyDoctorEntry(
    val name: String,
    val configured: Boolean,
    val source: String,
    val impactedProviders: List<String>
) {
    fun render(): String =
        "$name=${if (configured) "<configured:$source>" else "<missing>"} impact=${impactedProviders.joinToString(",").ifBlank { "none" }}"
}

class KeyDoctorService(
    private val registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val secretSource: CompositeSecretSource = DefaultSecretSource.create(),
    private val setupHelper: KeySetupHelper = KeySetupHelper()
) {
    fun entries(): List<KeyDoctorEntry> {
        val impact = mutableMapOf<String, MutableList<String>>()
        registry.getAll().forEach { descriptor ->
            descriptor.requiredEnv.forEach { name ->
                impact.getOrPut(name) { mutableListOf() } += descriptor.id
            }
        }

        return impact.keys.sorted().map { name ->
            val lookup = secretSource.lookup(name)
            KeyDoctorEntry(
                name = name,
                configured = lookup.configured,
                source = lookup.source,
                impactedProviders = impact[name].orEmpty().sorted()
            )
        }
    }

    fun renderStatus(): String = buildString {
        appendLine("keys:")
        appendLine("  precedence: explicit > environment > local_file")
        entries().forEach { appendLine("  ${it.render()}") }
        appendLine("  raw values: never printed")
    }.trimEnd()

    fun renderSetup(): String {
        val result = setupHelper.setup(entries().map { it.name })
        return buildString {
            appendLine("keys setup:")
            appendLine("  root: ${result.root.path}")
            appendLine("  template: ${result.template.path}")
            appendLine("  readme: ${result.readme.path}")
            appendLine("  names: ${result.names.size}")
            appendLine("  permissions: owner-only best effort")
            appendLine("  raw values: never written by setup")
        }.trimEnd()
    }

    fun renderDoctor(): String {
        val entries = entries()
        val configured = entries.count { it.configured }
        val missing = entries.size - configured
        return buildString {
            appendLine("keys doctor:")
            appendLine("  precedence: explicit > environment > local_file")
            appendLine("  requested: ${entries.size}")
            appendLine("  configured: $configured")
            appendLine("  missing: $missing")
            entries.forEach { entry ->
                appendLine(
                    "  ${entry.name.padEnd(32)} configured=${yesNo(entry.configured)} " +
                        "source=${entry.source} providers=${entry.impactedProviders.joinToString(",").ifBlank { "none" }}"
                )
            }
            appendLine("  remediation: configure missing names through env or .atropos/secrets/*.secret")
        }.trimEnd()
    }

    companion object {
        fun create(
            localRoot: File = AtroposRepoRootLocator.resolve().resolve(".atropos/secrets").toFile()
        ): KeyDoctorService =
            KeyDoctorService(
                secretSource = DefaultSecretSource.create(localRoot = localRoot),
                setupHelper = KeySetupHelper(localRoot)
            )

        private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
    }
}
