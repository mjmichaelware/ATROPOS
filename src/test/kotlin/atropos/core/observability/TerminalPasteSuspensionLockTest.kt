/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalPasteSuspensionLockTest {

    @BeforeTest
    @AfterTest
    fun tearDown() {
        TerminalPasteSuspensionLock.reset()
    }

    @Test
    fun `acquire immediately suspends background updates`() {
        assertFalse(TerminalPasteSuspensionLock.isSuspended())
        assertFalse(BoundedRenderingController.isPasteSuspended())

        TerminalPasteSuspensionLock.acquire()

        assertTrue(TerminalPasteSuspensionLock.isSuspended())
        assertTrue(BoundedRenderingController.isPasteSuspended())

        var executed = false
        val updated = BoundedRenderingController.controlBackgroundUpdate(isActiveScreen = true) {
            executed = true
        }

        assertFalse(updated, "Update must be refused while paste lock is acquired")
        assertFalse(executed, "Lambda must not be invoked while paste lock is acquired")
    }

    @Test
    fun `release preserves suspension for settle duration window`() {
        TerminalPasteSuspensionLock.acquire()
        assertTrue(TerminalPasteSuspensionLock.isSuspended())

        // Release with 200ms settle duration
        TerminalPasteSuspensionLock.release(settleDurationMillis = 200L)

        // Must still be suspended during the settle duration
        assertTrue(TerminalPasteSuspensionLock.isSuspended())
        assertTrue(BoundedRenderingController.isPasteSuspended())

        var executed = false
        val updated = BoundedRenderingController.controlBackgroundUpdate(isActiveScreen = true) {
            executed = true
        }
        assertFalse(updated)
        assertFalse(executed)
    }

    @Test
    fun `controlBackgroundUpdate allows rendering when not suspended and screen is active`() {
        assertFalse(TerminalPasteSuspensionLock.isSuspended())

        var executed = false
        val updated = BoundedRenderingController.controlBackgroundUpdate(isActiveScreen = true) {
            executed = true
        }

        assertTrue(updated)
        assertTrue(executed)
    }

    @Test
    fun `reset clears suspension immediately`() {
        TerminalPasteSuspensionLock.acquire()
        assertTrue(TerminalPasteSuspensionLock.isSuspended())

        TerminalPasteSuspensionLock.reset()
        assertFalse(TerminalPasteSuspensionLock.isSuspended())
    }
}
