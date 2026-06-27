package atropos.cli.ui

import atropos.core.ops.DeploymentOps

class StatusOpsRenderer(private val ops: DeploymentOps = DeploymentOps()) {
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
}
