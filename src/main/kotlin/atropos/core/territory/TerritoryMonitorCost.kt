/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.territory

import java.util.concurrent.atomic.AtomicLong

/**
 * Counts what territory monitoring actually costs.
 *
 * `SUP.VERIF.TERRITORY-MONITOR-COST` claims hierarchical territory checking is
 * O(N) in node count where a flat bag-of-agents is O(N²), and requires the
 * claim to be carried in evidence rather than asserted in prose. An
 * uninstrumented claim is a marketing sentence; this makes it falsifiable.
 *
 * The counter records checks performed, not time elapsed. Wall-clock on a
 * phone under thermal throttling says more about the phone than the algorithm,
 * whereas a check count is the thing the complexity claim is actually about and
 * is reproducible across machines.
 *
 * Nothing here participates in a decision. A counter that could refuse a check
 * would be a policy, and policy belongs to the gate.
 */
class TerritoryMonitorCost {
    private val checks = AtomicLong(0)
    private val nodes = AtomicLong(0)

    /** Records one containment check against one node's grants. */
    fun recordCheck(nodeCount: Int) {
        checks.incrementAndGet()
        nodes.updateAndGet { maxOf(it, nodeCount.toLong()) }
    }

    fun snapshot(): TerritoryCostSnapshot =
        TerritoryCostSnapshot(checks = checks.get(), peakNodes = nodes.get())

    fun reset() {
        checks.set(0)
        nodes.set(0)
    }
}

data class TerritoryCostSnapshot(
    val checks: Long,
    val peakNodes: Long
) {
    /**
     * Checks per node.
     *
     * The number the complexity claim lives or dies on. Under a hierarchy this
     * stays bounded as nodes grow; under a flat model where every node is
     * compared against every other it rises with N, and the ratio is what makes
     * the difference visible without a benchmark harness.
     */
    val checksPerNode: Double
        get() = if (peakNodes == 0L) 0.0 else checks.toDouble() / peakNodes.toDouble()

    /**
     * True when observed cost is consistent with the linear claim.
     *
     * Deliberately a wide bound. This is a regression guard against the shape
     * silently becoming quadratic, not a proof of a constant factor — a tight
     * threshold would fail on ordinary scheduling noise and teach everyone to
     * ignore it.
     */
    fun isLinearShaped(bound: Double = LINEAR_BOUND): Boolean =
        peakNodes == 0L || checksPerNode <= bound

    fun render(): String =
        "territory_monitor checks=$checks nodes=$peakNodes perNode=%.2f linear=%s"
            .format(checksPerNode, isLinearShaped())

    companion object {
        /** Generous: quadratic growth clears this long before the bound is tight. */
        const val LINEAR_BOUND = 8.0
    }
}
