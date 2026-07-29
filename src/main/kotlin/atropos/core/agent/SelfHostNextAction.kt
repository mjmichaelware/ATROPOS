package atropos.core.agent

enum class SelfHostNextActionKind {
    ADVANCE_NODE,
    ADVANCE_GOAL,
    PROMOTE_JAR,
    WAIT_EXTERNAL_INPUT,
    COMPLETE,
    HARD_STOP
}

data class SelfHostNextAction(
    val kind: SelfHostNextActionKind,
    val goalId: String?,
    val nodeId: String?,
    val reason: String
) {
    fun evidenceLine(): String =
        "next_action kind=$kind goal=${goalId ?: "none"} node=${nodeId ?: "none"} reason=${reason.replace('\n', ' ').take(240)}"
}
