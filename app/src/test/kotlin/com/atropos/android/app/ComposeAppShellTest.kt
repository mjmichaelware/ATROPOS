/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app

import com.atropos.android.app.ui.ChatListEntry
import com.atropos.android.app.ui.SessionTabModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ComposeAppShellTest {
    @Test
    fun ComposeAppShell_keeps_engine_sessions_as_the_client_shell_model() {
        assertNotNull(MainActivity::class)
        val model = SessionTabModel().replace(listOf(ChatListEntry("s1", "Notes", "now")))
        assertEquals("s1", model.explicitResume())
    }
}
