/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.endpoint

/**
 * Refuses manifests that describe an operation without constraining it.
 *
 * J009's recorded blocker was "incomplete manifest", and a manifest type alone
 * does not fix that — a record with ten blank strings satisfies the type and
 * documents nothing. The registry would look complete while every field was
 * empty, which is the same failure as before with more ceremony.
 *
 * Two of these rules are not mere completeness checks and are worth naming:
 *
 *  - **Retry requires no side effects.** Retrying an [EndpointSideEffect.NONE]
 *    operation repeats a read. Retrying anything else repeats the effect — a
 *    duplicate push, a second jar swap. Callers cannot be relied on to notice
 *    the combination at each declaration site, so it is refused here once.
 *  - **A declared test path is required.** An operation whose manifest names no
 *    test is an operation nothing proves, and the manifest would otherwise be a
 *    promise with no evidence behind it.
 */
class EndpointManifestValidator {

    fun validate(manifest: EndpointManifest): EndpointManifestValidation {
        val problems = buildList {
            if (manifest.id.isBlank()) add("id is blank")
            if (manifest.owner.isBlank()) add("owner is blank")
            if (manifest.input.isBlank()) add("input is undeclared")
            if (manifest.output.isBlank()) add("output is undeclared")
            if (manifest.errors.isEmpty() || manifest.errors.any(String::isBlank)) {
                // "This cannot fail" is a claim almost no operation can support;
                // requiring the list forces the author to name what does happen.
                add("errors are undeclared")
            }
            if (manifest.timeoutMillis <= 0L) add("timeout must be positive")
            if (manifest.retry.maxAttempts < 1) add("retry maxAttempts must be at least 1")
            if (manifest.retry.backoffMillis < 0L) add("retry backoff must not be negative")
            if (manifest.retry.retries && manifest.sideEffects != EndpointSideEffect.NONE) {
                add("retry is unsafe for side effect ${manifest.sideEffects}")
            }
            if (manifest.tests.isEmpty() || manifest.tests.any(String::isBlank)) {
                add("no test is declared for this endpoint")
            }
        }
        return EndpointManifestValidation(manifest.id, problems)
    }

    fun validateAll(manifests: List<EndpointManifest>): List<EndpointManifestValidation> {
        val duplicates = manifests.groupBy { it.id }.filterValues { it.size > 1 }.keys
        return manifests.map { manifest ->
            val base = validate(manifest)
            if (manifest.id in duplicates) {
                // Two manifests for one id means callers cannot know which
                // contract they are bound by — the ambiguity is the defect.
                base.copy(problems = base.problems + "duplicate manifest id")
            } else {
                base
            }
        }
    }
}

data class EndpointManifestValidation(
    val id: String,
    val problems: List<String>
) {
    val complete: Boolean get() = problems.isEmpty()

    fun evidenceLine(): String =
        "endpoint_manifest id=$id complete=$complete problems=${problems.joinToString(";").ifBlank { "none" }}"
}
