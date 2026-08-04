package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.AtroposCostPolicy
import atropos.core.provider.CostMode
import atropos.core.provider.InMemoryQuotaLedger
import atropos.core.provider.ProviderAvailabilityState
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.ProviderQuotaRecord
import atropos.core.provider.ProviderTask
import atropos.core.paid.EmergencyPaidGate
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdapterRouteFacadePolicyTest {
    @Test
    fun unlocked_paid_adapter_is_allowed_by_the_shared_policy_decision() {
        var calls = 0
        val descriptor = ProviderDescriptor(
            id = "openai",
            displayName = "OpenAI",
            costMode = CostMode.PAID_LOCKED,
            quotaTier = 1,
            capabilities = setOf(ApiCapability.CHAT),
            requiredEnv = listOf("OPENAI_API_KEY")
        )
        val adapter = CountingAdapter(descriptor) { calls++ }
        val registry = SingleProviderRegistry(descriptor)
        val quota = InMemoryQuotaLedger(
            listOf(
                ProviderQuotaRecord(
                    providerId = descriptor.id,
                    costMode = descriptor.costMode,
                    quotaWeight = descriptor.quotaTier,
                    configured = true,
                    verified = true,
                    state = ProviderAvailabilityState.READY,
                    paidLocked = true
                )
            )
        )
        val paidGate = EmergencyPaidGate(Files.createTempDirectory("atropos-route-paid-").toFile())
        paidGate.unlock("openai", "1m", "fixture")
        val facade = AdapterRouteFacade(
            descriptorRegistry = registry,
            adapterRegistry = SingleAdapterRegistry(adapter),
            ledger = quota,
            costPolicy = AtroposCostPolicy.PAID_EMERGENCY_UNLOCKED,
            paidGate = paidGate
        )

        val result = facade.decide(
            ProviderTask(atropos.core.provider.ProviderTaskKind.CHAT_PROMPT, ApiCapability.CHAT, "hello"),
            prompt = "hello",
            dryRun = true
        )

        assertEquals(1, calls)
        assertTrue(result.dryRunResult is ProviderCallResult.LocalOnly)
    }

    private class SingleProviderRegistry(private val descriptor: ProviderDescriptor) : ProviderDescriptorRegistry {
        override fun getAll() = listOf(descriptor)
        override fun getById(id: String) = getAll().firstOrNull { it.id == id }
        override fun getFreeEligible() = emptyList<ProviderDescriptor>()
        override fun getPaidLocked() = listOf(descriptor)
        override fun getByCapability(capability: ApiCapability) = getAll().filter { capability in it.capabilities }
    }

    private class SingleAdapterRegistry(private val adapter: ProviderAdapter) : ProviderAdapterRegistry {
        override fun getAll() = listOf(adapter)
        override fun getByProviderId(providerId: String) = getAll().firstOrNull { it.providerId == providerId }
        override fun getByCapability(capability: ApiCapability) = getAll().filter { capability in it.capabilities }
        override fun status() = getAll().map { it.status() }
    }

    private class CountingAdapter(
        override val descriptor: ProviderDescriptor,
        private val onComplete: () -> Unit
    ) : ProviderAdapter {
        override fun status() = AdapterStatus(descriptor.id, true, false, false, 1, "offline", "test")
        override fun complete(request: AdapterRequest): ProviderCallResult {
            onComplete()
            return ProviderCallResult.LocalOnly(request.task, "unexpected")
        }
    }
}
