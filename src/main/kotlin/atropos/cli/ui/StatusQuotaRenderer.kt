package atropos.cli.ui

import atropos.core.provider.AtroposCostPolicy
import atropos.core.provider.CostMode
import atropos.core.provider.FileQuotaLedger
import atropos.core.provider.FreeModeGuard
import atropos.core.provider.InMemoryQuotaLedger
import atropos.core.provider.LocalToolchainProbe
import atropos.core.provider.ProviderAvailabilityState
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.ProviderEligibility
import atropos.core.provider.ProviderQuotaRecord
import atropos.core.provider.ProviderTaskClassifier
import atropos.core.provider.QuotaLedger
import atropos.core.provider.RoutePolicy
import atropos.core.provider.RoutePolicyDecision
import atropos.core.provider.StaticProviderDescriptorRegistry
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class StatusQuotaRenderer(
    private val registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val ledger: QuotaLedger = InMemoryQuotaLedger(
        FileQuotaLedger.seedFromDescriptors(StaticProviderDescriptorRegistry())
    ),
    private val workspace: File = File("."),
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val costPolicy: AtroposCostPolicy = AtroposCostPolicy.FREE_ONLY
) {
    private val classifier = ProviderTaskClassifier()

    fun renderQuota(): String {
        val records = ledger.all().associateBy { it.providerId }
        val descriptors = registry.getAll().sortedWith(
            compareBy<ProviderDescriptor>({ it.costMode.ordinal }, { it.quotaTier }, { it.id })
        )

        val out = mutableListOf<String>()
        out += "quota: free_only"
        out += "providers: ${descriptors.size}"
        out += "free eligible: ${registry.getFreeEligible().size}"
        out += "paid locked: ${registry.getPaidLocked().size}"
        out += "columns: cost q provider state config used reset cooldown score"

        descriptors.forEach { descriptor ->
            val record = records[descriptor.id] ?: descriptor.toSeedRecord()
            out += quotaLine(descriptor, record)
        }

        out += ""
        out += renderLocalHealthLines()
        return out.joinToString("\n")
    }

    fun renderRoute(prompt: String): String {
        val task = classifier.classify(prompt)
        val decision = RoutePolicy(
            registry = registry,
            ledger = ledger,
            costPolicy = costPolicy,
            nowEpochMs = nowEpochMs
        ).decide(task)

        val out = mutableListOf<String>()
        out += "route task: ${task.kind.name.lowercase()}"
        out += "capability: ${task.capability.name.lowercase()}"
        out += "policy: ${costPolicy.name.lowercase()}"
        out += "selected: ${decision.selectedProviderId ?: "none"}"
        out += "queued: ${decision.queued}"
        out += "degraded: ${decision.degraded}"
        decision.queueReason?.let { out += "queue reason: $it" }

        out += ""
        out += "eligible:"
        if (decision.eligible.isEmpty()) {
            out += "  none"
        } else {
            decision.eligible.take(12).forEach { out += "  ${eligibilityLine(it)}" }
            if (decision.eligible.size > 12) out += "  ... ${decision.eligible.size - 12} more"
        }

        out += ""
        out += "skipped:"
        if (decision.skipped.isEmpty()) {
            out += "  none"
        } else {
            decision.skipped.take(16).forEach { out += "  ${eligibilityLine(it)}" }
            if (decision.skipped.size > 16) out += "  ... ${decision.skipped.size - 16} more"
        }

        out += ""
        out += "fallback chain:"
        out += renderFallbackChain(decision)

        return out.joinToString("\n")
    }

    fun renderFailures(): String {
        val failed = ledger.all()
            .filter { it.lastErrorClass != null || terminalState(it.state) }
            .sortedWith(compareBy({ it.state.ordinal }, { it.providerId }))

        val out = mutableListOf<String>()
        out += "failures:"
        if (failed.isEmpty()) {
            out += "  none recorded"
        } else {
            failed.forEach { record ->
                out += "  ${record.providerId.padEnd(18)} state=${record.state.name.lowercase()} error=${record.lastErrorClass ?: "state_only"} summary=${record.lastErrorSummary ?: "no summary"}"
            }
        }

        val queued = ledger.all().count {
            it.state == ProviderAvailabilityState.COOLDOWN ||
                it.state == ProviderAvailabilityState.EXHAUSTED_UNTIL_RESET
        }
        out += "queue count: $queued"
        out += ""
        out += renderLocalHealthLines()

        return out.joinToString("\n")
    }

    fun renderPaidStatus(): String {
        val guard = FreeModeGuard(AtroposCostPolicy.FREE_ONLY)
        val paid = registry.getPaidLocked().sortedWith(compareBy({ it.quotaTier }, { it.id }))
        val out = mutableListOf<String>()

        out += "paid providers: locked"
        out += "policy: free_only"
        out += "unlock: unavailable until Tier F"
        out += "paid count: ${paid.size}"

        paid.forEach { descriptor ->
            val record = ledger.get(descriptor.id) ?: descriptor.toSeedRecord()
            val allowed = guard.allows(descriptor)
            out += "  locked  q=${descriptor.quotaTier} ${descriptor.id.padEnd(18)} allowed=$allowed state=${record.state.name.lowercase()} endpoint=${descriptor.endpointId ?: "no-endpoint"}"
        }

        return out.joinToString("\n")
    }

    fun renderDefaultStatusSummary(): String {
        val records = ledger.all()
        val ready = records.count { it.state == ProviderAvailabilityState.READY }
        val cooldown = records.count { it.state == ProviderAvailabilityState.COOLDOWN }
        val exhausted = records.count { it.state == ProviderAvailabilityState.EXHAUSTED_UNTIL_RESET }
        val locked = registry.getPaidLocked().size

        return listOf(
            "status observability: tier_a_phase_4",
            "ready: $ready | cooldown: $cooldown | exhausted: $exhausted | paid locked: $locked",
            "routes: /status quota | /status route <task> | /status failures | /paid status"
        ).joinToString("\n")
    }

    private fun quotaLine(descriptor: ProviderDescriptor, record: ProviderQuotaRecord): String {
        val cost = descriptor.costMode.name.lowercase().padEnd(13)
        val provider = descriptor.id.padEnd(18)
        val state = record.state.name.lowercase().padEnd(21)
        val configured = if (record.configured) "yes" else "no"
        val used = "${record.usedRequests}/${record.usedTokens}"
        val reset = formatEpoch(record.resetAtEpochMs)
        val cooldown = formatEpoch(record.cooldownUntilEpochMs)
        val score = String.format("%.2f", record.successScore)
        return "  $cost q=${descriptor.quotaTier} $provider $state config=$configured used=$used reset=$reset cooldown=$cooldown score=$score"
    }

    private fun eligibilityLine(item: ProviderEligibility): String {
        val provider = item.provider
        val quota = item.quota
        val state = quota?.state?.name?.lowercase() ?: "quota_unknown"
        val cost = provider.costMode.name.lowercase()
        return "${provider.id.padEnd(18)} cost=$cost q=${provider.quotaTier} state=$state reason=${item.reason}"
    }

    private fun renderFallbackChain(decision: RoutePolicyDecision): String {
        val selected = decision.selected ?: return "  local degraded mode"
        if (selected.fallbackChain.isEmpty()) return "  ${selected.id} -> local degraded mode"
        val chain = mutableListOf(selected.id)
        selected.fallbackChain.take(6).forEach { chain += it }
        chain += "local degraded mode"
        return "  " + chain.joinToString(" -> ")
    }

    private fun renderLocalHealthLines(): String {
        val probe = LocalToolchainProbe(workspace)
        val probes = runCatching { probe.probeAll() }.getOrElse { emptyList() }
        val out = mutableListOf<String>()
        out += "local root health:"
        if (probes.isEmpty()) {
            out += "  local.probes unavailable"
        } else {
            probes.forEach { item ->
                val state = if (item.available) "ready" else "missing"
                val details = if (item.details.isBlank()) "" else " ${item.details}"
                out += "  $state ${item.id} ${item.summary}$details"
            }
        }
        return out.joinToString("\n")
    }

    private fun ProviderDescriptor.toSeedRecord(): ProviderQuotaRecord =
        ProviderQuotaRecord(
            providerId = id,
            costMode = costMode,
            quotaWeight = quotaTier,
            configured = isLocal,
            verified = isLocal,
            state = if (isLocal) ProviderAvailabilityState.READY else ProviderAvailabilityState.UNKNOWN,
            paidLocked = isPaidLocked()
        )

    private fun terminalState(state: ProviderAvailabilityState): Boolean =
        state == ProviderAvailabilityState.AUTH_FAILED ||
            state == ProviderAvailabilityState.BILLING_REQUIRED ||
            state == ProviderAvailabilityState.OFFLINE ||
            state == ProviderAvailabilityState.DISABLED

    private fun formatEpoch(value: Long?): String {
        if (value == null) return "-"
        return runCatching {
            val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault())
            formatter.format(Instant.ofEpochMilli(value))
        }.getOrDefault("-")
    }
}
