/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.nio.file.Path

enum class VerificationScope { NARROW, WIDE }
enum class DiagnosticSeverity { ERROR, WARNING, INFO }
enum class FailureClassification {
    SUCCESS, COMPILATION_ERROR, TIMEOUT, TOOL_UNAVAILABLE, PROCESS_FAILURE
}

data class VerificationRequest(
    val scope: VerificationScope,
    val workspace: Path,
    val command: List<String>,
    val timeoutMillis: Long,
    val maxOutputBytes: Int = 262_144,
    val maxOutputLines: Int = 4_000
) {
    init {
        require(command.isNotEmpty())
        require(timeoutMillis > 0)
        require(maxOutputBytes > 0)
        require(maxOutputLines > 0)
    }
}

data class CapturedText(
    val text: String,
    val truncated: Boolean
)

data class ProcessExecution(
    val command: List<String>,
    val exitCode: Int?,
    val timedOut: Boolean,
    val durationMillis: Long,
    val stdout: CapturedText,
    val stderr: CapturedText,
    val launchError: String? = null
)

data class CompilerDiagnostic(
    val path: String?,
    val line: Int?,
    val column: Int?,
    val severity: DiagnosticSeverity,
    val message: String,
    val raw: String
)

data class DiagnosticReport(
    val classification: FailureClassification,
    val diagnostics: List<CompilerDiagnostic>,
    val recommendations: List<String>,
    val riskScore: Int
)

data class RewardEvent(
    val scope: VerificationScope,
    val reward: Double,
    val exitCode: Int?,
    val timedOut: Boolean,
    val durationMillis: Long
)

data class VerificationResult(
    val request: VerificationRequest,
    val execution: ProcessExecution,
    val report: DiagnosticReport,
    val reward: Double,
    val persistenceError: String? = null
) {
    val successful: Boolean
        get() = !execution.timedOut &&
            execution.launchError == null &&
            execution.exitCode == 0
}

fun interface VerificationRunner {
    fun executeVerification(request: VerificationRequest): VerificationResult
}

fun interface RewardRecorder {
    fun record(event: RewardEvent)
}
