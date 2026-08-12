/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationScreenTest {
    @Test
    fun ConversationScreen_reports_transport_state_without_inventing_engine_data() {
        assertEquals("Ask ATROPOS anything.", conversationStatus(true))
        assertEquals("Engine not reachable", conversationStatus(false))
    }
}
