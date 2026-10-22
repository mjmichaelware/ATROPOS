/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.endpoint

/**
 * The full contract for one externally invocable operation (atom J009).
 *
 * [OperationEndpoint] carries only id, kind, description, and two availability
 * flags. That is enough to *list* an operation and not enough to *call* one
 * safely: nothing in it says who owns the operation, what it accepts, what it
 * returns, how it fails, whether it needs credentials, whether invoking it
 * changes state, how long it may run, or whether it may be retried. SD2 .215
 * requires all of those, and their absence is why the registry could describe
 * the surface without constraining it.
 *
 * This is a separate type rather than more fields on [OperationEndpoint] because
 * the two answer different questions and have different lifetimes. The endpoint
 * record is runtime state — is this configured, is it reachable right now — and
 * changes as providers come and go. The manifest is a static declaration that
 * changes only when the operation's contract does. Fusing them would mean a
 * probe result and a contract share a mutation path, and a liveness check could
 * silently edit a timeout.
 *
 * @param sideEffects what invoking this does to the world. [EndpointSideEffect.NONE]
 *   is a claim that the call is safe to repeat, which is what makes [retry] safe.
 */
data class EndpointManifest(
    val id: String,
    val owner: String,
    val input: String,
    val output: String,
    val errors: List<String>,
    val auth: EndpointAuth,
    val sideEffects: EndpointSideEffect,
    val timeoutMillis: Long,
    val retry: EndpointRetryPolicy,
    val tests: List<String>
)

/** What an operation needs before it may be invoked. */
enum class EndpointAuth {
    /** Callable with no credential. */
    NONE,

    /** Requires a credential from the vault; never an inline literal. */
    VAULT_SECRET,

    /** Requires the human owner's approval at call time. */
    HUMAN_OWNER
}

/** What invoking an operation changes. Ordered from safest to least safe. */
enum class EndpointSideEffect {
    /** Read-only. Safe to repeat and safe to retry. */
    NONE,

    /** Writes inside the repository or ATROPOS state. */
    LOCAL_WRITE,

    /** Leaves the machine — a provider call, a push. Cannot be un-sent. */
    EXTERNAL_CALL,

    /** Mutates the running system itself: promotion, jar swap. */
    SELF_MUTATION
}

/**
 * Whether and how an operation may be retried.
 *
 * [maxAttempts] of 1 means no retry. Retrying an operation with side effects
 * beyond [EndpointSideEffect.NONE] repeats those effects, which is why
 * [EndpointManifestValidator] refuses that combination rather than trusting each
 * declaration site to remember.
 */
data class EndpointRetryPolicy(
    val maxAttempts: Int,
    val backoffMillis: Long = 0L
) {
    val retries: Boolean get() = maxAttempts > 1

    companion object {
        val NONE = EndpointRetryPolicy(maxAttempts = 1)
    }
}
