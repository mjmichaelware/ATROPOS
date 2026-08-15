// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.time

import java.time.Instant

/**
 * Clock interface for dependency injection.
 */
interface SystemClock {
    fun now(): Instant
}

/**
 * Real system clock.
 */
class RealClock : SystemClock {
    override fun now(): Instant = Instant.now()
}

/**
 * Settable test clock.
 */
class TestClock(private var currentTime: Instant = Instant.now()) : SystemClock {
    override fun now(): Instant = currentTime

    fun setTime(time: Instant) {
        currentTime = time
    }
}
