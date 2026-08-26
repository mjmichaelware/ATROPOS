/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.Role
import atropos.core.storage.GcOutcome
import atropos.core.storage.RetentionPolicy
import atropos.core.storage.StorageConstitution
import atropos.core.storage.StorageSupervisor
import atropos.core.storage.BlobStoreGc.IntegrityReport

class StatusStorageRenderer(
    private val theme: TerminalTheme = TerminalTheme(ConfigurationManager())
) {
    private val surface get() = theme.surface

    fun renderStatus(constitution: StorageConstitution, supervisor: StorageSupervisor, width: Int): List<String> {
        val percentage = (constitution.fractionUsed * 100).toInt()
        val health = when {
            percentage > 90 -> Health.ERROR
            percentage > 70 -> Health.PENDING
            else -> Health.VERIFIED
        }
        val header = listOf(
            surface.statusRow("used", "${mib(constitution.usedBytes)} of ${mib(constitution.ceilingBytes)} ($percentage%)", health, width),
            surface.row("free", "${mib(constitution.remainingBytes)} before ceiling", width),
            surface.row("reclaimable", mib(constitution.reclaimableBytes()), width)
        )
        val body = if (constitution.classes.isEmpty()) {
            header + listOf(surface.hint("  (nothing stored yet)", width))
        } else {
            val classLines = constitution.classes.map { storageClass ->
                val retention = supervisor.retentionClass(storageClass)
                surface.row(storageClass.id, "${mib(storageClass.bytes)} [${retention.tier.canonical}]", width)
            }
            header + classLines
        }
        return surface.block("STORAGE STATUS", body, width, Role.BRAND)
    }

    fun renderPolicy(policy: RetentionPolicy, width: Int): List<String> {
        val body = policy.declared().map { (name, rule) ->
            surface.row(name, rule.describe(), width)
        } + listOf(
            surface.hint("policy: referenced files are never collected regardless of age", width)
        )
        return surface.block("RETENTION POLICY", body, width, Role.BRAND)
    }

    fun renderGc(outcomes: List<GcOutcome>, applied: Boolean, width: Int): List<String> {
        val title = if (applied) "GARBAGE COLLECTION" else "DRY RUN (NO DELETION)"
        val body = outcomes.flatMap { outcome ->
            val header = surface.statusRow(outcome.storageClass, outcome.render(), Health.VERIFIED, width)
            val details = outcome.auditLines().map { "    $it" }
            listOf(header) + details
        } + if (!applied) listOf(surface.hint("re-run with --apply to delete", width)) else emptyList()
        return surface.block(title, body, width, Role.BRAND)
    }

    fun renderVerify(report: IntegrityReport, width: Int): List<String> {
        val health = if (report.sound) Health.VERIFIED else Health.ERROR
        val body = listOf(
            surface.statusRow("verdict", if (report.sound) "sound" else "corrupt", health, width)
        ) + report.auditLines().map { "  $it" }
        return surface.block("INTEGRITY REPORT", body, width, Role.BRAND)
    }

    private fun mib(bytes: Long): String =
        if (bytes < 1024 * 1024) "${bytes}B" else "${bytes / (1024 * 1024)}MiB"
}
