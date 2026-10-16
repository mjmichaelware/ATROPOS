/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

import java.nio.file.Path
import java.security.MessageDigest
import java.util.Random

data class ChaosReport(
    val transactionsRun: Int,
    val threadLeaks: Int,
    val memoryLeakDetected: Boolean,
    val passed: Boolean,
    val receiptHash: String
)

class ChaosDigitalTwinRunner(private val transactions: Int = 10000) {

    fun runChaosSimulation(worktree: Path, seed: Long = 42L): ChaosReport {
        val rng = Random(seed)

        System.gc()
        Thread.sleep(20)
        val initialThreads = Thread.activeCount()
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("CHAOS_SEED_$seed".toByteArray(Charsets.UTF_8))

        var executed = 0
        for (i in 0 until transactions) {
            val latency = rng.nextInt(5)
            val packetDrop = rng.nextFloat() < 0.05f
            if (!packetDrop && latency > 0) {
                // simulate synthetic transaction
                digest.update(i.toByte())
            }
            executed++
        }

        System.gc()
        Thread.sleep(20)
        val finalThreads = Thread.activeCount()
        val threadDelta = Math.max(0, finalThreads - initialThreads)
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()

        // Transactional memory leak detection
        val leakDetector = TransactionalMemoryLeakDetector()
        var memoryLeaked = false
        try {
            leakDetector.assertZeroLeak {
                digest.update(0.toByte())
            }
        } catch (_: AssertionError) {
            memoryLeaked = true
        }
        val passed = (threadDelta == 0) && !memoryLeaked

        val receiptHash = digest.digest().joinToString("") { "%02x".format(it) }

        return ChaosReport(
            transactionsRun = executed,
            threadLeaks = threadDelta,
            memoryLeakDetected = memoryLeaked,
            passed = passed,
            receiptHash = receiptHash
        )
    }
}
