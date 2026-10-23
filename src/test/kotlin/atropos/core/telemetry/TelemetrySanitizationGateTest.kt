/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetrySanitizationGateTest {

    @Test
    fun `test redaction of home directories, emails, and tokens`() {
        val gate = TelemetrySanitizationGate()
        val raw = "User /home/developer/project with email admin@example.com and token ghp_12345678901234567890 accessed 192.168.1.100"

        val sanitized = gate.sanitize(raw)

        assertFalse(sanitized.contains("/home/developer"))
        assertFalse(sanitized.contains("admin@example.com"))
        assertFalse(sanitized.contains("ghp_12345678901234567890"))
        assertFalse(sanitized.contains("192.168.1.100"))

        assertTrue(sanitized.contains("[REDACTED_HOME]"))
        assertTrue(sanitized.contains("[REDACTED_EMAIL]"))
        assertTrue(sanitized.contains("[REDACTED_IP]"))
    }
}
