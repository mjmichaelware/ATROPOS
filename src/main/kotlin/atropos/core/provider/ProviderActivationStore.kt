package atropos.core.provider

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories

class ProviderActivationStore(
    private val root: Path = Path.of(".atropos/provider/activation")
) {
    init {
        root.createDirectories()
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
}
