package atropos.core.agent

import java.nio.file.Path

/** Hash-linked build facts. Display truncation is evidence, never a verdict. */
data class SelfHostBuildEvidence(
    val exitCode: Int?,
    val timedOut: Boolean,
    val totalOutputBytes: Long,
    val totalOutputLines: Long,
    val outputTruncated: Boolean,
    val outputSha256: String?,
    val displayHead: String,
    val displayTail: String,
    val fullLogPath: Path? = null,
    val fullLogSha256: String? = null,
    val candidateJarPath: Path? = null,
    val candidateJarSize: Long? = null,
    val candidateJarSha256: String? = null,
    val testResultSummary: String? = null,
    val requestedCommand: List<String> = emptyList(),
    val proposalId: String? = null
) {
    fun compactLine(): String = listOf(
        "exit=$exitCode", "timed_out=$timedOut", "output_bytes=$totalOutputBytes",
        "output_lines=$totalOutputLines", "truncated=$outputTruncated",
        "output_sha256=${outputSha256 ?: "none"}",
        "full_log_sha256=${fullLogSha256 ?: "none"}",
        "candidate_jar=${candidateJarPath?.fileName ?: "none"}",
        "candidate_jar_size=${candidateJarSize ?: 0}",
        "candidate_jar_sha256=${candidateJarSha256 ?: "none"}",
        "proposal=${proposalId ?: "none"}"
    ).joinToString(" ")
}
