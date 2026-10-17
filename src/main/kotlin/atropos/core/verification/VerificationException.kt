/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

/**
 * Thrown when a verification gate or oracle check fails (e.g., Mutation score < 0.85,
 * false green tests, vacuous assertions, or invariant proof failures).
 */
class VerificationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
