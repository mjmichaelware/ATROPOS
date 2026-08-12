package atropos.core.agent

/** Failure classifications persisted by bounded self-host/process evidence. */
enum class AgentExecutionFailure {
    INVALID_COMMAND,
    POLICY_REFUSED,
    LAUNCH_FAILED,
    TIMEOUT,
    NONZERO_EXIT,
    MISSING_ARTIFACT,
    OUTPUT_TRUNCATED,
    EVIDENCE_MISSING,
    REPOSITORY_COMMAND_FAILED
}
