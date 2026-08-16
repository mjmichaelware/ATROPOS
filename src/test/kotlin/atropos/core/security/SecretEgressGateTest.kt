/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.nio.file.Files
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
        assertEquals(SecretSinkKind.MODEL_OUTPUT, violations[0].sink)
    }
    
    @Test
    fun `multiple canaries detect independent leaks`() {
        SecretEgressGate.registerCanary("SECRET_A", "PatternA")
        SecretEgressGate.registerCanary("SECRET_B", "PatternB")
        
        val violations = SecretEgressGate.scan("Leaking SECRET_B here")
        assertEquals(1, violations.size)
        assertEquals("PatternB", violations[0].patternName)
    }

    @Test
    fun `split secret across turns is detected through bounded accumulator`() {
        SecretEgressGate.registerCanary(Canary("SplitKey"), "split-secret-value")

        assertTrue(SecretEgressGate.scanTurn("conversation-1", "split-secret-").isEmpty())
        val violations = SecretEgressGate.scanTurn("conversation-1", "value")

        assertEquals(setOf("SplitKey"), violations.map { it.patternName }.toSet())
        assertEquals(2, SecretEgressGate.conversationTurnCount("conversation-1"))
    }

    @Test
    fun `detects encoded and whitespace split representations`() {
        val secret = "encoded-secret-value-456"
        SecretEgressGate.registerCanary(secret, "EncodedKey")
        val encoded = SecretEncodingClosure.variantsOf(secret).first { it != secret && it.length > secret.length }

        assertTrue(SecretEgressGate.scan(encoded).single().patternName == "EncodedKey")
        SecretEgressGate.clearCanaries()
        SecretEgressGate.registerCanary(secret, "SplitEncodedKey")
        val midpoint = encoded.length / 2
        assertTrue(SecretEgressGate.scanTurn("encoded-conversation", encoded.substring(0, midpoint)).isEmpty())
        assertEquals("SplitEncodedKey", SecretEgressGate.scanTurn("encoded-conversation", encoded.substring(midpoint)).single().patternName)
    }

    @Test
    fun `TokenIsolationVault edge scans encoded output across turns`() {
        val vault = TokenIsolationVault(
            Files.createTempDirectory("atropos-egress-vault-"),
            TestSecretVaultKeyProvider(),
        )
        val secret = "vault-edge-secret-789"
        vault.writeSecret("EDGE_TOKEN", secret)
        val encoded = SecretEncodingClosure.variantsOf(secret).first { it != secret && it.length > secret.length }
        SecretEgressGate.registerCanary(secret, "VaultEdge")

        val midpoint = encoded.length / 2
        assertTrue(SecretEgressGate.scanTurn("vault-edge", encoded.substring(0, midpoint)).isEmpty())
        assertEquals("VaultEdge", SecretEgressGate.scanTurn("vault-edge", encoded.substring(midpoint)).single().patternName)
    }

    @Test
    fun `conversation state can be forgotten`() {
        SecretEgressGate.registerCanary("bounded-secret", "Bounded")
        SecretEgressGate.scanTurn("conversation-2", "safe")
        assertEquals(1, SecretEgressGate.conversationTurnCount("conversation-2"))

        SecretEgressGate.forgetConversation("conversation-2")
        assertEquals(0, SecretEgressGate.conversationTurnCount("conversation-2"))
    }

    @Test
    fun `accumulator itself enforces bounded conversation state`() {
        val accumulator = LeakageAccumulator(maxConversations = 1, maxTailChars = 4)
        accumulator.scan("first", "abcd")
        accumulator.scan("second", "efgh")

        assertEquals(0, accumulator.turnCount("first"))
        assertEquals(1, accumulator.turnCount("second"))
    }

    @Test
    fun `sink policy remains owned by the canonical sink matrix`() {
        SecretEgressGate.registerCanary("sink-secret", "Sink")
        val violations = SecretEgressGate.scanTurn(
            "conversation-3", "sink-secret", SecretSinkKind.PERSISTENT_MEMORY
        )

        assertEquals(SecretSinkKind.PERSISTENT_MEMORY, violations.single().sink)
        // The verdict is its own field. The pattern name stays the canary's
        // name so a caller can still recognise which secret leaked.
        assertFalse(violations.single().sinkPermitted)
        assertEquals("Sink", violations.single().patternName)
    }
}
