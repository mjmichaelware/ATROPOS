/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClipboardServiceTest {

    private val service = ClipboardService()

    @Test
    fun extractDialogText_extracts_clean_string_on_code_minus_one() {
        val json = """{"code": -1, "text": "Build a Raft consensus KV store"}"""
        val result = service.extractDialogText(json)
        assertEquals("Build a Raft consensus KV store", result)
    }

    @Test
    fun extractDialogText_returns_null_on_cancel_code_zero_or_minus_two() {
        val cancelJson = """{"code": 0, "text": ""}"""
        assertNull(service.extractDialogText(cancelJson))

        val dismissJson = """{"code": -2, "text": "abandoned"}"""
        assertNull(service.extractDialogText(dismissJson))
    }

    @Test
    fun extractDialogText_properly_unescapes_quotes_and_newlines() {
        val json = """{"code": -1, "text": "Line 1: \"KryptonStore\"\nLine 2: \\path\\to\\file\tTabbed"}"""
        val result = service.extractDialogText(json)
        val expected = "Line 1: \"KryptonStore\"\nLine 2: \\path\\to\\file\tTabbed"
        assertEquals(expected, result)
    }

    @Test
    fun extractDialogText_handles_unicode_escapes() {
        val json = """{"code": -1, "text": "Hello \u0041\u0054\u0052\u004F\u0050\u004F\u0053"}"""
        val result = service.extractDialogText(json)
        assertEquals("Hello ATROPOS", result)
    }
}
