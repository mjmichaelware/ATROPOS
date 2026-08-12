/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dag

import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.security.CredentialDiffGuard
import atropos.core.territory.TerritoryGrantService
import java.nio.file.Path

/** The verdict of a check node. */
data class CheckOutcome(val passed: Boolean, val detail: String)

/**
 * Decides whether a check node passes.
 *
 * These three actions used to complete unconditionally with `"check passed"` —
 * three gates that could not fail. Each now asks a real question of an existing
 * owner: the permission authority, the credential guard, and the territory
 * grants.
 *
 * A check with nothing to inspect **fails**. A gate that cannot tell you
 * anything has not told you the thing is safe, and reporting success there is
 * how the original stub read as working.
 */
class DagNodeCheckEvaluator(
    private val repoRoot: Path,
    private val agencyGate: BoundedAgencyGate,
    private val territoryGrants: TerritoryGrantService,
    private val credentialGuard: CredentialDiffGuard = CredentialDiffGuard()
) {
    fun evaluate(node: DagNode, actor: ActionActor.HierarchyNode): CheckOutcome = when (node.action) {
        DagNodeAction.POLICY_CHECK -> policyCheck(node, actor)
        DagNodeAction.SECRET_CHECK -> secretCheck(node)
        DagNodeAction.TERRITORY_CHECK -> territoryCheck(node, actor)
        else -> CheckOutcome(false, "not a check action: ${node.action}")
    }

    /**
     * Asks the single permission authority what it would say about this node's
     * payload — the same question `executeNode` asks before running one, so a
     * check node reports the verdict the run would actually get.
     */
    private fun policyCheck(node: DagNode, actor: ActionActor.HierarchyNode): CheckOutcome {
        val payload = node.actionPayload?.trim().orEmpty()
        if (payload.isBlank()) {
            return CheckOutcome(false, "policy check has no payload to evaluate")
        }

        val proposal = DagNodeProposals.forNode(
            action = DagNodeAction.RUN_COMMAND,
            actionPayload = payload,
            territory = node.territory,
            repoRoot = repoRoot,
            actor = actor
        ) ?: return CheckOutcome(false, "policy check could not form a proposal")

        val decision = agencyGate.evaluate(proposal)
        return if (decision.disposition == AgencyDisposition.ALLOWED) {
            CheckOutcome(true, "policy allows: $payload")
        } else {
            CheckOutcome(false, "policy refused: ${decision.reason}")
        }
    }

    /**
     * Looks for credential material among the paths this node touches.
     *
     * Both the declared territory and the expected outputs are inspected: a node
     * can name a secret in either.
     */
    private fun secretCheck(node: DagNode): CheckOutcome {
        val paths = (node.territory + node.expectedOutputs)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (paths.isEmpty()) {
            return CheckOutcome(false, "secret check has no paths to inspect")
        }

        val findings = credentialGuard.inspectPaths(paths)
        return if (findings.isEmpty()) {
            CheckOutcome(true, "no credential material in ${paths.size} path(s)")
        } else {
            CheckOutcome(
                false,
                "credential material: " + findings.joinToString(", ") { "${it.path} (${it.reason})" }
            )
        }
    }

    /**
     * Confirms the node holds a grant covering everything it declared.
     *
     * This is the check half of grant-on-dispatch: `BoundedAgencyGate` refuses
     * an out-of-territory action when it happens, and this reports the same
     * fact ahead of time.
     */
    private fun territoryCheck(node: DagNode, actor: ActionActor.HierarchyNode): CheckOutcome {
        val declared = (node.territory + node.expectedOutputs)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (declared.isEmpty()) {
            return CheckOutcome(false, "territory check has no declared paths to verify")
        }

        val outside = territoryGrants.firstPathOutsideTerritory(actor, declared)
        return if (outside == null) {
            CheckOutcome(true, "all ${declared.size} declared path(s) within grant")
        } else {
            CheckOutcome(false, "outside granted territory: $outside")
        }
    }
}
