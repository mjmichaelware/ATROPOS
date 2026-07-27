/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dag

import atropos.core.policy.ActionProposal
import atropos.core.policy.PolicyActionClass
import atropos.core.policy.ProviderActionProposals
import java.nio.file.Path
import java.util.UUID

/**
 * Turns a DAG node into an [ActionProposal] so node execution answers to the
 * single permission authority.
 *
 * Construction only — no verdict.
 *
 * Every command-carrying node is proposed as [PolicyActionClass.SHELL] because
 * that is what actually happens: `DagExecutionService` runs each of them through
 * `sh -c`. Proposing `BUILD_TEST` for a node labelled `RUN_BUILD` would describe
 * the node's intent rather than its mechanism, and the engine would then apply
 * the launcher allowlist instead of the shell rules — letting
 * `./gradlew test && rm -rf x` through on the strength of its first token. The
 * proposal states the mechanism, so the shell rules apply.
 *
 * The payload is tokenised rather than wrapped as `["sh", "-c", payload]`: the
 * engine inspects the joined command for chaining and network tokens either way,
 * but tokenising keeps the launcher visible in `command.first()`.
 */
object DagNodeProposals {
    /**
     * @return the proposal to authorise, or `null` for actions that execute
     *   nothing at all. Returning `null` is not an allowance — the caller must
     *   only skip the gate for actions that have no side effect to gate.
     */
    fun forNode(
        action: DagNodeAction,
        actionPayload: String?,
        territory: List<String>,
        repoRoot: Path
    ): ActionProposal? = when (action) {
        DagNodeAction.CREATE_FILE,
        DagNodeAction.EDIT_FILE -> ActionProposal(
            id = nextId("dag-write"),
            actionClass = PolicyActionClass.FILE_MUTATION,
            cwd = repoRoot.toString(),
            // The engine denies a mutation with no declared targets. A node that
            // never declared its territory is exactly that case.
            targetPaths = territory
        )

        DagNodeAction.RUN_COMMAND,
        DagNodeAction.RUN_TEST,
        DagNodeAction.RUN_BUILD,
        DagNodeAction.VERIFY,
        DagNodeAction.COMPILE_GATE,
        DagNodeAction.SMOKE_GATE,
        DagNodeAction.ACCEPTANCE_GATE -> ActionProposal(
            id = nextId("dag-run"),
            actionClass = PolicyActionClass.SHELL,
            command = tokenise(actionPayload),
            cwd = repoRoot.toString(),
            targetPaths = territory
        )

        DagNodeAction.PROVIDER_CALL -> ActionProposal(
            id = nextId("dag-provider"),
            actionClass = PolicyActionClass.PROVIDER_CALL,
            cwd = repoRoot.toString(),
            providerId = DAG_PROVIDER,
            paidProvider = ProviderActionProposals.isPaid(DAG_PROVIDER)
        )

        // These execute nothing — `executeCheck` reads state and returns. There
        // is no side effect to authorise.
        DagNodeAction.POLICY_CHECK,
        DagNodeAction.SECRET_CHECK,
        DagNodeAction.TERRITORY_CHECK -> null
    }

    /** True for actions that execute nothing and therefore make no proposal. */
    fun executesNothing(action: DagNodeAction): Boolean =
        action == DagNodeAction.POLICY_CHECK ||
            action == DagNodeAction.SECRET_CHECK ||
            action == DagNodeAction.TERRITORY_CHECK

    private fun tokenise(payload: String?): List<String> =
        payload.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    /** `DagExecutionService.executeProviderCall` dispatches to this provider. */
    private const val DAG_PROVIDER = "groq"

    private fun nextId(prefix: String): String = "$prefix-" + UUID.randomUUID().toString().take(12)
}
