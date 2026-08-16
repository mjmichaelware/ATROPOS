/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CheckpointChipTest {
    @Test
    fun CheckpointChip_formats_actions_without_changing_the_action_id() {
        assertNotNull(::CheckpointChip)
        assertEquals("Resume now", checkpointActionLabel("resume_now"))
        assertEquals("Next", checkpointActionLabel("next"))
    }
}
