/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * The link vocabulary shared by [FallbackChain] and [RoutedTask].
 *
 * Source Doc 2's chains and routing matrix are written in one alphabet, and
 * several of its words are not providers: `paid_emergency` is a gate,
 * `queued` is a deferral, `manual_url_request` is a question for the operator.
 * They occupy positions in a chain and must be nameable, but a caller iterating
 * providers must not treat them as endpoints to call.
 *
 * A separate owner rather than constants on either enum, for two reasons. Kotlin
 * initialises an enum's entries before its companion, so entries cannot
 * reference companion constants at all — the constants have to live outside.
 * And both tables need the same words: `local_toolchain` roots nine of eleven
 * chains and eleven of fourteen matrix rows, and two copies of that string would
 * eventually disagree.
 */
object ChainLink {

    /**
     * Provider id 0 in the Source Doc 2 grid.
     *
     * Quota weight 0, cost mode local, and the root of eight fallback chains and
     * eleven routing rows. It was not a registered provider, which meant every
     * chain that begins locally silently began at its *second* entry — turning
     * the local-first guarantee that makes ATROPOS cheap into a preference
     * nothing enforced.
     */
    const val LOCAL_TOOLCHAIN = "local_toolchain"

    /** Provider id 29. An adapter slot that must declare cost and quota before use. */
    const val CUSTOM_USER_API = "custom_user_api"

    // -- terminal positions, not providers ------------------------------------

    /** Reachable only through an explicit unlock. Never by iterating a chain. */
    const val PAID_EMERGENCY = "paid_emergency"

    /** Work deferred until a free provider returns. */
    const val QUEUED = "queued"

    /** The chain ends and the operator is asked. */
    const val MANUAL = "manual_url_request"

    /** The chain ends and the answer is whatever is local. */
    const val LOCAL_ONLY = "local_only"

    /** The chain ends and the step is skipped. */
    const val SKIP = "skip_asset"

    /**
     * Positions that are outcomes rather than providers.
     *
     * A caller walking a chain to find something to call must skip these.
     * Iterating past [PAID_EMERGENCY] is how an unattended run spends money
     * nobody authorised, which is the mechanical failure Blueprint Phase 3's
     * "forbid accidental paid calls" is written against.
     */
    val TERMINAL: Set<String> = setOf(PAID_EMERGENCY, QUEUED, MANUAL, LOCAL_ONLY, SKIP)

    /** True when [link] names a real provider rather than an outcome. */
    fun isProvider(link: String): Boolean = link !in TERMINAL

    /** True when reaching [link] requires an explicit unlock. */
    fun requiresUnlock(link: String): Boolean = link == PAID_EMERGENCY
}
