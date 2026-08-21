package atropos.core.factory

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Selects bounded native verification commands from repository manifests. */
class RepositoryVerificationPlanner {
    fun plan(repoRoot: Path): RepositoryVerificationPlan {
        val commands = buildList {
            when {
                Files.exists(repoRoot.resolve("verify.sh"), LinkOption.NOFOLLOW_LINKS) ->
                    add(VerificationCommand(listOf("sh", "verify.sh"), "repository verifier"))
                Files.exists(repoRoot.resolve("gradlew"), LinkOption.NOFOLLOW_LINKS) ->
                    add(VerificationCommand(listOf("./gradlew", "test"), "Gradle project"))
                Files.exists(repoRoot.resolve("pom.xml"), LinkOption.NOFOLLOW_LINKS) ->
                    add(VerificationCommand(listOf("mvn", "test"), "Maven project"))
                Files.exists(repoRoot.resolve("package.json"), LinkOption.NOFOLLOW_LINKS) ->
                    add(VerificationCommand(listOf("npm", "test"), "Node project"))
                Files.exists(repoRoot.resolve("pyproject.toml"), LinkOption.NOFOLLOW_LINKS) ->
                    add(VerificationCommand(listOf("python3", "-m", "pytest"), "Python project"))
                Files.exists(repoRoot.resolve("Cargo.toml"), LinkOption.NOFOLLOW_LINKS) ->
                    add(VerificationCommand(listOf("cargo", "test"), "Rust project"))
                Files.exists(repoRoot.resolve("go.mod"), LinkOption.NOFOLLOW_LINKS) ->
                    add(VerificationCommand(listOf("go", "test", "./..."), "Go project"))
                Files.exists(repoRoot.resolve("mix.exs"), LinkOption.NOFOLLOW_LINKS) ->
                    add(VerificationCommand(listOf("mix", "test"), "Elixir project"))
                Files.exists(repoRoot.resolve("build.sbt"), LinkOption.NOFOLLOW_LINKS) ->
                    add(VerificationCommand(listOf("sbt", "test"), "Scala project"))
                Files.exists(repoRoot.resolve("foundry.toml"), LinkOption.NOFOLLOW_LINKS) ->
                    add(VerificationCommand(listOf("forge", "test"), "Solidity project"))
                Files.exists(repoRoot.resolve("Makefile"), LinkOption.NOFOLLOW_LINKS) ->
                    add(VerificationCommand(listOf("make", "test"), "Make-based project"))
            }
        }
        return RepositoryVerificationPlan(commands, commands.isEmpty())
    }
}

data class VerificationCommand(val argv: List<String>, val reason: String)

data class RepositoryVerificationPlan(
    val commands: List<VerificationCommand>,
    val noKnownToolchain: Boolean
)
