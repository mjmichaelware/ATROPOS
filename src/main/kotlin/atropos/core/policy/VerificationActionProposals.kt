/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import atropos.core.factory.ProjectLanguage
import java.nio.file.Path
import java.util.UUID

/**
 * Builds [ActionProposal]s for verification and smoke runs.
 *
 * Construction only — no verdict. The engine decides which build and smoke
 * commands may run; this file only states what is being asked for.
 *
 * Both builders reproduce the [ExecutionPolicyRequest] their callers used to
 * assemble inline. Stop hardcoding `./gradlew` — routes dynamically based on
 * the target project's [ProjectLanguage].
 */
object VerificationActionProposals {
    fun defaultBuildCommand(language: ProjectLanguage): List<String> = when (language) {
        ProjectLanguage.PYTHON -> listOf("python3", "-m", "py_compile")
        ProjectLanguage.TYPESCRIPT -> listOf("npm", "run", "build")
        ProjectLanguage.GO -> listOf("go", "build", "./...")
        ProjectLanguage.RUST -> listOf("cargo", "check")
        ProjectLanguage.JAVA -> listOf("mvn", "compile")
        ProjectLanguage.KOTLIN -> listOf("./gradlew", "compileKotlin")
        else -> listOf("sh", "-c", "echo build")
    }

    fun defaultTestCommand(language: ProjectLanguage): List<String> = when (language) {
        ProjectLanguage.PYTHON -> listOf("pytest")
        ProjectLanguage.TYPESCRIPT -> listOf("npm", "test")
        ProjectLanguage.GO -> listOf("go", "test", "./...")
        ProjectLanguage.RUST -> listOf("cargo", "test")
        ProjectLanguage.JAVA -> listOf("mvn", "test")
        ProjectLanguage.KOTLIN -> listOf("./gradlew", "test")
        else -> listOf("sh", "-c", "echo test")
    }

    /** Dynamic language-aware build/test proposal. */
    fun buildTest(
        language: ProjectLanguage,
        targetDir: Path,
        actor: ActionActor,
        customCommand: List<String>? = null
    ): ActionProposal =
        ActionProposal(
            id = nextId("verify-${language.name.lowercase()}"),
            actionClass = PolicyActionClass.BUILD_TEST,
            actor = actor,
            command = customCommand ?: defaultTestCommand(language),
            cwd = targetDir.toString()
        )

    /** Shortcut helper for language-specific build/test routing. */
    fun forLanguage(language: ProjectLanguage, targetDir: Path, actor: ActionActor): ActionProposal =
        buildTest(language, targetDir, actor)

    /** A build/test run with explicit command tokens. */
    fun buildTest(command: List<String>, repoRoot: Path, actor: ActionActor): ActionProposal =
        ActionProposal(
            id = nextId("verify"),
            actionClass = PolicyActionClass.BUILD_TEST,
            actor = actor,
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
    fun smoke(
        tokens: List<String>,
        repoRoot: Path,
        actor: ActionActor = ActionActor.HumanOwner
    ): ActionProposal =
        ActionProposal(
            id = nextId("smoke"),
            actionClass = PolicyActionClass.SMOKE,
            actor = actor,
            command = tokens,
            cwd = repoRoot.toString()
        )

    private fun nextId(prefix: String): String = "$prefix-" + UUID.randomUUID().toString().take(12)
}
