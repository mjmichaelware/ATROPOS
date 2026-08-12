/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import com.atropos.android.app.bridge.MobileThinking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThinkingSheetTest {
    @Test
    fun ThinkingSheet_discloses_at_most_three_levels() {
        assertEquals(2, nextThinkingDepth(MobileThinking(1, true, emptyList())))
        assertEquals(3, nextThinkingDepth(MobileThinking(3, true, emptyList())))
        assertNull(nextThinkingDepth(MobileThinking(2, false, emptyList())))
    }
}
