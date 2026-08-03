package atropos.core.factory

import java.nio.file.Files
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppProjectGeneratorTest {
    @Test
    fun generated_repository_namespace_is_allowed_by_canonical_policy() {
        val root = Files.createTempDirectory("atropos-app-policy-")
        val target = root.resolve(".atropos/generated-projects/weather-policy-1")

        AppProjectMutationGate(root).requireAllowed(root, target)
    }

    @Test
    fun mutation_gate_rejects_targets_outside_active_repository() {
        val root = Files.createTempDirectory("atropos-app-policy-root-")
        val outside = Files.createTempDirectory("atropos-app-policy-outside-")

        assertFailsWith<IllegalArgumentException> {
            AppProjectMutationGate(root).requireAllowed(root, outside.resolve("generated"))
        }
    }

    @Test
    fun mutation_gate_rejects_a_root_different_from_its_policy_root() {
        val configuredRoot = Files.createTempDirectory("atropos-app-policy-configured-")
        val suppliedRoot = Files.createTempDirectory("atropos-app-policy-supplied-")

        assertFailsWith<IllegalArgumentException> {
            AppProjectMutationGate(configuredRoot).requireAllowed(
                suppliedRoot,
                suppliedRoot.resolve(".atropos/generated-projects/app")
            )
        }
    }

    @Test
    fun calculator_proof_uses_the_general_app_generation_api() {
        val root = Files.createTempDirectory("atropos-app-generator-")
        val generated = AppProjectGenerator(root).generateApp(
            "Build a simple calculator CLI with tests and README",
            "factory-proof-1"
        )
        val target = root.resolve(".atropos/generated-projects/calculator-factory-proof-1")

        assertEquals(target.toString(), generated.path)
        assertEquals("cli", generated.spec.intent.kind)
        assertTrue(generated.commitId.matches(Regex("[0-9a-f]{40}")))
        assertTrue(generated.branch.isNotBlank())
        assertTrue(generated.files.containsAll(listOf("README.md", "LICENSE", ".gitignore", "AGENTS.md")))
        assertTrue(Files.exists(target.resolve("src/main/kotlin/calculator/Main.kt")))
        assertTrue(Files.exists(target.resolve("src/test/kotlin/calculator/MainTest.kt")))
        val generatedAgents = Files.readString(target.resolve("AGENTS.md"))
        assertTrue("./verify.sh" in generatedAgents)
        assertTrue(".atropos/evidence/app-manifest.txt" in generatedAgents)
        assertTrue(Files.readString(target.resolve(".atropos/evidence/app-manifest.txt")).contains("verification=generated-test-and-content-shape"))
        assertTrue(Files.readString(target.resolve(".atropos/evidence/app-manifest.txt")).contains("verification_output_sha256="))
        assertTrue(Files.readString(target.resolve("verify.sh")).contains("MainTestKt"))
        val evidence = Files.readString(target.resolve(".atropos/evidence/app-manifest.txt"))
        assertTrue(evidence.contains("branch=${generated.branch}"))
        assertTrue(evidence.contains("tree_sha256=${generated.treeSha256}"))
        assertTrue(evidence.contains("project=.\n"))
        assertTrue("/data/" !in evidence)
        assertTrue(Files.exists(root.resolve(".atropos/territory/assignments.jsonl")))
        assertTrue(Files.size(root.resolve(".atropos/generated-projects/calculator-factory-proof-1.tar")) > 0)
    }

    @Test
    fun arbitrary_todo_and_notes_requests_use_the_same_general_pipeline() {
        val root = Files.createTempDirectory("atropos-app-general-")
        val generator = AppProjectGenerator(root)

        val todo = generator.generateApp("Create a todo app with tests", "todo-1")
        val notes = generator.generateApp("Make a notes CLI with README", "notes-1")
        val planned = AppProjectSpecParser().parse("Build a weather service")
        val plannedProject = generator.generateApp(planned, "planned-1")

        assertEquals("todo", todo.spec.intent.name)
        assertEquals("notes", notes.spec.intent.name)
        assertTrue(todo.files.contains("verify.sh"))
        assertTrue(notes.files.contains("verify.sh"))
        assertEquals("weather", plannedProject.spec.intent.name)
    }

    @Test
    fun generated_evidence_preserves_the_canonical_plan_and_atom_ids() {
        val root = Files.createTempDirectory("atropos-app-plan-evidence-")
        val generated = AppProjectGenerator(root).generateApp(
            AppProjectSpecParser().parse("Build a weather service"),
            "plan-evidence-1",
            planningDagId = "dag-plan-evidence-1",
            plannedAtomIds = listOf("atom-1", "atom-2")
        )

        val evidence = Files.readString(root.resolve(generated.evidencePath))
        assertEquals("dag-plan-evidence-1", generated.planningDagId)
        assertEquals(listOf("atom-1", "atom-2"), generated.plannedAtomIds)
        assertTrue("planning_dag=dag-plan-evidence-1" in evidence)
        assertTrue("planning_atoms=atom-1,atom-2" in evidence)
    }

    @Test
    fun arbitrary_names_are_safe_for_generated_kotlin() {
        val root = Files.createTempDirectory("atropos-app-safe-name-")
        val generated = AppProjectGenerator(root).generateApp("Build 2048's weather tool", "safe-1")

        assertTrue(generated.files.any { it.startsWith("src/main/kotlin/app_2048") })
        assertTrue(Files.readString(root.resolve(".atropos/generated-projects/app_2048-safe-1/src/main/kotlin/app_2048/Main.kt")).contains("println"))
    }

    @Test
    fun generated_readme_redacts_prompt_credentials() {
        val root = Files.createTempDirectory("atropos-app-redaction-")
        val generated = AppProjectGenerator(root).generateApp(
            "Build a notes CLI with api_key=sk-test-secret-value",
            "redaction-1"
        )
        val readme = Files.readString(root.resolve(generated.path).resolve("README.md"))
        val source = Files.readString(root.resolve(generated.path).resolve("src/main/kotlin/notes/Main.kt"))

        assertTrue("sk-test-secret-value" !in readme)
        assertTrue("sk-test-secret-value" !in source)
        assertTrue("redacted" in readme)
    }
}
