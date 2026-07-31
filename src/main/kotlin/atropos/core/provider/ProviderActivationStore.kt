package atropos.core.provider

import atropos.core.AtroposRepoRootLocator
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.io.path.createDirectories
import atropos.core.provider.adapter.AdapterStatus

class ProviderActivationStore(
    private val root: Path = defaultRoot()
) {
    companion object {
        fun defaultRoot(): Path = AtroposRepoRootLocator.resolve().resolve(".atropos/provider/activation")
    }

    init {
        root.createDirectories()
    }

    fun read(providerId: String): ProviderActivationRecord? {
        val target = root.resolve("$providerId.meta")
        if (!Files.isRegularFile(target)) return null
        val lines = runCatching { Files.readAllLines(target, StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val fields = lines.mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }.toMap()

        return runCatching {
            val recordProviderId = fields["providerId"] ?: return null
            val mode = ProviderVerificationMode.valueOf(fields["mode"] ?: "SNAPSHOT")
            val state = ProviderActivationState.valueOf(fields["state"] ?: "MISSING")
            val descriptorPresent = fields["descriptorPresent"]?.toBooleanStrictOrNull() ?: false

            val adapterId = fields["adapterId"]
            val adapterStatus = if (!adapterId.isNullOrBlank()) {
                AdapterStatus(
                    providerId = adapterId,
                    implemented = fields["adapterImplemented"]?.toBooleanStrictOrNull() ?: false,
                    configured = fields["adapterConfigured"]?.toBooleanStrictOrNull() ?: false,
                    dryRunOnly = fields["adapterDryRunOnly"]?.toBooleanStrictOrNull() ?: false,
                    modelCount = 0,
                    health = fields["adapterHealth"].orEmpty(),
                    detail = decode(fields["adapterDetailB64"].orEmpty())
                )
            } else null

            val keySources = decode(fields["keySourcesB64"].orEmpty()).split(",").filter { it.isNotBlank() }
            val impact = decode(fields["impactB64"].orEmpty()).split(",").filter { it.isNotBlank() }
            val executableSupport = fields["executableSupport"]?.toBooleanStrictOrNull() ?: false

            val fixturePassed = fields["fixturePassed"]?.toBooleanStrictOrNull() ?: false
            val fixtureMatrix = if (fixturePassed || fields["fixtureTotalCount"] != null) {
                ProviderFixtureMatrixRecord(
                    providerId = recordProviderId,
                    passed = fixturePassed,
                    passedCount = fields["fixturePassedCount"]?.toIntOrNull() ?: 0,
                    totalCount = fields["fixtureTotalCount"]?.toIntOrNull() ?: 0,
                    details = emptyList()
                )
            } else null

            ProviderActivationRecord(
                providerId = recordProviderId,
                mode = mode,
                state = state,
                descriptorPresent = descriptorPresent,
                adapterStatus = adapterStatus,
                keySources = keySources,
                impact = impact,
                executableSupport = executableSupport,
                fixtureMatrix = fixtureMatrix,
                verificationSummary = decode(fields["verificationSummaryB64"].orEmpty()),
                remediation = decode(fields["remediationB64"].orEmpty()),
                lastCheckedAt = Instant.parse(fields["lastCheckedAt"] ?: Instant.now().toString())
            )
        }.getOrNull()
    }

    fun write(record: ProviderActivationRecord) {
        val target = root.resolve("${record.providerId}.meta")
        val temp = Files.createTempFile(root, "${record.providerId}.", ".tmp")
        val content = buildString {
            appendLine("providerId=${record.providerId}")
            appendLine("mode=${record.mode.name}")
            appendLine("state=${record.state.name}")
            appendLine("descriptorPresent=${record.descriptorPresent}")
            appendLine("adapterId=${record.adapterStatus?.providerId ?: ""}")
            appendLine("adapterImplemented=${record.adapterStatus?.implemented ?: false}")
            appendLine("adapterConfigured=${record.adapterStatus?.configured ?: false}")
            appendLine("adapterDryRunOnly=${record.adapterStatus?.dryRunOnly ?: false}")
            appendLine("adapterHealth=${record.adapterStatus?.health ?: ""}")
            appendLine("adapterDetailB64=${encode(record.adapterStatus?.detail.orEmpty())}")
            appendLine("keySourcesB64=${encode(record.keySources.joinToString(","))}")
            appendLine("impactB64=${encode(record.impact.joinToString(","))}")
            appendLine("executableSupport=${record.executableSupport}")
            appendLine("fixturePassed=${record.fixtureMatrix?.passed ?: false}")
            appendLine("fixturePassedCount=${record.fixtureMatrix?.passedCount ?: 0}")
            appendLine("fixtureTotalCount=${record.fixtureMatrix?.totalCount ?: 0}")
            appendLine("verificationSummaryB64=${encode(record.verificationSummary)}")
            appendLine("remediationB64=${encode(record.remediation)}")
            appendLine("lastCheckedAt=${record.lastCheckedAt}")
        }
        Files.writeString(temp, content, StandardCharsets.UTF_8)
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun encode(value: String): String =
        java.util.Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        if (value.isBlank()) "" else String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
}
