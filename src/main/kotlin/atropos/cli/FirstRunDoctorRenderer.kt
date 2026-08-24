/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.HomeStateProvider

/** Non-interactive first-run report composed from the existing doctor and home-state owners. */
class FirstRunDoctorRenderer(
    private val backendDoctor: BackendDoctor,
    private val homeState: HomeStateProvider
) {
    fun render(activeProvider: String = "unknown"): List<String> {
        val answers = homeState.capture(activeProvider).answers
        return buildList {
            add("ATROPOS FIRST-RUN DOCTOR")
            add("six_answers:")
            add("  objective=${answers.objective.value}")
            add("  doing=${answers.doing.value}")
            add("  why=${answers.why.value}")
            add("  progress=${answers.progress.value}")
            add("  next=${answers.next.value}")
            add("  evidence=${answers.evidence.value}")
            addAll(backendDoctor.render())
        }
    }
}
