package atropos.core.knowledge

import atropos.core.verification.*
import atropos.core.verifier.ProbabilisticImmunityEngine
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

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

            val request = VerificationRequest(
                scope = GoalScope.COMPILATION,
                command = listOf("echo", "success"),
                workspace = tempDir
            )

            val result = loop.executeVerification(request)
            assertNotNull(result)
            assertEquals(1.0, result.reward)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
