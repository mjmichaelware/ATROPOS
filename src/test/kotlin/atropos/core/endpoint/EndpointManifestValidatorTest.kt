/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.endpoint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * J009's blocker was "incomplete manifest". A manifest type alone does not close
 * that — a record of ten blank strings satisfies the type and constrains nothing.
 */
class EndpointManifestValidatorTest {

    private val validator = EndpointManifestValidator()

    private fun manifest(
        id: String = "tool.git.status",
        owner: String = "atropos.core.worktree.BoundedGitWorktreeCommandRunner",
        input: String = "repository path",
        output: String = "porcelain status lines",
        errors: List<String> = listOf("NONZERO_EXIT", "TIMEOUT"),
        auth: EndpointAuth = EndpointAuth.NONE,
        sideEffects: EndpointSideEffect = EndpointSideEffect.NONE,
        timeoutMillis: Long = 60_000,
        retry: EndpointRetryPolicy = EndpointRetryPolicy.NONE,
        tests: List<String> = listOf("SelfHostGitStatusEvidenceTest")
    ) = EndpointManifest(id, owner, input, output, errors, auth, sideEffects, timeoutMillis, retry, tests)

    @Test
    fun a_fully_declared_manifest_is_complete() {
        val validation = validator.validate(manifest())

        assertTrue(validation.complete, validation.problems.toString())
        assertEquals("endpoint_manifest id=tool.git.status complete=true problems=none", validation.evidenceLine())
    }

    @Test
    fun every_required_field_is_actually_required() {
        // Each of these would previously have been describable and uncheckable.
        assertProblem(manifest(id = ""), "id is blank")
        assertProblem(manifest(owner = "  "), "owner is blank")
        assertProblem(manifest(input = ""), "input is undeclared")
        assertProblem(manifest(output = ""), "output is undeclared")
        assertProblem(manifest(errors = emptyList()), "errors are undeclared")
        assertProblem(manifest(timeoutMillis = 0), "timeout must be positive")
        assertProblem(manifest(tests = emptyList()), "no test is declared for this endpoint")
    }

    @Test
    fun a_blank_entry_inside_a_list_does_not_count_as_a_declaration() {
        assertProblem(manifest(errors = listOf("")), "errors are undeclared")
        assertProblem(manifest(tests = listOf("   ")), "no test is declared for this endpoint")
    }

    @Test
    fun retry_is_refused_for_anything_with_side_effects() {
        // The rule that is not mere completeness: retrying a read repeats a read,
        // retrying a push repeats a push.
        listOf(
            EndpointSideEffect.LOCAL_WRITE,
            EndpointSideEffect.EXTERNAL_CALL,
            EndpointSideEffect.SELF_MUTATION
        ).forEach { effect ->
            val validation = validator.validate(
                manifest(sideEffects = effect, retry = EndpointRetryPolicy(maxAttempts = 3, backoffMillis = 100))
            )
            assertFalse(validation.complete, "retry must be refused for $effect")
            assertTrue(validation.problems.any { it.contains("retry is unsafe") }, validation.problems.toString())
        }
    }

    @Test
    fun retry_is_allowed_when_the_operation_has_no_side_effects() {
        val validation = validator.validate(
            manifest(sideEffects = EndpointSideEffect.NONE, retry = EndpointRetryPolicy(maxAttempts = 3))
        )

        assertTrue(validation.complete, validation.problems.toString())
    }

    @Test
    fun a_single_attempt_is_not_a_retry_and_stays_legal_with_side_effects() {
        val validation = validator.validate(
            manifest(sideEffects = EndpointSideEffect.SELF_MUTATION, retry = EndpointRetryPolicy.NONE)
        )

        assertTrue(validation.complete, validation.problems.toString())
        assertFalse(EndpointRetryPolicy.NONE.retries)
    }

    @Test
    fun malformed_retry_policies_are_rejected() {
        assertProblem(manifest(retry = EndpointRetryPolicy(maxAttempts = 0)), "retry maxAttempts must be at least 1")
        assertProblem(
            manifest(retry = EndpointRetryPolicy(maxAttempts = 1, backoffMillis = -1)),
            "retry backoff must not be negative"
        )
    }

    @Test
    fun two_manifests_for_one_id_are_both_flagged() {
        // Callers otherwise cannot know which contract binds them.
        val validations = validator.validateAll(listOf(manifest(), manifest()))

        assertEquals(2, validations.size)
        assertTrue(validations.all { !it.complete })
        assertTrue(validations.all { v -> v.problems.any { it == "duplicate manifest id" } })
    }

    @Test
    fun distinct_ids_are_not_flagged_as_duplicates() {
        val validations = validator.validateAll(listOf(manifest(id = "cli.help"), manifest(id = "cli.status")))

        assertTrue(validations.all { it.complete }, validations.toString())
    }

    private fun assertProblem(manifest: EndpointManifest, expected: String) {
        val validation = validator.validate(manifest)
        assertFalse(validation.complete, "expected a problem: $expected")
        assertTrue(
            validation.problems.any { it == expected },
            "expected \"$expected\" among ${validation.problems}"
        )
    }
}
