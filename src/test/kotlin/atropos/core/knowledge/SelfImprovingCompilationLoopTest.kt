package atropos.core.knowledge

import atropos.core.verification.*
import atropos.core.verifier.ProbabilisticImmunityEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import atropos.core.verification.VerificationScope
import kotlin.test.Test

class SelfImprovingCompilationLoopTest {

    @Test
    fun `test execution verification loop`() {
        val tempDir = Files.createTempDirectory("loop-test-")
        try {
            val fakeExecutor = VerificationProcessExecutor { req ->
                ProcessExecution(
                    command = req.command,
                    exitCode = 0,
                    timedOut = false,
                    durationMillis = 10L,
                    stdout = CapturedText("success", false),
                    stderr = CapturedText("", false)
                )
            }
            
            val recorder = AtomicRewardRecorder(tempDir)
            val loop = SelfImprovingCompilationLoop(
                processExecutor = fakeExecutor,
                analyzer = ProbabilisticImmunityEngine(),
                rewardRecorder = recorder
            )

            // VerificationScope.NARROW, not a GoalScope: the latter does not
            // exist. timeoutMillis is required and has no default.
            val request = VerificationRequest(
                scope = VerificationScope.NARROW,
                command = listOf("echo", "success"),
                workspace = tempDir,
                timeoutMillis = 30_000
            )

            val result = loop.executeVerification(request)
            assertNotNull(result)
            assertEquals(1.0, result.reward)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
