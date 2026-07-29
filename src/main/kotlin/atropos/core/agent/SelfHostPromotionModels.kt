package atropos.core.agent

import atropos.core.artifact.JarSwapResult
import atropos.core.verification.CompletionGateReport
import java.nio.file.Path
import java.time.Instant

data class SelfHostPromotionRequest(
    val goalId: String,
    val nodeId: String? = null,
    val candidateJar: Path,
    val targetJar: Path
)

data class SelfHostPromotionResult(
    val promoted: Boolean,
    val message: String,
    val goal: SelfHostGoal?,
    val gateReport: CompletionGateReport?,
    val jarSwap: JarSwapResult?,
    val promotedAt: Instant? = null
)
