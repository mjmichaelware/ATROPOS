package atropos.core.agent

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SupervisedSessionStoreLeaseTokenTest {
    @Test
    fun session_metadata_migrates_legacy_bearer_without_losing_lease_recovery_state() {
        val root = Files.createTempDirectory("atropos-legacy-session-lease-")
        val store = SupervisedSessionStore(root)
        val token = "session-lease-sensitive-bearer"
        val record = store.initialRecord(AgentRuntimeKind.OPENCODE).copy(
            leaseToken = token,
            leaseExpiresAt = Instant.parse("2026-07-30T18:00:00Z")
        )
        val written = store.writeSession(record)
        Files.writeString(
            written.metaFile,
            Files.readString(written.metaFile)
                .replace("leaseTokenSha256=${LeaseTokenDigest.of(token)}", "leaseToken=$token")
        )

        val recovered = assertNotNull(store.readSession(written.id))
        val metadata = Files.readString(written.metaFile)
        assertEquals(LeaseTokenDigest.of(token), recovered.leaseToken)
        assertEquals(record.leaseExpiresAt, recovered.leaseExpiresAt)
        assertTrue(metadata.contains("leaseTokenSha256=${LeaseTokenDigest.of(token)}"))
        assertFalse(metadata.contains("leaseToken=$token"))
        assertFalse(metadata.contains(token))
    }

    @Test
    fun session_recovery_redacts_provider_and_message_bearers_without_losing_backoff_state() {
        val root = Files.createTempDirectory("atropos-session-redaction-recovery-")
        val store = SupervisedSessionStore(root)
        val bearer = "Bearer ABCDEFGHIJKLMNOPQRSTUVWX"
        val record = store.initialRecord(AgentRuntimeKind.OPENCODE).copy(
            providerSessionId = "provider-session",
            lastMessage = "healthy",
            backoffAttempt = 3,
            nextBackoffAt = Instant.parse("2026-07-30T18:00:00Z")
        )
        val written = store.writeSession(record)
        val legacy = Files.readString(written.metaFile)
            .replace("providerSessionId=provider-session", "providerSessionId=$bearer")
            .replace("lastMessageB64=${java.util.Base64.getEncoder().encodeToString("healthy".toByteArray())}", "lastMessageB64=${java.util.Base64.getEncoder().encodeToString(bearer.toByteArray())}")
        Files.writeString(written.metaFile, legacy)

        val recovered = assertNotNull(store.readSession(written.id))
        val metadata = Files.readString(written.metaFile)
        assertEquals(record.backoffAttempt, recovered.backoffAttempt)
        assertEquals(record.nextBackoffAt, recovered.nextBackoffAt)
        assertFalse(recovered.providerSessionId.orEmpty().contains("ABCDEFGHIJKLMNOPQRSTUVWX"))
        assertFalse(recovered.lastMessage.orEmpty().contains("ABCDEFGHIJKLMNOPQRSTUVWX"))
        assertFalse(metadata.contains("ABCDEFGHIJKLMNOPQRSTUVWX"))
        assertTrue(recovered.providerSessionId.orEmpty().contains("<redacted:bearer>"))
        assertTrue(recovered.lastMessage.orEmpty().contains("<redacted:bearer>"))
    }
}
