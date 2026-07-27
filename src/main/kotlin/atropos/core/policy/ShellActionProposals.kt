/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import java.nio.file.Path
import java.util.UUID

/**
 * Builds [ActionProposal]s for shell and git invocations.
 *
 * Construction only. This file holds no policy logic, reaches no verdict, and
 * has no opinion about whether a command should run — that authority belongs to
 * [ExecutionPolicyEngine], reached through [BoundedAgencyGate]. Its single
 * responsibility is turning a command line into a proposal the gate can judge.
 *
 * The classification below reproduces exactly what `ShellCommandRunner` used to
 * pass to the engine inline, so converting the shell path to bounded agency
 * changes no command's verdict. Adding a rule here would be a second policy
 * engine by the back door.
 */
object ShellActionProposals {
    /**
     * @param command already-cleaned argv; callers strip blanks first.
     * @param cwd the directory the command would run in.
     */
    fun forCommand(command: List<String>, cwd: Path): ActionProposal =
        ActionProposal(
            id = nextId(),
            actionClass = classify(command),
            command = command,
            cwd = cwd.toString()
        )

    /** Git gets its own action class so the engine can apply its git rules. */
    fun classify(command: List<String>): PolicyActionClass =
        if (command.firstOrNull() == "git") PolicyActionClass.GIT else PolicyActionClass.SHELL

    private fun nextId(): String = "shell-" + UUID.randomUUID().toString().take(12)
}
