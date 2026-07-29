package atropos.core.policy

data class CapabilitySet(
    val values: Set<String>
) {
    fun missing(required: CapabilitySet): Set<String> = required.values - values

    companion object {
        fun parse(raw: String?): CapabilitySet =
            CapabilitySet(
                raw.orEmpty()
                    .split(",", "|", " ")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
            )
    }
}

data class EnforcementViolation(
    val proposalId: String,
    val actorIdentity: String,
    val missingCapabilities: Set<String>,
    val reason: String
)

class CapabilityEnforcer {
    fun evaluate(proposal: ActionProposal): EnforcementViolation? {
        val required = CapabilitySet.parse(proposal.metadata["requiredCapabilities"])
        if (required.values.isEmpty()) return null

        val granted = CapabilitySet.parse(proposal.metadata["grantedCapabilities"])
        val missing = granted.missing(required)
        if (missing.isEmpty()) return null

        return EnforcementViolation(
            proposalId = proposal.id,
            actorIdentity = proposal.actor.identity,
            missingCapabilities = missing,
            reason = "capability refusal: ${proposal.actor.identity} lacks ${missing.sorted().joinToString(",")}"
        )
    }
}
