package atropos.core.ops

import atropos.core.provider.StaticProviderDescriptorRegistry
import java.io.File

data class OpsVerificationResult(
    val passed: Boolean,
    val checked: List<String>,
    val missing: List<String>
)

data class OpsExportResult(
    val root: File,
    val providerTiers: File,
    val providerModels: File,
    val quotaMigration: File,
    val sourceAddendum: File
)

class DeploymentOps(
    private val root: File = File(".atropos/ops"),
    private val registry: StaticProviderDescriptorRegistry = StaticProviderDescriptorRegistry()
) {
    fun export(): OpsExportResult {
        root.mkdirs()
        val tiers = File(root, "provider_tiers.json")
        val models = File(root, "provider_models.json")
        val migration = File(root, "quota_ledger_migration.sql")
        val addendum = File(root, "source-doc-tier-h-addendum.md")

        tiers.writeText(renderProviderTiers())
        models.writeText(renderProviderModels())
        migration.writeText(renderQuotaMigration())
        addendum.writeText(renderAddendum())

        return OpsExportResult(root, tiers, models, migration, addendum)
    }

    fun verify(): OpsVerificationResult {
        val export = export()
        val files = listOf(
            export.providerTiers,
            export.providerModels,
            export.quotaMigration,
            export.sourceAddendum
        )
        val missing = files.filterNot { it.isFile && it.length() > 0L }.map { it.path }
        return OpsVerificationResult(
            passed = missing.isEmpty(),
            checked = files.map { it.path },
            missing = missing
        )
    }

    fun status(): String {
        val export = export()
        return buildString {
            appendLine("ops:")
            appendLine("  root: ${export.root.path}")
            appendLine("  provider_tiers: ${export.providerTiers.path}")
            appendLine("  provider_models: ${export.providerModels.path}")
            appendLine("  quota_migration: ${export.quotaMigration.path}")
            appendLine("  source_addendum: ${export.sourceAddendum.path}")
            appendLine("  policy: local export first; remote deploy optional")
        }
    }

    private fun renderProviderTiers(): String {
        val groups = registry.getAll().groupBy { it.costMode.name.lowercase() }.toSortedMap()
        return buildString {
            appendLine("{")
            groups.entries.forEachIndexed { groupIndex, entry ->
                append("  \"").append(escape(entry.key)).append("\": [")
                if (entry.value.isNotEmpty()) appendLine()
                entry.value.sortedWith(compareBy({ it.quotaTier }, { it.id })).forEachIndexed { index, descriptor ->
                    append("    {\"id\":\"").append(escape(descriptor.id)).append("\",\"tier\":").append(descriptor.quotaTier).append(",\"endpoint\":")
                    if (descriptor.endpointId == null) append("null") else append("\"").append(escape(descriptor.endpointId)).append("\"")
                    append("}")
                    if (index != entry.value.lastIndex) append(",")
                    appendLine()
                }
                append("  ]")
                if (groupIndex != groups.size - 1) append(",")
                appendLine()
            }
            appendLine("}")
        }
    }

    private fun renderProviderModels(): String {
        val descriptors = registry.getAll().sortedBy { it.id }
        return buildString {
            appendLine("{")
            descriptors.forEachIndexed { index, descriptor ->
                append("  \"").append(escape(descriptor.id)).append("\": {")
                append("\"display\":\"").append(escape(descriptor.displayName)).append("\",")
                append("\"capabilities\":[")
                descriptor.capabilities.sortedBy { it.name }.forEachIndexed { capIndex, cap ->
                    if (capIndex > 0) append(",")
                    append("\"").append(cap.name.lowercase()).append("\"")
                }
                append("],\"required_env\":[")
                descriptor.requiredEnv.forEachIndexed { envIndex, envName ->
                    if (envIndex > 0) append(",")
                    append("\"").append(escape(envName)).append("\"")
                }
                append("]}")
                if (index != descriptors.lastIndex) append(",")
                appendLine()
            }
            appendLine("}")
        }
    }

    private fun renderQuotaMigration(): String = """
        create table if not exists provider_quota_ledger (
          provider_id text primary key,
          cost_mode text not null,
          quota_weight integer not null,
          configured integer not null default 0,
          verified integer not null default 0,
          state text not null,
          used_requests integer not null default 0,
          used_tokens integer not null default 0,
          reset_at_epoch_ms integer,
          cooldown_until_epoch_ms integer,
          last_error_class text,
          last_error_summary text,
          latency_ms_avg integer,
          success_score real not null default 0.0,
          paid_locked integer not null default 0,
          updated_at_epoch_ms integer not null
        );
        create index if not exists idx_provider_quota_state on provider_quota_ledger(state);
        create index if not exists idx_provider_quota_cost on provider_quota_ledger(cost_mode, quota_weight);
    """.trimIndent() + "\n"

    private fun renderAddendum(): String = """
        # ATROPOS Tier H Addendum

        Tier H hardens the local-first provider system without requiring remote services.

        - Phase 14: redaction and secret source precedence run before display, persistence, and model prompts.
        - Phase 15: deterministic local test matrix covers descriptors, routing, cooldown, paid locks, redaction, memory, and queue behavior.
        - Phase 16: deployment ops exports provider tier/model manifests and quota ledger migration files.

        Remote systems remain optional. Local compile and local status commands are the acceptance gates.
    """.trimIndent() + "\n"

    private fun escape(value: String): String = buildString {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}
