/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MobileAppMviTest {
    @Test
    fun reducer_keeps_session_selection_explicit_and_queues_messages() {
        val sessions = listOf(ChatListEntry("s1", "Notes", "now"))
        val state = reduceMobileAppState(
            reduceMobileAppState(
                MobileAppState(),
                MobileAppIntent.SessionsLoaded(sessions)
            ),
            MobileAppIntent.SessionSelected("s1")
        )
        val queued = reduceMobileAppState(state, MobileAppIntent.MessageQueued("hello"))

        assertEquals("s1", queued.sessionTabs.explicitResume())
        assertEquals("hello", queued.outbox.head())
        assertNull(reduceMobileAppState(queued, MobileAppIntent.QueueHeadDelivered).outbox.head())
    }

    @Test
    fun offline_transition_clears_remote_facts_but_preserves_local_transcript() {
        val message = MobileMessage("m1", "pending", false, 1L)
        val state = MobileAppState(
            messages = listOf(message),
            isOnline = true,
            activeProvider = "local"
        )

        val offline = reduceMobileAppState(state, MobileAppIntent.ReachabilityChanged(false))

        assertEquals(listOf(message), offline.messages)
        assertNull(offline.activeProvider)
        assertNull(offline.answers)
        assertEquals(false, offline.isOnline)
    }
}
