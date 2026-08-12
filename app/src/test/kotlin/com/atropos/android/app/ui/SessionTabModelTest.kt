package com.atropos.android.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionTabModelTest {
    @Test
    fun selects_existing_session_without_automatic_resume() {
        val model = SessionTabModel().replace(listOf(ChatListEntry("s1", "One", "now")))
        assertEquals("s1", model.activeId)
        assertEquals("s1", model.select("s1").explicitResume())
        assertEquals(model, model.select("missing"))
    }
}
