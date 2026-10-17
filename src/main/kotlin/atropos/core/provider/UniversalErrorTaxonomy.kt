/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Universal error category taxonomy for deterministic context-aware provider routing.
 */
enum class UniversalErrorCategory {
    /** Context or token bounds exceeded (HTTP 413 or 400 with context/token limits). */
    CONTEXT_EXCEEDED,

    /** Rate limit hit (HTTP 429) or upstream server overload (HTTP 503). */
    RATE_LIMIT_OVERLOAD,

    /** Dead key or authentication rejection (HTTP 401 or 403). */
    AUTH_DEAD_KEY,

    /** Network timeouts, connection drops, broken pipes or connect exceptions. */
    NETWORK_TIMEOUT_DROP,

    /** Unclassified or generic error. */
    UNKNOWN
}

/**
 * Structured universal error extracted from raw network exceptions or HTTP JSON bodies.
 */
data class ParsedUniversalError(
    val category: UniversalErrorCategory,
    val message: String,
    val errorType: String? = null,
    val httpCode: Int? = null,
    val raw: String = ""
)

/**
 * Deterministic routing actions based on error taxonomy.
 */
enum class RoutingAction {
    /** Instantly route to a known high-context frontier model without wasting retries on low-context models. */
    ROUTE_HIGH_CONTEXT,

    /** Shift to the next available API key in the loaded configuration arrays. */
    ROTATE_KEY,

    /** Shift to the next available parallel provider in the fallback chain. */
    NEXT_PARALLEL_PROVIDER,

    /** Execute a standard reliable fallback. */
    RELIABLE_FALLBACK,

    /** All configured API keys and providers across loaded arrays are exhausted. */
    PIPELINE_EXHAUSTED
}

/**
 * Deterministic context-aware routing decision.
 */
data class RoutingDecision(
    val action: RoutingAction,
    val targetProvider: String? = null,
    val targetKey: String? = null,
    val reason: String
)
