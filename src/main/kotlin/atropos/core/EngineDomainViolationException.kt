/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

/**
 * Thrown when an untrusted model proposal, shell execution, or file mutation
 * attempts to target or leak into the immutable engine authority domain (A_engine).
 * Enforces: Cap(A_engine) ∩ Cap(A_target) = ∅.
 */
class EngineDomainViolationException(
    message: String,
    cause: Throwable? = null
) : SecurityException(message, cause)
