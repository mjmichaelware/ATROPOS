/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Injects a structured [ContextEnvelope] into a provider prompt.
 *
 * The envelope is serialised as a machine-readable block, followed by
 * a prose instruction that makes the system identity unambiguous.
 *
 * The provider is also told what attestation it must return.
 */
object ProviderContextInjector {

    /**
     * Wrap a raw prompt with the context envelope and identity instructions.
     *
     * @param envelope the structured context for this provider call
     * @param prompt the original user or system prompt
     * @param explicitMythologyRequest whether the user explicitly asked about mythology
     * @return the full prompt including envelope, identity instructions, and attestation requirements
     */
    fun inject(
        envelope: ContextEnvelope,
        prompt: String,
        explicitMythologyRequest: Boolean = false
    ): String = buildString {
        // 1. Serialised envelope (machine-readable)
        appendLine(ContextEnvelopeSerializer.serialize(envelope))

        // 2. Identity instruction (human-readable)
        appendLine()
        appendLine(
            "You are a bounded implementation provider operating inside ATROPOS, " +
            "a local-first autonomous software engineering engine. " +
            "ATROPOS refers to this repository and runtime, not the Greek mythological figure, " +
            "unless mythology was explicitly requested."
        )
        appendLine()
        appendLine("Repository: ${envelope.repository}")
        appendLine("Repository root: ${envelope.repositoryRoot}")
        appendLine("Branch: ${envelope.branch}")
        appendLine("Baseline commit: ${envelope.baselineCommit}")
        if (envelope.goalId.isNotBlank()) appendLine("Goal: ${envelope.goalId}")
        if (envelope.dagId.isNotBlank()) appendLine("DAG: ${envelope.dagId}")
        if (envelope.nodeId.isNotBlank()) appendLine("Node: ${envelope.nodeId}")
        if (envelope.task.isNotBlank()) appendLine("Task: ${envelope.task}")
        if (envelope.phaseOrPass.isNotBlank()) appendLine("Phase/pass: ${envelope.phaseOrPass}")
        if (envelope.hierarchyRole.isNotBlank()) appendLine("Role: ${envelope.hierarchyRole}")
        if (envelope.assignedTerritory.isNotEmpty()) appendLine("Assigned territory: ${envelope.assignedTerritory.joinToString(", ")}")
        if (envelope.activePolicy.isNotBlank()) appendLine("Active policy: ${envelope.activePolicy}")

        appendLine()
        appendLine("Operate only within the supplied task, role, permissions, policy, and territory.")
        appendLine("Do not edit files outside the assigned territory.")
        appendLine("Do not modify build outputs, secrets, credentials, .git, or metadata.")
        appendLine("Do not commit or push.")

        // 3. Attestation requirement
        appendLine()
        appendLine(
            "You MUST include the following attestation block at the end of your response " +
            "so the system can verify that you correctly understood which context you are operating in:"
        )
        appendLine()
        appendLine(ContextEnvelopeSerializer.attestationBlock(
            systemIdentity = envelope.systemIdentity,
            repository = envelope.repository,
            taskOrNodeId = envelope.nodeId.ifBlank { envelope.task.take(64) },
            role = envelope.hierarchyRole,
            contextVersion = envelope.contextVersion,
            contextHash = envelope.canonicalContextHash
        ))
        appendLine()
        appendLine("If you do not include the attestation block, your response will be REJECTED.")
        appendLine()

        // 4. The actual task
        if (explicitMythologyRequest) {
            appendLine(
                "The user has explicitly requested information about Greek mythology. " +
                "A mythology-related answer is permitted in this specific case."
            )
            appendLine()
        }
        appendLine(prompt)
    }
}
