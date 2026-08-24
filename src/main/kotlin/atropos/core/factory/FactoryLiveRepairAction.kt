package atropos.core.factory

import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ShellActionProposals
import atropos.core.security.RedactionFilter
import java.nio.file.Path

/**
 * The production repair callback for a factory run.
 *
 * Repair is deliberately operator-configured: the engine never invents a
 * patch command. The command is admitted by the existing agency gate, runs
 * through the bounded process owner, and returns real exit/stderr evidence to
 * the unchanged acceptance-freeze oracle.
 */
class FactoryLiveRepairAction(
    private val repoRoot: Path,
    private val command: List<String>? = repairCommand(System.getenv("ATROPOS_FACTORY_REPAIR_COMMAND")),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot)),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    operator fun invoke(
        plan: FactoryPlan,
        plannedPath: Path,
        failure: Throwable,
        freeze: FactoryAcceptanceFreeze
    ): FactoryAcceptanceFreeze.RepairEvidence {
        val argv = command ?: error(
            "factory verification failed for ${plan.id}; no repair callback is configured; " +
                "set ATROPOS_FACTORY_REPAIR_COMMAND to an argv-style bounded repair command"
        )
        val target = plannedPath.toAbsolutePath().normalize()
        require(target.startsWith(repoRoot.toAbsolutePath().normalize())) {
            "factory repair target escaped repository root"
        }
        val proposal = ShellActionProposals.forCommand(
            command = argv,
            cwd = repoRoot,
            actor = ActionActor.SystemService("factory-repair")
        ).copy(targetPaths = listOf(repoRoot.relativize(target).toString()))
        val authorization = agencyGate.evaluate(proposal)
        check(authorization.disposition == AgencyDisposition.ALLOWED) {
            "factory repair refused by policy: ${authorization.reason}"
        }
        val result = processRunner.run(
            command = argv,
            directory = repoRoot,
            timeoutMillis = 900_000L,
            maxOutputBytes = 64 * 1024,
            maxOutputLines = 4_000,
            environment = mapOf("ATROPOS_FACTORY_REPAIR_TARGET" to target.toString()),
            evidenceDirectory = repoRoot.resolve(".atropos/factory/repair").resolve(plan.id)
        )
        val stderr = redactionFilter.redact(
            result.stderr.ifBlank { "repair command produced no stderr; failure=${failure.javaClass.simpleName}" }
        )
        return FactoryAcceptanceFreeze.RepairEvidence(
            freezeSha256 = freeze.sha256,
            command = redactionFilter.redact(argv.joinToString(" ")),
            exitCode = result.exitCode ?: 1,
            stderr = stderr,
            predicateResults = mapOf(
                "repair_process_completed" to (result.launchError == null && !result.timedOut),
                "repair_process_exit_zero" to (result.exitCode == 0)
            )
        )
    }

    companion object {
        private fun repairCommand(raw: String?): List<String>? {
            val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            require(value.none { it == ';' || it == '|' || it == '&' || it == '$' || it == '`' }) {
                "ATROPOS_FACTORY_REPAIR_COMMAND must be argv-style and cannot contain shell operators"
            }
            return value.split(Regex("\\s+")).filter { it.isNotBlank() }
        }
    }
}
