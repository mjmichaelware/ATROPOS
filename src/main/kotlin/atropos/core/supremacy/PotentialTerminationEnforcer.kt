package atropos.core.supremacy

data class SystemEntropyVector(
    val unverifiedNodes: Int,
    val failingDiagnostics: Int,
    val openObligations: Int,
    val cyclicDependencyCount: Int
) {
    val phi: Long get() =
        (unverifiedNodes.toLong() * 100L) +
        (failingDiagnostics.toLong() * 25L) +
        (openObligations.toLong() * 10L) +
        (cyclicDependencyCount.toLong() * 1000L)
}

class LoopOscillationHaltException(message: String, val previousPhi: Long, val currentPhi: Long) :
    IllegalStateException(message)

class PotentialTerminationEnforcer(private val maxStagnantIterations: Int = 2) {
    private var lastVector: SystemEntropyVector? = null
    private var stagnantCount: Int = 0

    fun assertMonotonicDescent(iteration: Int, current: SystemEntropyVector) {
        val previous = lastVector
        if (previous == null) {
            lastVector = current
            return
        }

        val prevPhi = previous.phi
        val currPhi = current.phi

        if (currPhi < prevPhi) {
            atropos.core.thinking.Thinking.step("autonomy", "Lyapunov potential descent confirmed: Φ=$currPhi")
            atropos.core.thinking.Thinking.detail("autonomy", "Iteration $iteration: unverified=${current.unverifiedNodes}, failing=${current.failingDiagnostics}, phi=$currPhi (delta=${currPhi - prevPhi})")
            stagnantCount = 0
            lastVector = current
            return
        }

        if (currPhi == prevPhi) {
            stagnantCount++
            if (stagnantCount >= maxStagnantIterations) {
                throw LoopOscillationHaltException(
                    "Autonomous loop stagnant: Phi failed to decrease for $stagnantCount consecutive turns (Phi=$currPhi)",
                    prevPhi,
                    currPhi
                )
            }
        } else {
            throw LoopOscillationHaltException(
                "Autonomous loop oscillating backwards: Current Phi ($currPhi) exceeded previous Phi ($prevPhi) on iteration $iteration",
                prevPhi,
                currPhi
            )
        }
        lastVector = current
    }

    fun reset() {
        lastVector = null
        stagnantCount = 0
    }
}
