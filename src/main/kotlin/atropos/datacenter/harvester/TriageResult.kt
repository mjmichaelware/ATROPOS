package atropos.datacenter.harvester

/**
 * Outcome of autonomous SWE-bench Pro triage on an isolated worktree.
 */
data class TriageResult(
    val issueId: String,
    val repository: String,
    val success: Boolean,
    val generatedPatchDiff: String,
    val verificationLog: String,
    val executionTimeMs: Long
)
