/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.queue

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QueueInterruptCoordinatorTest {

    @Test
    fun `executes soft and hard interrupts and freezes queue durably`() {
        val tempDir = Files.createTempDirectory("queue-coord-test")
        val coordinator = QueueInterruptCoordinator(tempDir)

        val softOk = coordinator.executeSoftInterrupt("entry-101", "operator-web")
        assertTrue(softOk)

        val hardOk = coordinator.executeHardInterrupt("entry-102", "operator-web")
        assertTrue(hardOk)

        val frozenState = coordinator.setQueueFreeze(true, "Emergency maintenance", "admin")
        assertTrue(frozenState.isFrozen)
        assertEquals("Emergency maintenance", frozenState.frozenReason)

        val readState = coordinator.readFreezeState()
        assertTrue(readState.isFrozen)
        assertEquals("Emergency maintenance", readState.frozenReason)

        val unfreeze = coordinator.setQueueFreeze(false, null, "admin")
        assertFalse(unfreeze.isFrozen)

        tempDir.toFile().deleteRecursively()
    }
}
