package atropos.core.factory

import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.PolicyActionClass
import atropos.core.AtroposRepoRootLocator
import atropos.core.director.DirectorService
import atropos.core.director.DirectorStore
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.territory.TerritoryGrantService
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import java.nio.file.Path

/** Adapts the existing agency policy to the one bounded new-repository root. */
class AppProjectMutationGate(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val agency: BoundedAgencyGate = localAgency(repoRoot)
) : AppProjectMutationAuthorizer {
    override fun requireAllowed(repoRoot: Path, target: Path) {
        val root = repoRoot.toAbsolutePath().normalize()
        val configuredRoot = this.repoRoot.toAbsolutePath().normalize()
        require(root == configuredRoot) { "app project mutation root does not match configured policy root" }
        val normalizedTarget = target.toAbsolutePath().normalize()
        require(normalizedTarget.startsWith(root)) { "app project mutation escaped repository root" }
        val relative = root.relativize(normalizedTarget).toString()
        val decision = agency.evaluate(
            ActionProposal(
                id = "factory-mutation-${target.fileName}",
                actionClass = PolicyActionClass.FILE_MUTATION,
                actor = ActionActor.HumanOwner,
                targetPaths = listOf(relative),
                metadata = mapOf("owner" to "app-factory", "territory" to ".atropos/generated-projects")
            )
        )
        require(decision.disposition == AgencyDisposition.ALLOWED) {
            "app project mutation refused: ${decision.reason}"
        }
    }

    private companion object {
        fun localAgency(repoRoot: Path): BoundedAgencyGate {
            val root = repoRoot.toAbsolutePath().normalize()
            val director = DirectorService(DirectorStore(root), root)
            val territory = TerritoryGrantService(
                service = TerritoryService(TerritoryStore(root), director),
                rootPrefix = ".atropos/generated-projects"
            )
            return BoundedAgencyGate(
                policyEngine = ExecutionPolicyEngine(root),
                territory = territory
            )
        }
    }
}
