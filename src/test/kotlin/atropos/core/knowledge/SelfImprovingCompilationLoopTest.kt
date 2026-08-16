package atropos.core.knowledge

import atropos.core.verification.*
import atropos.core.verifier.ProbabilisticImmunityEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import atropos.core.verification.VerificationScope
import kotlin.test.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SelfImprovingCompilationLoopTest {

    @Test
    fun `reward recorder enqueues persistence without blocking the caller`() {
        val completed = CountDownLatch(1)
        val recorder = NonBlockingRewardRecorder(
            RewardRecorder { completed.countDown() }
        )
        try {
            recorder.record(RewardEvent(VerificationScope.NARROW, 1.0, 0, false, 1L))
            assertTrue(completed.await(2, TimeUnit.SECONDS))
        } finally {
            recorder.close()
        }
    }

    @Test
    fun `verification reward exposes the canonical score and stderr trace`() {
        val vector = RewardEvent(
            scope = VerificationScope.NARROW,
            reward = 0.5,
            exitCode = 1,
            timedOut = false,
            durationMillis = 25L,
            trace = "compiler: missing symbol"
        ).vector

        assertEquals(0.5, vector.score)
        assertEquals("compiler: missing symbol", vector.trace)
    }

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
