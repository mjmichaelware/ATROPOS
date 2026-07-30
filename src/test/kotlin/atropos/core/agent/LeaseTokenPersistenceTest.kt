package atropos.core.agent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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
    }
}
