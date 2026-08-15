/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class SecretEgressGateTest {

    @AfterTest
    fun tearDown() {
        SecretEgressGate.clearCanaries()
    }

    @Test
    fun `detects registered canary matches in outputs`() {
        SecretEgressGate.registerCanary("AIzaSyDummySecretKey123")
        assertTrue(SecretEgressGate.hasSecretLeak("Log info: key is AIzaSyDummySecretKey123 in standard output"))
        assertFalse(SecretEgressGate.hasSecretLeak("Clean log output with no keys"))
    }
    
    @Test
    fun `scan returns violation objects with exact pattern name`() {
        SecretEgressGate.registerCanary("AWS_SECRET", "AwsKeyPattern")
        val violations = SecretEgressGate.scan("Testing leaking AWS_SECRET token")
        
        assertEquals(1, violations.size)
        assertEquals("AwsKeyPattern", violations[0].patternName)
        assertTrue(violations[0].matchedFragment.contains("AwsKeyPattern"))
    }
    
    @Test
    fun `multiple canaries detect independent leaks`() {
        SecretEgressGate.registerCanary("SECRET_A", "PatternA")
        SecretEgressGate.registerCanary("SECRET_B", "PatternB")
        
        val violations = SecretEgressGate.scan("Leaking SECRET_B here")
        assertEquals(1, violations.size)
        assertEquals("PatternB", violations[0].patternName)
    }
}
