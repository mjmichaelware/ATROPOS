/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Mathematical Invariant: Exact-hash restart continuity
 *
 * Terminal bracketed paste atomic suspension lock.
 *
 * Prevents race conditions during interactive terminal input where background
 * polling loops (`stty size` geometry probing, `git status` worktree monitoring)
 * or screen redraws interrupt bracketed paste escape sequences (\e[200~ and \e[201~)
 * or swallow subsequent Enter keys.
 */
object TerminalPasteSuspensionLock {
    private val suspended = AtomicBoolean(false)
    private val suspendedAt = AtomicLong(0L)
    private val settledUntil = AtomicLong(0L)

    private const val MAX_SUSPENSION_MILLIS = 30_000L

    /**
     * Atomically acquires the paste suspension lock when a bracketed paste
     * start sequence (\e[200~) is detected.
     */
    fun acquire() {
        suspended.set(true)
        suspendedAt.set(System.currentTimeMillis())
        settledUntil.set(Long.MAX_VALUE)
    }

    /**
     * Atomically transitions the lock to settled state after bracketed paste
     * end sequence (\e[201~) is parsed. Background polling remains suspended
     * for [settleDurationMillis] to allow trailing carriage return / Enter key
     * and tty input buffers to settle cleanly.
     */
    fun release(settleDurationMillis: Long = 150L) {
        val now = System.currentTimeMillis()
        settledUntil.set(now + settleDurationMillis)
        suspended.set(false)
        suspendedAt.set(0L)
    }

    /**
     * True when bracketed paste is actively receiving or settling.
     */
    fun isSuspended(): Boolean {
        if (suspended.get()) {
            val start = suspendedAt.get()
            if (start > 0L && System.currentTimeMillis() - start > MAX_SUSPENSION_MILLIS) {
                suspended.set(false)
                suspendedAt.set(0L)
                settledUntil.set(0L)
                return false
            }
            return true
        }
        val until = settledUntil.get()
        if (until == 0L) return false
        val now = System.currentTimeMillis()
        if (now < until) return true
        settledUntil.compareAndSet(until, 0L)
        return false
    }

    /**
     * Clears any active suspension lock.
     */
    fun reset() {
        suspended.set(false)
        suspendedAt.set(0L)
        settledUntil.set(0L)
    }
}
