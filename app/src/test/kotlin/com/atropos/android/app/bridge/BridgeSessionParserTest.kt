package com.atropos.android.app.bridge

import kotlin.test.Test
import kotlin.test.assertEquals

class BridgeSessionParserTest {
    @Test
    fun parses_bridge_owned_session_rows() {
        val rows = BridgeSessionParser.parse(
            "{\"sessions\":[{\"id\":\"s1\",\"title\":\"Notes\",\"updatedAt\":\"now\"}]}"
        )
        assertEquals(1, rows.size)
        assertEquals("s1", rows.single().id)
        assertEquals("Notes", rows.single().title)
    }
}
