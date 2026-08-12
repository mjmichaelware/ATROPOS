/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.autonomous.AutonomousBacklogService
import atropos.core.autonomous.AutonomousOrchestrator

/**
 * `/autonomous` — Phase 20 orchestrator control and backlog inspection.
 *
 * The bare command reports status rather than starting anything. Everything
 * that advances the loop — `tick`, `run`, `run-max` — has to be named
 * explicitly, so no unqualified keystroke can set the autonomous loop running.
 */
class AutonomousCommandHandler(
    private val orchestrator: AutonomousOrchestrator = AutonomousOrchestrator(),
    private val backlog: AutonomousBacklogService = AutonomousBacklogService()
) {
    fun handle(args: List<String>): String = when (args.firstOrNull()) {
        "init" -> "Autonomous session initialized: ${orchestrator.init().id}"
        "tick" -> orchestrator.tick()
        "run" -> orchestrator.runOnce()
        "run-max" -> orchestrator.runMax(args.getOrNull(1)?.toIntOrNull() ?: DEFAULT_RUN_MAX)
        "backlog" -> backlog()
        "repairs" -> repairs()
        "failovers" -> failovers()
        else -> orchestrator.status()
    }

    private fun backlog(): String {
        val snapshot = backlog.snapshot()
        if (snapshot.tasks.isEmpty()) return "autonomous backlog empty"
        return snapshot.tasks.joinToString("\n") {
            "  [${it.state.name}] ${it.id}: ${it.description} (${it.kind.name})"
        }
    }

    private fun repairs(): String {
        val repairs = backlog.repairHistory()
        if (repairs.isEmpty()) return "no repair records"
        return repairs.joinToString("\n") {
            "  ${it.id}: ${it.failureSignature.take(SIGNATURE_PREVIEW)} " +
                "success=${it.success} attempt=${it.attemptNumber}"
        }
    }

    private fun failovers(): String {
        val failovers = backlog.failoverHistory()
        if (failovers.isEmpty()) return "no failover events"
        return failovers.joinToString("\n") {
            "  ${it.id}: ${it.primaryProviderId} -> ${it.fallbackProviderId} success=${it.success}"
        }
    }

    private companion object {
        const val DEFAULT_RUN_MAX = 3
        const val SIGNATURE_PREVIEW = 60
    }
}
