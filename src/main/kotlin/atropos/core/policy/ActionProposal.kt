package atropos.core.policy

import java.util.UUID

sealed class TypedProposalSchema {
    data class FileMutationProposal(val paths: List<String>, val contentHash: String) : TypedProposalSchema()
    data class CommandExecutionProposal(val command: List<String>, val workingDirectory: String?) : TypedProposalSchema()
    data class NetworkRequestProposal(val targetUrl: String, val method: String) : TypedProposalSchema()
    data class ProviderInvocationProposal(val providerId: String, val promptTokens: Int) : TypedProposalSchema()
    data object UnknownSchema : TypedProposalSchema()
}

data class ActionProposal(
    val id: String = UUID.randomUUID().toString(),
    val actionClass: PolicyActionClass,
    /**
     * Who is asking. Required and without a default, so an unattributed action
     * cannot be constructed — territory has to know whose territory applies.
     */
    val actor: ActionActor,
    val command: List<String> = emptyList(),
    val cwd: String? = null,
    val targetPaths: List<String> = emptyList(),
    val providerId: String? = null,
    val networkTarget: String? = null,
    val paidProvider: Boolean = false,
    val metadata: Map<String, String> = emptyMap(),
    val schema: TypedProposalSchema = TypedProposalSchema.UnknownSchema
) {
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
                    return ActionProposal(
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
                    return ActionProposal(
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
