package com.atropos.android.app.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThinkingParserTest {
    @Test
    fun preserves_depth_lines_and_expandability() {
        val thinking = ThinkingParser.parse(
            "{\"present\":true,\"depth\":1,\"hasMore\":true,\"lines\":[{\"text\":\"outline\"}]}"
        )
        assertEquals(1, thinking?.depth)
        assertEquals(listOf("outline"), thinking?.lines)
        assertTrue(thinking?.hasMore == true)
    }
}
