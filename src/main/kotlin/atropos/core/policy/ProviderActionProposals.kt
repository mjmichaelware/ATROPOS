/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import java.util.UUID
import atropos.core.provider.StaticProviderDescriptorRegistry

/**
 * Builds [ActionProposal]s for provider calls.
 *
 * Construction only — no verdict. [ExecutionPolicyEngine] decides whether a
 * provider may be called; this file only states which provider, for what, and
 * whether it is a paid one.
 *
 * [PAID_PROVIDERS] is retained as a compatibility projection for policy callers;
 * the provider descriptor registry is the canonical source for which providers
 * are paid-locked. It previously existed as two private copies inside
 * `AgentService` and `AgentRepairService`; two copies of a security-critical lock
 * can drift out of step, and only one of them would then be enforcing.
 */
object ProviderActionProposals {
    /**
     * Providers that cost money. They stay locked: the engine denies any call
     * where this is true, so adding a name here disables it everywhere at once.
     */
    val PAID_PROVIDERS: Set<String> =
        StaticProviderDescriptorRegistry().getAll()
            .filter { it.billingClass() == atropos.core.provider.BillingClass.PAID }
            .map { it.id }
            .toSet()

    fun isPaid(provider: String): Boolean = provider in PAID_PROVIDERS

    /**
     * @param operation what the call is for, e.g. `"patch"` or `"repair"`.
     * @param promptLength recorded for the audit trail; the prompt itself is
     *   never carried into policy metadata.
     */
    fun forCall(
        provider: String,
        operation: String,
        promptLength: Int,
        actor: ActionActor
    ): ActionProposal =
        ActionProposal(
            id = nextId(),
            actionClass = PolicyActionClass.PROVIDER_CALL,
            actor = actor,
            providerId = provider,
            paidProvider = isPaid(provider),
            metadata = mapOf(
                "operation" to operation,
                "prompt_length" to promptLength.toString(),
                "provider_local" to (StaticProviderDescriptorRegistry().getById(provider)?.isLocal == true).toString()
            )
        )

    private fun nextId(): String = "provider-" + UUID.randomUUID().toString().take(12)
}
