package atropos.core.policy

data class ActionProposal(
    val id: String,
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
    val metadata: Map<String, String> = emptyMap()
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
}
