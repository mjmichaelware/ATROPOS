package atropos.core.provider

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class SubTerritoryTask(val workerId: String, val providerId: String, val prompt: String, val assignedPrefix: String)
data class WorkerPatchOutcome(val workerId: String, val patchDiff: String, val touchedFiles: List<String>, val success: Boolean)
data class DisjointMergeReport(val mergedPatches: List<WorkerPatchOutcome>, val rejectedPatches: List<WorkerPatchOutcome>, val cleanMerge: Boolean)

class ParallelProviderWorkerDispatcher(private val threadPoolSize: Int = 4) {
    fun assertDisjointTerritories(tasks: List<SubTerritoryTask>) {
        val prefixes = tasks.map { it.assignedPrefix.replace('\\', '/').trim().trimEnd('/') + "/" }
        for (i in prefixes.indices) {
            for (j in i + 1 until prefixes.size) {
                val p1 = prefixes[i]
                val p2 = prefixes[j]
                if (p1.startsWith(p2) || p2.startsWith(p1)) {
                    throw IllegalStateException("Territory Collision: Workers have overlapping scopes: [$p1] vs [$p2]")
                }
            }
        }
    }

    fun dispatchParallel(
        tasks: List<SubTerritoryTask>,
        workerInvoker: (SubTerritoryTask) -> WorkerPatchOutcome
    ): DisjointMergeReport {
        assertDisjointTerritories(tasks)
        val executor = Executors.newFixedThreadPool(minOf(tasks.size, threadPoolSize))
        val futures = tasks.map { task ->
            executor.submit(Callable { workerInvoker(task) })
        }
        executor.shutdown()
        executor.awaitTermination(60, TimeUnit.SECONDS)

        val outcomes = futures.map { it.get() }
        val merged = mutableListOf<WorkerPatchOutcome>()
        val rejected = mutableListOf<WorkerPatchOutcome>()
        val claimedFiles = mutableSetOf<String>()

        for (outcome in outcomes) {
            val collides = outcome.touchedFiles.any { it in claimedFiles }
            if (outcome.success && !collides) {
                claimedFiles.addAll(outcome.touchedFiles)
                merged.add(outcome)
            } else {
                rejected.add(outcome)
            }
        }

        return DisjointMergeReport(merged, rejected, rejected.isEmpty())
    }
}
