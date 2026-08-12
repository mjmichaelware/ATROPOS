package atropos.core.agent

/** Stable machine-readable refusal categories for self-host command boundaries. */
enum class SelfHostFailureCode {
    GOAL_NOT_FOUND,
    MISSING_GOAL,
    MISSING_DAG,
    EVIDENCE_EXPORT_FAILED,
    EVIDENCE_HASH_MISSING,
    PROMOTION_REFUSED,
    EVIDENCE_INCOMPLETE
}
