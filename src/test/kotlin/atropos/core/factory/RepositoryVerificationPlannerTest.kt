package atropos.core.factory

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryVerificationPlannerTest {
    @Test
    fun selects_the_native_manifest_command_without_falling_back_to_kotlin() {
        val root = Files.createTempDirectory("atropos-verification-plan-")
        Files.writeString(root.resolve("pyproject.toml"), "[project]\nname='sample'\n")

        val plan = RepositoryVerificationPlanner().plan(root)

        assertEquals(listOf("python3", "-m", "pytest"), plan.commands.single().argv)
        assertTrue(!plan.noKnownToolchain)
    }

    @Test
    fun unknown_repository_is_not_reported_as_verified() {
        val root = Files.createTempDirectory("atropos-verification-plan-")

        val plan = RepositoryVerificationPlanner().plan(root)

        assertTrue(plan.noKnownToolchain)
        assertTrue(plan.commands.isEmpty())
    }

    @Test
    fun generated_repository_verifier_is_the_authoritative_command() {
        val root = Files.createTempDirectory("atropos-verification-plan-")
        Files.writeString(root.resolve("verify.sh"), "#!/usr/bin/env sh\nexit 0\n")

        val plan = RepositoryVerificationPlanner().plan(root)

        assertEquals(listOf("sh", "verify.sh"), plan.commands.single().argv)
    }
}
