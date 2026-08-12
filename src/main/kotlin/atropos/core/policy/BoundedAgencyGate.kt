package atropos.core.policy

enum class AgencyDisposition {
    ALLOWED,
    POLICY_BLOCKED,
    APPROVAL_REQUIRED
}

data class AgencyDecision(
    val proposal: ActionProposal,
    val policyDecision: ExecutionPolicyDecision,
    val disposition: AgencyDisposition,
    val reason: String
)

class BoundedAgencyGate(
    private val policyEngine: ExecutionPolicyEngine = ExecutionPolicyEngine(),
    private val territory: atropos.core.territory.TerritoryGrantService =
        atropos.core.territory.TerritoryGrantService(),
    private val capabilityEnforcer: CapabilityEnforcer = CapabilityEnforcer(),
    /**
     * Storage admission. Lazy because measuring walks the state directory, and
     * a gate constructed for a provider call should not pay for a walk it will
     * never consult.
     */
    private val storage: Lazy<atropos.core.storage.StorageSupervisor> =
        lazy { atropos.core.storage.StorageSupervisor() }
) {
    fun evaluate(proposal: ActionProposal): AgencyDecision {
        territoryRefusal(proposal)?.let { return it }
        capabilityRefusal(proposal)?.let { return it }
        storageRefusal(proposal)?.let { return it }

        val decision = policyEngine.evaluate(proposal.toRequest())
        val disposition = when (decision.decision) {
            PolicyDecisionType.ALLOW -> AgencyDisposition.ALLOWED
            PolicyDecisionType.DENY -> AgencyDisposition.POLICY_BLOCKED
            PolicyDecisionType.APPROVAL_REQUIRED -> AgencyDisposition.APPROVAL_REQUIRED
        }
        return AgencyDecision(
            proposal = proposal,
            policyDecision = decision,
            disposition = disposition,
            reason = decision.reason
        )
    }

    /**
     * Refuses an action that would grow storage past what the device or the
     * declared ceiling allows.
     *
     * Only the classes that can actually grow storage are checked, so a
     * provider call or a lifecycle transition costs nothing. The check runs
     * after territory and capability because a write that is out of bounds is
     * refused for being out of bounds — telling the operator to free disk space
     * for a write that was never going to be permitted sends them to fix the
     * wrong thing.
     *
     * A warning is not a refusal. The gate's job here is `P(disk-full-crash)=0`,
     * not keeping the disk tidy, and blocking work at the warn band would stop
     * an autonomous run with most of its ceiling still unused.
     */
    private fun storageRefusal(proposal: ActionProposal): AgencyDecision? {
        if (proposal.actionClass !in STORAGE_GROWING) return null

        val decision = runCatching { storage.value.admit(0) }.getOrNull() ?: return null
        if (decision !is atropos.core.storage.FreeSpaceDecision.Refused) return null

        val reason = "storage refusal: ${decision.reason}; " +
            "${decision.reclaimableBytes}B could be reclaimed with '/storage gc'"

        return AgencyDecision(
            proposal = proposal,
            policyDecision = ExecutionPolicyDecision(
                id = "storage",
                decision = PolicyDecisionType.DENY,
                actionClass = proposal.actionClass,
                destructive = false,
                reason = reason
            ),
            disposition = AgencyDisposition.POLICY_BLOCKED,
            reason = reason
        )
    }

    private fun capabilityRefusal(proposal: ActionProposal): AgencyDecision? {
        val violation = capabilityEnforcer.evaluate(proposal) ?: return null
        return AgencyDecision(
            proposal = proposal,
            policyDecision = ExecutionPolicyDecision(
                id = "capability",
                decision = PolicyDecisionType.DENY,
                actionClass = proposal.actionClass,
                destructive = false,
                reason = violation.reason
            ),
            disposition = AgencyDisposition.POLICY_BLOCKED,
            reason = violation.reason
        )
    }

    /**
     * Territory is checked before policy, because a node reaching outside its
     * grant is refused regardless of how benign the action itself looks.
     *
     * Only dispatched work is bounded this way. [ActionActor.HumanOwner] holds
     * the root grant, so bounding the owner out of their own repository would be
     * incoherent, and [ActionActor.SystemService] lifecycle transitions touch no
     * paths. A hierarchy node that declares no paths has nothing to check —
     * dispatchers refuse those before they get this far.
     */
    private fun territoryRefusal(proposal: ActionProposal): AgencyDecision? {
        val actor = proposal.actor
        if (actor !is ActionActor.HierarchyNode) return null
        if (proposal.targetPaths.isEmpty()) return null

        val offending = territory.firstPathOutsideTerritory(actor, proposal.targetPaths)
            ?: return null

        val reason = "territory refusal: ${actor.identity} holds no grant covering '$offending'"
        territory.recordViolation(actor, offending, reason)

        return AgencyDecision(
            proposal = proposal,
            policyDecision = ExecutionPolicyDecision(
                id = "territory",
                decision = PolicyDecisionType.DENY,
                actionClass = proposal.actionClass,
                destructive = false,
                reason = reason
            ),
            disposition = AgencyDisposition.POLICY_BLOCKED,
            reason = reason
        )
    }

    private companion object {
        /** The action classes that can leave bytes on disk behind them. */
        val STORAGE_GROWING = setOf(
            PolicyActionClass.FILE_MUTATION,
            PolicyActionClass.PATCH_APPLY,
            PolicyActionClass.BUILD_TEST,
            PolicyActionClass.GIT
        )
    }
}
