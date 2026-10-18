/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MathematicalRateLimitSchedulerTest {

    @Test
    fun parses_retry_after_from_standard_and_vendor_headers() {
        val standardHeader = mapOf("Retry-After" to listOf("15"))
        assertEquals(15L, MathematicalRateLimitScheduler.parseRetryAfterSeconds(standardHeader))

        val decimalHeader = mapOf("retry-after" to listOf("8.4s"))
        assertEquals(9L, MathematicalRateLimitScheduler.parseRetryAfterSeconds(decimalHeader))

        val msHeader = mapOf("retry-after-ms" to listOf("14000"))
        assertEquals(14L, MathematicalRateLimitScheduler.parseRetryAfterSeconds(msHeader))

        val otpmHeader = mapOf("x-ratelimit-reset-requests" to listOf("22"))
        assertEquals(22L, MathematicalRateLimitScheduler.parseRetryAfterSeconds(otpmHeader))
    }

    @Test
    fun falls_back_to_body_parsing_when_headers_absent() {
        val body = """{"error":{"message":"Rate limit exceeded. Please try again in 12.5s."}}"""
        assertEquals(13L, MathematicalRateLimitScheduler.parseRetryAfterSeconds(emptyMap(), body))

        val altBody = "HTTP 429: wait 18 seconds before making another call"
        assertEquals(18L, MathematicalRateLimitScheduler.parseRetryAfterSeconds(emptyMap(), altBody))

        val fallback = MathematicalRateLimitScheduler.parseRetryAfterSeconds(emptyMap(), "Unknown 429 error")
        assertEquals(5L, MathematicalRateLimitScheduler.parseRetryAfterSeconds(emptyMap(), fallback.toString()))
    }

    @Test
    fun enforces_10_second_buffer_on_rate_limit_sleep() {
        assertEquals(25L, MathematicalRateLimitScheduler.computeSleepDurationSeconds(15L))
        assertEquals(11L, MathematicalRateLimitScheduler.computeSleepDurationSeconds(1L))

        var recordedSleepMs = 0L
        val originalSleeper = MathematicalRateLimitScheduler.sleeper
        try {
            MathematicalRateLimitScheduler.sleeper = { millis -> recordedSleepMs = millis }
            val totalMs = MathematicalRateLimitScheduler.sleepWithBuffer(12L)
            assertEquals(22_000L, totalMs)
            assertEquals(22_000L, recordedSleepMs)
        } finally {
            MathematicalRateLimitScheduler.sleeper = originalSleeper
        }
    }

    @Test
    fun calculates_accurate_ast_depth_and_valid_lines() {
        val snippet = """
            package test
            // comment line
            class Container {
                fun outer() {
                    if (true) {
                        println("deep")
                    }
                }
            }
        """.trimIndent()

        val astDepth = MathematicalRateLimitScheduler.computeAstDepth(snippet)
        assertEquals(3, astDepth)

        val validLines = MathematicalRateLimitScheduler.countValidLines(snippet)
        assertEquals(8, validLines)
    }

    @Test
    fun evaluates_mathematical_progress_delta_for_3_strike_rule() {
        val snap1 = ExecutionSnapshot(attemptNumber = 1, output = "class S {}", byteCount = 10, validLines = 1, astDepth = 1)
        val snap2NoProgress = ExecutionSnapshot(attemptNumber = 2, output = "class S {}", byteCount = 10, validLines = 1, astDepth = 1)

        val deltaNoProg = MathematicalRateLimitScheduler.computeProgress(snap1, snap2NoProgress)
        assertFalse(deltaNoProg.hasProgress)
        assertEquals(0, deltaNoProg.deltaBytes)
        assertEquals(0, deltaNoProg.deltaLines)
        assertEquals(0, deltaNoProg.deltaAstDepth)

        // Progress detected via AST depth delta
        val snap2WithAst = ExecutionSnapshot(attemptNumber = 2, output = "class S { fun run() {} }", byteCount = 25, validLines = 1, astDepth = 2)
        val deltaAst = MathematicalRateLimitScheduler.computeProgress(snap1, snap2WithAst)
        assertTrue(deltaAst.hasProgress)
        assertEquals(1, deltaAst.deltaAstDepth)
        assertTrue(deltaAst.deltaBytes > 0)

        // Progress detected via line completion delta
        val snap2WithLines = ExecutionSnapshot(attemptNumber = 2, output = "val a = 1\nval b = 2\nval c = 3", byteCount = 30, validLines = 3, astDepth = 0)
        val deltaLines = MathematicalRateLimitScheduler.computeProgress(snap1, snap2WithLines)
        assertTrue(deltaLines.hasProgress)
        assertEquals(2, deltaLines.deltaLines)
    }
}
