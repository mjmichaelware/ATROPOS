package atropos.cli.ui

import atropos.core.ops.DeploymentOps
import atropos.core.provider.QuotaLedgerBackup
import java.io.File

class StatusOpsRenderer(
    private val ops: DeploymentOps = DeploymentOps()
) {
    fun render(): String = ops.status()

    fun export(): String {
        val result = ops.export()
        return buildString {
            appendLine("ops export:")
            appendLine("  provider_tiers: ${result.providerTiers.path}")
            appendLine("  provider_models: ${result.providerModels.path}")
            appendLine("  quota_migration: ${result.quotaMigration.path}")
            appendLine("  source_addendum: ${result.sourceAddendum.path}")
        }
    }

    fun verify(): String {
        val result = ops.verify()
        return buildString {
            appendLine("ops verification:")
            appendLine("  passed: ${result.passed}")
            appendLine("  checked: ${result.checked.size}")
            result.checked.forEach { appendLine("  ok: $it") }
            result.missing.forEach { appendLine("  missing: $it") }
        }
    }

    fun quotaBackup(): String {
        val result = QuotaLedgerBackup().backup()
        return buildString {
            appendLine("quota backup:")
            appendLine("  file: ${result.file.path}")
            appendLine("  records: ${result.records}")
        }
    }

    fun quotaRestore(path: String): String {
        val result = QuotaLedgerBackup().restore(File(path))
        return buildString {
            appendLine("quota restore:")
            appendLine("  file: ${result.file.path}")
            appendLine("  records: ${result.records}")
        }
    }
}
