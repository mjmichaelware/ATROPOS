package atropos.core.agent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class RepositoryImpactPlannerTest {
    @Test
    fun changed_kotlin_file_includes_symbol_callers_without_scanning_build_outputs() {
        val root = Files.createTempDirectory("atropos-impact-")
        val source = root.resolve("src/main/kotlin/demo/Owner.kt")
        val caller = root.resolve("src/main/kotlin/demo/Caller.kt")
        Files.createDirectories(source.parent)
        Files.writeString(source, "package demo\nfun owner() = 1\n")
        Files.writeString(caller, "package demo\nfun caller() = owner()\n")
        Files.createDirectories(root.resolve("build/generated"))
        Files.writeString(root.resolve("build/generated/Ignore.kt"), "package demo\nfun owner() = 99\n")

        val plan = RepositoryImpactPlanner(root).plan(listOf("src/main/kotlin/demo/Owner.kt"))

        assertTrue("src/main/kotlin/demo/Owner.kt" in plan.impactedPaths)
        assertTrue(plan.impactedPaths.any { it.endsWith("Caller.kt") }, plan.toString())
        assertTrue(plan.impactedPaths.none { it.startsWith("build/") }, plan.toString())
    }

    @Test
    fun invalid_paths_are_refused_before_planning() {
        val root = Files.createTempDirectory("atropos-impact-")
        val failure = runCatching { RepositoryImpactPlanner(root).plan(listOf("../outside.kt")) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, failure.toString())
    }
}
