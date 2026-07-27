/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import java.nio.file.Path
import java.util.UUID

/**
 * Builds [ActionProposal]s for verification and smoke runs.
 *
 * Construction only — no verdict. The engine decides which build and smoke
 * commands may run; this file only states what is being asked for.
 *
 * Both builders reproduce the [ExecutionPolicyRequest] their callers used to
 * assemble inline. The engine restricts `BUILD_TEST` to a known launcher and
 * refuses chained or network `SMOKE` commands, so the action class carries real
 * weight and must not be swapped between them.
 */
object VerificationActionProposals {
    /** A build/test run, e.g. `./gradlew test jar --no-daemon`. */
    fun buildTest(command: List<String>, repoRoot: Path): ActionProposal =
        ActionProposal(
            id = nextId("verify"),
            actionClass = PolicyActionClass.BUILD_TEST,
            command = command,
            cwd = repoRoot.toString()
        )

    /**
     * A smoke command.
     *
     * The tokens originate as free text, which is exactly why this must be
     * proposed rather than run: it is the closest thing in the tree to raw
     * provider prose reaching a process.
     */
    fun smoke(tokens: List<String>, repoRoot: Path): ActionProposal =
        ActionProposal(
            id = nextId("smoke"),
            actionClass = PolicyActionClass.SMOKE,
            command = tokens,
            cwd = repoRoot.toString()
        )

    private fun nextId(prefix: String): String = "$prefix-" + UUID.randomUUID().toString().take(12)
}
