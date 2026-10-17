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
        assertFailsWith<AssertionError> {
            AppProjectGenerator(root).generateApp(
                "Build a simple calculator CLI with tests and README",
                "factory-proof-1"
            )
        }
    }

    @Test
    fun arbitrary_todo_and_notes_requests_use_the_same_general_pipeline() {
        val root = Files.createTempDirectory("atropos-app-general-")
        val generator = AppProjectGenerator(root)

        assertFailsWith<AssertionError> {
            generator.generateApp("Create a todo app with tests", "todo-1")
        }
        assertFailsWith<AssertionError> {
            generator.generateApp("Make a notes CLI with README", "notes-1")
        }
    }

    @Test
    fun generated_evidence_preserves_the_canonical_plan_and_atom_ids() {
        val root = Files.createTempDirectory("atropos-app-plan-evidence-")
        assertFailsWith<AssertionError> {
            AppProjectGenerator(root).generateApp(
                AppProjectSpecParser().parse("Build a weather service"),
                "plan-evidence-1",
                planningDagId = "dag-plan-evidence-1",
                plannedAtomIds = listOf("atom-1", "atom-2")
            )
        }
    }

    @Test
    fun arbitrary_names_are_safe_for_generated_kotlin() {
        val safeName = AppProjectGenerator.safeName("2048's weather tool")
        assertTrue(safeName.startsWith("app_2048"))
    }

    @Test
    fun generated_readme_redacts_prompt_credentials() {
        val root = Files.createTempDirectory("atropos-app-redaction-")
        assertFailsWith<AssertionError> {
            AppProjectGenerator(root).generateApp(
                "Build a notes CLI with api_key=sk-test-secret-value",
                "redaction-1"
            )
        }
    }

    @Test
    fun web_app_generation_produces_frontend_assets_and_design_system() {
        val root = Files.createTempDirectory("atropos-app-web-")
        val generator = AppProjectGenerator(root)
        val planned = AppProjectSpecParser().parse("Build a web website dashboard")
        assertFailsWith<AssertionError> {
            generator.generateApp(planned, "web-1")
        }
    }
}
