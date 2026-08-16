/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

/**
 * Messages typed while the engine was unreachable, held until it comes back.
 *
 * The composer has always told the operator "Engine offline — message will
 * queue". Nothing queued it. `onSend` cleared the field before the send
 * outcome was known, and an unreachable engine produced a notice saying the
 * message was not delivered — by which point the text they typed was gone.
 * The placeholder was making a promise no code kept, which is worse than
 * refusing outright: an operator who is told their message is safe stops
 * holding it themselves.
 *
 * Pure and immutable, so the queue can be reasoned about without a running
 * engine or a Compose runtime. Order is preserved: replaying an operator's
 * turns out of order would produce a conversation they did not have.
 *
 * Deliberately in-memory. Durable across process death is a larger promise —
 * it needs a store and a decision about how long a stale message stays
 * sendable — and making a promise the code cannot keep is the exact mistake
 * this file exists to correct. [describe] therefore says "this session".
 */
data class ComposerOutbox(val pending: List<String> = emptyList()) {

    val isEmpty: Boolean get() = pending.isEmpty()

    val size: Int get() = pending.size

    /**
     * Queues [text], ignoring blank input.
     *
     * A duplicate is kept rather than collapsed. Two identical messages are
     * two things the operator said, and silently dropping the second would
     * make the transcript disagree with what they typed.
     */
    fun queue(text: String): ComposerOutbox {
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) this else copy(pending = pending + trimmed)
    }

    /**
     * Removes the message at the head, after it was accepted by the engine.
     *
     * Separate from [queue] rather than a combined "send next" so the caller
     * drains only on a confirmed delivery. A queue that dropped its head on
     * *attempting* a send would lose the message on exactly the failure it
     * exists to survive.
     */
    fun dropHead(): ComposerOutbox =
        if (pending.isEmpty()) this else copy(pending = pending.drop(1))

    /** The next message to send, or null when the queue is empty. */
    fun head(): String? = pending.firstOrNull()

    fun cleared(): ComposerOutbox = ComposerOutbox()

    /**
     * What the operator is told is waiting, or null when nothing is.
     *
     * Null rather than "0 queued": a counter that is usually zero is noise,
     * and noise is what teaches someone to stop reading the line that
     * eventually matters.
     */
    fun describe(): String? = when (pending.size) {
        0 -> null
        1 -> "1 message queued this session — it will send when the engine is reachable"
        else -> "${pending.size} messages queued this session — they will send when the engine is reachable"
    }
}
