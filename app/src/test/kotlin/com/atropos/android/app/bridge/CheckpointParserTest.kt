package com.atropos.android.app.bridge

import kotlin.test.Test
import kotlin.test.assertEquals

class CheckpointParserTest {
    @Test
    fun preserves_engine_primary_and_available_actions() {
        val checkpoint = CheckpointParser.parse(
            "{\"present\":true,\"goalId\":\"g1\",\"phase\":\"review\",\"primaryAction\":{\"id\":\"resume\"},\"actions\":[{\"id\":\"resume\"},{\"id\":\"evidence\"}]}"
        )
        assertEquals("resume", checkpoint?.primaryAction)
        assertEquals(listOf("resume", "evidence"), checkpoint?.actions)
    }
}
