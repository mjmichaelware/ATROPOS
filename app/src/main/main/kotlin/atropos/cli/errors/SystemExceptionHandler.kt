/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.errors

import atropos.core.security.RedactionFilter

/**
 * The last-resort exception sink.
 *
 * This is the path that runs when every other handler has already failed, which
 * makes it the most dangerous place in the CLI for a secret to surface:
 * exception messages from HTTP clients routinely carry the request URL, an
 * `Authorization: Bearer …` header, or a key echoed back inside a provider error
 * body. It used to be `println(e.message)` — unredacted, unbounded, and on
 * stdout, where it could be piped straight into a bug report.
 *
 * Three rules hold here now, and Phase 4 depends on all three:
 * 1. Every message passes through [RedactionFilter] before it is emitted.
 * 2. Output is bounded, so a megabyte-long provider body cannot scroll the real
 *    failure off the operator's screen.
 * 3. Diagnostics go to stderr, so piping stdout never captures them.
 *
 * A null or blank message names the exception type rather than printing nothing.
 * Silence would be the worst outcome available: a crash with no stated cause.
 */
class SystemExceptionHandler(
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val sink: (String) -> Unit = { System.err.println(it) }
) {
    fun handle(e: Throwable) = sink(format(e))

    /**
     * Formats without emitting, so the redaction guarantee can be asserted in a
     * test without capturing a stream.
     */
    fun format(e: Throwable): String {
        val type = e.javaClass.simpleName.ifBlank { "Throwable" }
        val message = e.message?.takeIf { it.isNotBlank() }
            ?: return "[ERROR] $type (no message)"
        return "[ERROR] $type: ${redactionFilter.compact(message, MAX_MESSAGE_CHARS)}"
    }

    private companion object {
        const val MAX_MESSAGE_CHARS = 500
    }
}
