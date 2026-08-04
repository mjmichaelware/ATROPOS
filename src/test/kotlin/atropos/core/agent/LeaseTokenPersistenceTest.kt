package atropos.core.agent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LeaseTokenPersistenceTest {
    @Test
    fun queue_metadata_keeps_lease_recovery_without_bearer_token() {
        val root = Files.createTempDirectory("atropos-lease-persistence-")
        val store = AgentQueueStore(root)
        val queued = store.createEntry("bounded task", "true")
        val leased = assertNotNull(store.acquireLease(queued.id, "worker", 60).record)
        val token = assertNotNull(leased.lease).token
        val metadata = Files.readString(leased.metaFile)

        assertFalse(metadata.contains(token))
        assertTrue(metadata.contains("leaseTokenSha256="))

        val recovered = assertNotNull(store.resolve(leased.id))
        val heartbeated = store.heartbeat(recovered.id, token, leaseSeconds = 60)
        assertNotNull(heartbeated)
        assertEquals(LeaseTokenDigest.of(token), assertNotNull(store.resolve(leased.id)?.lease).token)
        assertFalse(store.resolve(leased.id)?.renderRaw().orEmpty().contains(token))
    }

    @Test
    fun legacy_queue_bearer_is_rewritten_during_recovery() {
        val root = Files.createTempDirectory("atropos-legacy-queue-lease-")
        val store = AgentQueueStore(root)
        val queued = store.createEntry("bounded task", "true")
        val leased = assertNotNull(store.acquireLease(queued.id, "worker", 60).record)
        val token = assertNotNull(leased.lease).token
        Files.writeString(leased.metaFile, Files.readString(leased.metaFile).replace("leaseTokenSha256=${LeaseTokenDigest.of(token)}", "leaseToken=$token"))

        val recovered = assertNotNull(store.resolve(leased.id))
        val metadata = Files.readString(leased.metaFile)
        assertEquals(LeaseTokenDigest.of(token), assertNotNull(recovered.lease).token)
        assertFalse(metadata.contains("leaseToken=$token"))
        assertFalse(metadata.contains(token))
    }

    @Test
    fun queue_recovery_rewrites_bearer_text_in_metadata_without_losing_lease_timing() {
        val root = Files.createTempDirectory("atropos-queue-redaction-recovery-")
        val store = AgentQueueStore(root)
        val bearer = "Bearer ABCDEFGHIJKLMNOPQRSTUVWX"
        val queued = store.createEntry("bounded task", "true")
        val leased = assertNotNull(store.acquireLease(queued.id, "worker", 60).record)
        val persistedLease = assertNotNull(store.resolve(leased.id)?.lease)
        val legacy = Files.readString(leased.metaFile)
            .replace("taskB64=${java.util.Base64.getEncoder().encodeToString("bounded task".toByteArray())}", "taskB64=${java.util.Base64.getEncoder().encodeToString(bearer.toByteArray())}")
        Files.writeString(leased.metaFile, legacy)

        val recovered = assertNotNull(store.resolve(leased.id))
        val metadata = Files.readString(leased.metaFile)
        assertEquals(persistedLease.expiresAt, assertNotNull(recovered.lease).expiresAt)
        assertFalse(recovered.task.contains("ABCDEFGHIJKLMNOPQRSTUVWX"))
        assertFalse(metadata.contains("ABCDEFGHIJKLMNOPQRSTUVWX"))
        assertTrue(recovered.task.contains("<redacted:bearer>"))
    }
}
