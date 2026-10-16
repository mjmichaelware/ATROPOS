/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean

class TransactionalMemoryLeakDetector {

    private val memoryMxBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()

    fun assertZeroLeak(testBlock: () -> Unit) {
        // 1. Warmup cycles
        repeat(50) {
            testBlock()
        }

        // 2. Force GC and baseline snapshot
        System.gc()
        Thread.sleep(50)
        val baselineHeap = memoryMxBean.heapMemoryUsage.used

        // 3. Execution cycles
        repeat(1000) {
            testBlock()
        }

        // 4. Force GC and post-run snapshot
        System.gc()
        Thread.sleep(50)
        val postHeap = memoryMxBean.heapMemoryUsage.used

        // Allow 500KB threshold to accommodate background JVM classloading/JIT
        val retainedBytes = postHeap - baselineHeap
        if (retainedBytes > 500 * 1024) {
            throw AssertionError("Memory leak detected: retained $retainedBytes bytes post-warmup")
        }
    }
}
