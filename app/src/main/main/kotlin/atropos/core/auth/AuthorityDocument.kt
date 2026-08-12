/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

/**
 * A governing document and the integrity claim made about it.
 *
 * `SUP.AUTH.HASH-ATTEST`: "Authority documents receive identical integrity
 * controls as source code; silent mutation becomes detectable before any agent
 * action." Competitors treat `AGENTS.md` as mutable prose — anyone who can edit
 * the file can change what every agent believes it was told, and nothing
 * notices.
 *
 * [Attested] is the only state that permits action. `Mismatch` is not a warning:
 * a document whose bytes changed since it was recorded is an instruction set
 * nobody authorised, and proceeding on it is exactly the silent injection the
 * atom exists to make impossible.
 */
data class AuthorityDocument(
    val path: String,
    val sha256: String,
    /** Lower rank wins a conflict. Rank 0 documents are non-overridable. */
    val precedenceRank: Int
)

sealed class AttestationResult {
    data class Attested(val document: AuthorityDocument) : AttestationResult()
    data class Mismatch(
        val path: String,
        val expected: String,
        val observed: String
    ) : AttestationResult() {
        fun reason(): String =
            "$path changed since it was recorded (expected $expected, observed $observed)"
    }
    data class Missing(val path: String) : AttestationResult()

    val trusted: Boolean get() = this is Attested
}
