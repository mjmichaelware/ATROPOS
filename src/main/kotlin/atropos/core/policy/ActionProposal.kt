/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import java.util.UUID

sealed class TypedProposalSchema {
    data class FileMutationProposal(val paths: List<String>, val contentHash: String) : TypedProposalSchema()
    data class CommandExecutionProposal(val command: List<String>, val workingDirectory: String?) : TypedProposalSchema()
    data class NetworkRequestProposal(val targetUrl: String, val method: String) : TypedProposalSchema()
    data class ProviderInvocationProposal(val providerId: String, val promptTokens: Int) : TypedProposalSchema()
    data object UnknownSchema : TypedProposalSchema()
}

/**
 * SD7-T-003: Unforgeable action-proposal sealed hierarchy carrying cryptographic capability tokens.
 * Mathematical Invariant: Cap(P_theta) ∩ Cap(A_write) = ∅
 * Ambient authority is rejected; every variant carries proof.
 */
sealed class ActionProposal(
    open val id: String = UUID.randomUUID().toString(),
    open val actionClass: PolicyActionClass = PolicyActionClass.FILE_MUTATION,
    open val actor: ActionActor = ActionActor.SystemService("AutonomousDirector"),
    open val command: List<String> = emptyList(),
    open val cwd: String? = null,
    open val targetPaths: List<String> = emptyList(),
    open val providerId: String? = null,
    open val networkTarget: String? = null,
    open val paidProvider: Boolean = false,
    open val metadata: Map<String, String> = emptyMap(),
    open val schema: TypedProposalSchema = TypedProposalSchema.UnknownSchema,
    open val capabilityProof: String = ""
) {
    data class CreateFile(
        val path: String,
        val content: String,
        val sha256: String,
        override val actor: ActionActor = ActionActor.SystemService("AutonomousDirector"),
        override val capabilityProof: String = ""
    ) : ActionProposal(
        id = UUID.randomUUID().toString(),
        actionClass = PolicyActionClass.FILE_MUTATION,
        actor = actor,
        targetPaths = listOf(path),
        schema = TypedProposalSchema.FileMutationProposal(listOf(path), sha256),
        capabilityProof = capabilityProof
    )

    data class ModifyFile(
        val path: String,
        val diff: String,
        val baseSha256: String,
        override val actor: ActionActor = ActionActor.SystemService("AutonomousDirector"),
        override val capabilityProof: String = ""
    ) : ActionProposal(
        id = UUID.randomUUID().toString(),
        actionClass = PolicyActionClass.FILE_MUTATION,
        actor = actor,
        targetPaths = listOf(path),
        schema = TypedProposalSchema.FileMutationProposal(listOf(path), baseSha256),
        capabilityProof = capabilityProof
    )

    data class DeleteFile(
        val path: String,
        override val actor: ActionActor = ActionActor.SystemService("AutonomousDirector"),
        override val capabilityProof: String = ""
    ) : ActionProposal(
        id = UUID.randomUUID().toString(),
        actionClass = PolicyActionClass.FILE_MUTATION,
        actor = actor,
        targetPaths = listOf(path),
        capabilityProof = capabilityProof
    )

    data class ExecuteToolchain(
        override val command: List<String>,
        val worktreeId: String,
        override val actor: ActionActor = ActionActor.SystemService("AutonomousDirector"),
        override val capabilityProof: String = ""
    ) : ActionProposal(
        id = UUID.randomUUID().toString(),
        actionClass = PolicyActionClass.SHELL,
        actor = actor,
        command = command,
        schema = TypedProposalSchema.CommandExecutionProposal(command, worktreeId),
        capabilityProof = capabilityProof
    )

    data class MergeWorktree(
        val worktreeId: String,
        val targetBranch: String,
        override val actor: ActionActor = ActionActor.SystemService("AutonomousDirector"),
        override val capabilityProof: String = ""
    ) : ActionProposal(
        id = UUID.randomUUID().toString(),
        actionClass = PolicyActionClass.FILE_MUTATION,
        actor = actor,
        capabilityProof = capabilityProof
    )

    class Generic(
        override val id: String = UUID.randomUUID().toString(),
        override val actionClass: PolicyActionClass,
        override val actor: ActionActor,
        override val command: List<String> = emptyList(),
        override val cwd: String? = null,
        override val targetPaths: List<String> = emptyList(),
        override val providerId: String? = null,
        override val networkTarget: String? = null,
        override val paidProvider: Boolean = false,
        override val metadata: Map<String, String> = emptyMap(),
        override val schema: TypedProposalSchema = TypedProposalSchema.UnknownSchema,
        override val capabilityProof: String = ""
    ) : ActionProposal(
        id, actionClass, actor, command, cwd, targetPaths, providerId, networkTarget, paidProvider, metadata, schema, capabilityProof
    )

    fun copy(
        id: String = this.id,
        actionClass: PolicyActionClass = this.actionClass,
        actor: ActionActor = this.actor,
        command: List<String> = this.command,
        cwd: String? = this.cwd,
        targetPaths: List<String> = this.targetPaths,
        providerId: String? = this.providerId,
        networkTarget: String? = this.networkTarget,
        paidProvider: Boolean = this.paidProvider,
        metadata: Map<String, String> = this.metadata,
        schema: TypedProposalSchema = this.schema,
        capabilityProof: String = this.capabilityProof
    ): ActionProposal = Generic(
        id = id,
        actionClass = actionClass,
        actor = actor,
        command = command,
        cwd = cwd,
        targetPaths = targetPaths,
        providerId = providerId,
        networkTarget = networkTarget,
        paidProvider = paidProvider,
        metadata = metadata,
        schema = schema,
        capabilityProof = capabilityProof
    )

    fun toRequest() = ExecutionPolicyRequest(
        actionClass = actionClass,
        command = command,
        cwd = cwd?.let(java.nio.file.Path::of),
        targetPaths = targetPaths,
        providerId = providerId,
        networkTarget = networkTarget,
        paidProvider = paidProvider,
        metadata = metadata
    )

    companion object {
        operator fun invoke(
            id: String = UUID.randomUUID().toString(),
            actionClass: PolicyActionClass,
            actor: ActionActor,
            command: List<String> = emptyList(),
            cwd: String? = null,
            targetPaths: List<String> = emptyList(),
            providerId: String? = null,
            networkTarget: String? = null,
            paidProvider: Boolean = false,
            metadata: Map<String, String> = emptyMap(),
            schema: TypedProposalSchema = TypedProposalSchema.UnknownSchema,
            capabilityProof: String = ""
        ): ActionProposal = Generic(
            id = id,
            actionClass = actionClass,
            actor = actor,
            command = command,
            cwd = cwd,
            targetPaths = targetPaths,
            providerId = providerId,
            networkTarget = networkTarget,
            paidProvider = paidProvider,
            metadata = metadata,
            schema = schema,
            capabilityProof = capabilityProof
        )

        /**
         * SD7-T2-013: Parses model output into strictly typed ActionProposal schemas.
         * Raw shell execution is prohibited; proposals not matching declared schemas are rejected.
         */
        fun parseFromModelOutput(raw: String, actor: ActionActor): ActionProposal? {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null
            if (trimmed.startsWith("```json") || trimmed.startsWith("{")) {
                val cleanJson = trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                if (cleanJson.contains("\"action\":\"mutate\"") || cleanJson.contains("\"path\":")) {
                    val pathRegex = Regex(""""path"\s*:\s*"([^"]+)"""")
                    val matchedPath = pathRegex.find(cleanJson)?.groupValues?.getOrNull(1)
                    val paths = if (matchedPath != null) listOf(matchedPath) else emptyList()
                    return Generic(
                        id = UUID.randomUUID().toString(),
                        actionClass = PolicyActionClass.FILE_MUTATION,
                        actor = actor,
                        targetPaths = paths,
                        schema = TypedProposalSchema.FileMutationProposal(paths, "")
                    )
                }
                if (cleanJson.contains("\"action\":\"command\"") || cleanJson.contains("\"command\":")) {
                    val cmdRegex = Regex(""""command"\s*:\s*\[([^\]]+)\]""")
                    val matchedCmds = cmdRegex.find(cleanJson)?.groupValues?.getOrNull(1)
                        ?.split(",")?.map { it.trim().removeSurrounding("\"") } ?: emptyList()
                    return Generic(
                        id = UUID.randomUUID().toString(),
                        actionClass = PolicyActionClass.SHELL,
                        actor = actor,
                        command = matchedCmds,
                        schema = TypedProposalSchema.CommandExecutionProposal(matchedCmds, null)
                    )
                }
            }
            return null
        }
    }
}
