package atropos.core.autonomous

import atropos.core.provider.ProviderActivationService
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.provider.QuotaLedger
import atropos.core.provider.adapter.AdapterRouteFacade
import atropos.dloi.DloiService

class ProviderFailoverService(
    private val backlog: AutonomousBacklogService = AutonomousBacklogService(),
    private val activationService: ProviderActivationService? = null,
    private val descriptorRegistry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val dloiService: DloiService = DloiService()
) {
    data class FailoverPlan(
        val primaryId: String,
        val fallbackId: String,
        val reason: String,
        val availableAlternatives: List<String>
    )

    fun assess(providerId: String): FailoverPlan? {
        val descriptors = descriptorRegistry.getAll()
        val primary = descriptors.firstOrNull { it.id == providerId } ?: return null
        val fallbacks = descriptors
            .filter { it.id != providerId && it.isFreeEligible() }
            .map { it.id }
        val fallbackId = fallbacks.firstOrNull() ?: descriptors.firstOrNull { it.id != providerId }?.id ?: return null
        return FailoverPlan(
            primaryId = providerId,
            fallbackId = fallbackId,
            reason = "auto-failover from $providerId",
            availableAlternatives = fallbacks
        )
    }

    fun failover(primaryId: String, fallbackId: String, reason: String): ProviderFailoverEvent {
        val event = backlog.recordFailover(primaryId, fallbackId, reason, true)
        return event
    }

    fun autoFailover(primaryId: String): ProviderFailoverEvent? {
        val plan = assess(primaryId) ?: return null
        val escalated = plan.fallbackId == plan.availableAlternatives.lastOrNull()
        val event = ProviderFailoverEvent(
            primaryProviderId = primaryId,
            fallbackProviderId = plan.fallbackId,
            failureReason = plan.reason,
            success = true,
            escalated = escalated
        )
        backlog.recordFailover(primaryId, plan.fallbackId, plan.reason, true, escalated)
        return event
    }

    fun failoverHistory(): List<ProviderFailoverEvent> = backlog.failoverHistory()

    fun renderFailoverStatus(): String {
        val history = backlog.failoverHistory()
        if (history.isEmpty()) return "ProviderFailover: no events recorded"
        return history.joinToString("\n") { "  ${it.id}: ${it.primaryProviderId} -> ${it.fallbackProviderId} (${if (it.success) "OK" else "FAIL"})${if (it.escalated) " ESCALATED" else ""}" }
    }
}
