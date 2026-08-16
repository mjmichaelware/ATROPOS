package atropos.cli.ui

import atropos.core.ops.DeploymentOps
import atropos.core.evaluation.EvaluationEngine
import atropos.core.provider.QuotaLedgerBackup
import java.io.File

class StatusOpsRenderer(
    private val ops: DeploymentOps = DeploymentOps(),
    private val evaluation: EvaluationEngine = EvaluationEngine()
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

    fun evaluate(subjectId: String = "operator"): String {
        val decision = evaluation.evaluateRelease(subjectId = subjectId)
        return buildString {
            appendLine("evaluation:")
            appendLine("  accepted: ${decision.accepted}")
            appendLine("  reason: ${decision.reason}")
            appendLine("  report: ${decision.report.summary}")
            decision.report.metrics.forEach { metric ->
                appendLine("  ${metric.kind.name.lowercase()}: ${metric.passed} (${metric.evidence})")
            }
        }.trimEnd()
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
