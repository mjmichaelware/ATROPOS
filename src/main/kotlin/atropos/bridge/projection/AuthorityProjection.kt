/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.auth.AttestationResult
import atropos.core.auth.CascadeResolution
import atropos.core.security.RedactionFilter

/**
 * Projects which authority is in force and whether it is intact.
 *
 * `SUP.AUTH.HASH-ATTEST` makes attestation the condition of acting at all: a
 * governing document whose bytes changed since it was recorded is an
 * instruction set nobody authorised. `SUP.AUTH.CASCADE-PRECEDENCE` adds that
 * some keys cannot be overridden by construction.
 *
 * Both facts are emitted, because a surface showing only "authority resolved"
 * would say the same thing for a clean document and a mutated one. `resolved`
 * here means *attested*, not merely found — a document present on disk with the
 * wrong hash resolves to nothing this bridge will vouch for.
 *
 * Core-key violations are named individually. A count would tell the operator
 * something is being overridden without telling them which invariant, which is
 * the only part they can act on.
 */
class AuthorityProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    fun render(
        attestations: List<AttestationResult>,
        cascade: List<CascadeResolution>
    ): String {
        val attested = attestations.filterIsInstance<AttestationResult.Attested>()
        val violations = cascade.filterIsInstance<CascadeResolution.Violation>()

        // Attested and untampered, or the bridge vouches for nothing.
        val resolved = attestations.isNotEmpty() &&
            attestations.all { it.trusted } &&
            violations.isEmpty()

        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "resolved" to JsonWriter.bool(resolved),
            // The strongest document in force, so the ribbon can name a source.
            "source" to JsonWriter.nullable(
                attested.minByOrNull { it.document.precedenceRank }?.document?.path?.let(redactionFilter::redact)
            ),
            "documents" to JsonWriter.arr(
                attestations.map { result ->
                    when (result) {
                        is AttestationResult.Attested -> JsonWriter.obj(
                            "path" to JsonWriter.str(redactionFilter.redact(result.document.path)),
                            "state" to JsonWriter.str("attested"),
                            "sha256" to JsonWriter.str(result.document.sha256),
                            "rank" to JsonWriter.num(result.document.precedenceRank),
                            /** Rank 0 is non-overridable by construction. */
                            "nonOverridable" to JsonWriter.bool(result.document.precedenceRank == 0)
                        )
                        is AttestationResult.Mismatch -> JsonWriter.obj(
                            "path" to JsonWriter.str(redactionFilter.redact(result.path)),
                            "state" to JsonWriter.str("mismatch"),
                            "detail" to JsonWriter.str(redactionFilter.redact(result.reason()))
                        )
                        is AttestationResult.Missing -> JsonWriter.obj(
                            "path" to JsonWriter.str(redactionFilter.redact(result.path)),
                            "state" to JsonWriter.str("missing"),
                            "detail" to JsonWriter.str(redactionFilter.redact("${result.path} was not found where it was recorded."))
                        )
                    }
                }
            ),
            "violations" to JsonWriter.arr(
                violations.map { violation ->
                    JsonWriter.obj(
                        "key" to JsonWriter.str(redactionFilter.redact(violation.key)),
                        "heldBy" to JsonWriter.str(redactionFilter.redact(violation.heldBy)),
                        "attemptedBy" to JsonWriter.strArr(violation.attemptedBy.map(redactionFilter::redact)),
                        "detail" to JsonWriter.str(redactionFilter.redact(violation.reason))
                    )
                }
            )
        )
    }
}
