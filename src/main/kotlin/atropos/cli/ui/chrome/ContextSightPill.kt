/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.chrome

import atropos.cli.ui.design.Health

/**
 * Content of the ContextSight pill: the one-line answer to "can I see what
 * context the provider was actually given?"
 *
 * `ContextAttestationRenderer` already answers that question in full, but only
 * on demand — inside `/agent status` or as a failure block after the fact. A
 * failure the operator has to go looking for is not visible, so the pill is the
 * always-present, always-narrow form of the same truth, pinned in the header.
 *
 * This file decides *what the pill says and which parts matter most*; it holds
 * no colours, no glyphs and no escape sequences, because the painter that owns
 * the canvas owns the ink. [Health] is reused rather than re-enumerated so the
 * pill cannot drift from the status vocabulary the rest of the UI uses.
 *
 * Field order is significance order, and it is the contract
 * [ContextSightPillLine] truncates against: state first, because "unattested"
 * is the field whose loss would change the operator's decision, and identity
 * last, because it is nearly always the same constant string.
 */
data class ContextSightPill(
    val health: Health,
    val stateLabel: String,
    val provider: String? = null,
    val contextHash: String? = null,
    val identity: String? = null
) {
    /** One labelled cell of the pill. [label] is for accessible/expanded surfaces, not the pill line. */
    data class Field(val label: String, val text: String)

    /**
     * Present fields, most significant first.
     *
     * Blank optional values are dropped here rather than rendered as empty
     * separators — a pill reading `attested · ·` implies data that does not exist.
     */
    fun fields(): List<Field> = buildList {
        add(Field("context", stateLabel.trim().ifBlank { UNKNOWN_STATE }))
        provider?.trim()?.takeIf(String::isNotBlank)?.let { add(Field("provider", it.lowercase())) }
        contextHash?.trim()?.takeIf(String::isNotBlank)?.let { add(Field("hash", shortHash(it))) }
        identity?.trim()?.takeIf(String::isNotBlank)?.let { add(Field("identity", it)) }
    }

    /** The field that must survive every truncation. */
    fun mostSignificantField(): Field = fields().first()

    /** Fits the pill through the canonical responsive header-line owner. */
    fun fitForColumns(columns: Int): ContextSightPillLine.Fitted =
        ContextSightPillLine.fitForColumns(this, columns)

    companion object {
        const val UNKNOWN_STATE = "unknown"
        private const val HASH_CELLS = 8

        private fun shortHash(hash: String): String =
            if (hash.length <= HASH_CELLS) hash else hash.take(HASH_CELLS)

        /**
         * The pill for a session where nothing has been attested yet.
         *
         * Distinct from a failure: no provider call has been made, so there is
         * nothing to trust *or* distrust. It reports [Health.UNKNOWN] rather than
         * quietly reading as healthy.
         */
        fun unattested(): ContextSightPill =
            ContextSightPill(health = Health.UNKNOWN, stateLabel = UNKNOWN_STATE)
    }
}
