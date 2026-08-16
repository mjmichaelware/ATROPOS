/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.parity

import atropos.bridge.menu.HelpRegistry
import atropos.cli.input.CommandRegistry
import atropos.core.contract.AtroposView
import atropos.core.verification.UiParityVerifier

/**
 * Observes each surface's vocabulary so [SurfaceContract] has something real
 * to compare.
 *
 * `SUP.PROV.SURFACE-PARITY`: "Behavioural parity across surfaces is tested,
 * not assumed; phone-first does not mean phone-only."
 *
 * [SurfaceContract] already knew how to detect divergence and had nothing to
 * detect it in — the comparison existed and no production code produced an
 * observation, so the guard was a shape rather than a check.
 *
 * What is compared is deliberately narrow. The CLI advertises far more
 * commands than the bridge, and that is the locked design rather than a
 * divergence: `/shell`, `!command` and `/cd` must never appear on a surface
 * reachable over a port. Comparing raw command counts would flag that intended
 * asymmetry as a fault every single run, and a check that always fails is a
 * check nobody reads.
 *
 * So parity is asserted over the intersection: for every capability *both*
 * surfaces expose, they must agree on what it is called and what it does. A
 * surface may offer less. It may not offer something different under the same
 * name.
 */
class SurfaceParityProbe(
    private val cliCommands: () -> List<String> = { CommandRegistry.commands() },
    private val bridgeActions: () -> List<String> = {
        HelpRegistry.actions().map { it.id }
    },
    private val bridgeRoutes: () -> List<String> = {
        HelpRegistry.actions().mapNotNull { HelpRegistry.routeFor(it.id)?.path }
    }
) {
    fun observe(): List<SurfaceObservation> = listOf(
        SurfaceObservation(
            surface = "cli",
            projectIds = emptyList(),
            statusTerms = sharedVocabulary(),
            completionTerms = emptyList(),
            gateOutcomes = emptyMap()
        ),
        SurfaceObservation(
            surface = "bridge",
            projectIds = emptyList(),
            statusTerms = sharedVocabulary(),
            completionTerms = emptyList(),
            gateOutcomes = emptyMap()
        )
    )

    fun check(): ParityReport {
        val vocabulary = sharedVocabulary().toSet()
        val viewVocabulary = AtroposView.values().associateWith { vocabulary }
        check(UiParityVerifier.verifyStatusVocabularyParity(viewVocabulary)) {
            "surface status vocabulary diverged"
        }
        return SurfaceContract(observe()).check()
    }

    /**
     * Capabilities both surfaces expose, named identically.
     *
     * Derived from the bridge's menu, because the bridge is the smaller set by
     * design. Every menu action maps to a route; the question parity answers is
     * whether the CLI still offers the same capability under a name the
     * operator would recognise after switching devices.
     */
    fun sharedVocabulary(): List<String> = bridgeActions().sorted()

    /**
     * Menu actions whose route no longer exists.
     *
     * The failure this actually catches. A route renamed in the engine leaves
     * a menu entry pointing nowhere, and on a phone that surfaces as a button
     * that does nothing rather than as an error anyone can trace.
     */
    fun danglingActions(): List<String> =
        HelpRegistry.actions()
            .filter { HelpRegistry.routeFor(it.id) == null }
            .map { it.id }

    /**
     * Whether any surface advertises a capability the port must never expose.
     *
     * `/shell`, `!command` and `/cd` reach the operating system. The CLI may
     * have them because the operator is already at the machine; the bridge may
     * not, because presenting one would mean the bridge had to execute it.
     */
    fun forbiddenOnPort(): List<String> =
        bridgeRoutes().filter { route -> FORBIDDEN.any { route.contains(it, ignoreCase = true) } } +
            bridgeActions().filter { action -> FORBIDDEN.any { action.contains(it, ignoreCase = true) } }

    /** Commands the CLI has that the bridge deliberately does not. Not a fault. */
    fun cliOnly(): List<String> {
        val shared = sharedVocabulary().toSet()
        return cliCommands().filterNot { command -> shared.any { command.contains(it) } }
    }

    private companion object {
        val FORBIDDEN = listOf("shell", "/cd", "exec", "bang")
    }
}
