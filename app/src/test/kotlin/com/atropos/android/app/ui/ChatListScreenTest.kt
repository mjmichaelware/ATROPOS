/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ChatListScreenTest {
    @Test
    fun ChatListScreen_selects_only_a_known_session() {
        assertNotNull(::ChatListScreen)
        val sessions = listOf(ChatListEntry("s1", "Notes", "now"))
        assertEquals("s1", selectedChatId(sessions, "s1"))
        assertNull(selectedChatId(sessions, "missing"))
    }
}
