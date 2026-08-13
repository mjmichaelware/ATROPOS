/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * Decides what a single token *means*, so [SemanticLineColorizer] only has to
 * decide where tokens begin and end.
 *
 * Split out because the two questions fail differently. Shape errors tear a
 * sentence into a fake key and value; meaning errors paint a passing run red.
 * Keeping them in one file meant a change to either could quietly break the
 * other, and the second failure is the one an operator acts on.
 *
 * Two rules do most of the work here, and both come from output that was being
 * mis-painted:
 *
 * 1. **In `key=value`, the value carries the meaning.** `rejected=15` is a
 *    count on a healthy line, not a rejection; classifying the whole token made
 *    `st_memory=PASS scoped_hits=1 rejected=15` render as a failure. The key is
 *    a label and is painted as one.
 * 2. **Markers match whole segments, never substrings.** `SKIPPED_SOFT_FAIL`
 *    contains `FAIL`, and substring matching made every soft fail look like a
 *    hard one — flattening the single distinction an operator most needs. A
 *    token is segmented both with underscores kept and with them split, so
 *    `NOT_REQUIRED` stays one word while `SKIPPED_SOFT_FAIL` still yields
 *    `SKIPPED`.
 */
object SemanticTokenClassifier {

    /** The role for a value token, ignoring any `key=` prefix it carries. */
    fun roleFor(value: String): Role {
        val segments = segmentsOf(value)
        return when {
            // Degraded is tested before failure on purpose: a soft fail carries
            // the word FAIL and a run that merely degraded is not a run that
            // broke. A wall of red says they are the same event.
            segments.any { it in DEGRADED } -> Role.STATUS_PENDING
            segments.any { it in FAILURE } -> Role.STATUS_ERROR
            segments.any { it in SUCCESS } -> Role.STATUS_VERIFIED
            segments.any { it in UNSET } -> Role.TEXT_MUTED
            looksLikePath(value) -> Role.PATH
            looksLikeDigest(value) -> Role.TEXT_MUTED
            looksNumeric(value) -> Role.INFO
            else -> Role.TEXT_PRIMARY
        }
    }

    /** True when a token reads as `key=value` rather than a bare word. */
    fun isAssignment(token: String): Boolean {
        val split = token.indexOf('=')
        return split > 0 && split < token.length - 1
    }

    fun assignmentKey(token: String): String = token.substring(0, token.indexOf('=') + 1)

    fun assignmentValue(token: String): String = token.substring(token.indexOf('=') + 1)

    /**
     * The words inside a token, with underscore-joined forms kept alongside
     * their parts so both `NOT_REQUIRED` and `SKIPPED` can be recognised in the
     * same pass.
     */
    private fun segmentsOf(value: String): Set<String> {
        val upper = value.uppercase()
        val kept = upper.split(NON_WORD_KEEPING_UNDERSCORE).filter { it.isNotEmpty() }
        val split = upper.split(NON_WORD).filter { it.isNotEmpty() }
        return (kept + split).toSet()
    }

    private fun looksLikePath(value: String): Boolean =
        value.contains('/') && !value.contains(' ') && value.length > 3

    /** A hash or an opaque id: present for provenance, not for reading. */
    private fun looksLikeDigest(value: String): Boolean =
        value.length >= 12 && value.none { it.isWhitespace() } &&
            value.count { it.isDigit() } >= 4 &&
            value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private fun looksNumeric(value: String): Boolean =
        value.isNotEmpty() && value.all { it.isDigit() || it == '.' || it == '%' } &&
            value.any { it.isDigit() }

    private val NON_WORD = Regex("[^A-Z0-9]+")
    private val NON_WORD_KEEPING_UNDERSCORE = Regex("[^A-Z0-9_]+")

    private val FAILURE = setOf(
        "FAIL", "FAILED", "FAILING", "FAILURE", "FAILURES",
        "ERROR", "ERRORS", "REFUSED", "REFUSAL", "BLOCKED",
        "VIOLATION", "VIOLATIONS", "REJECTED", "DENIED", "ABORTED", "BROKEN"
    )

    private val DEGRADED = setOf(
        "DEGRADED", "SKIPPED", "SKIP", "SOFT", "WARN", "WARNING",
        "PARTIAL", "PENDING", "MISS", "MISSED", "RETRY", "STALE"
    )

    private val SUCCESS = setOf(
        "PASS", "PASSED", "VERIFIED", "COMPLETE", "COMPLETED", "GRANTED",
        "HIT", "HITS", "OK", "SUCCEEDED", "SUCCESS", "CLEAN", "READY"
    )

    private val UNSET = setOf(
        "UNCONFIGURED", "UNSET", "NOT_REQUIRED", "UNRECORDED", "NONE", "ABSENT", "DISABLED"
    )
}
