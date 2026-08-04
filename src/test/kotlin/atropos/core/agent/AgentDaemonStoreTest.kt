package atropos.core.agent

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentDaemonStoreTest {
    @Test
    fun persisted_daemon_messages_are_redacted_before_base64_storage() {
        val root = Files.createTempDirectory("atropos-daemon-store-")
        val store = AgentDaemonStore(
            repoRoot = root,
            clock = { Instant.parse("2026-01-01T00:00:00Z") },
            daemonRootOverride = root.resolve("daemon")
        )

        val record = store.writeState(
            store.initialRecord(
                state = AgentDaemonState.FAILED,
                pollSeconds = 15,
                message = "provider api_key=super-secret-value"
            )
        )

        assertTrue(record.lastMessage!!.contains("<redacted:secret>"), record.lastMessage)
        assertFalse(Files.readString(store.stateFile()).contains("super-secret-value"))
        assertTrue(store.readState()!!.lastMessage!!.contains("<redacted:secret>"))
    }

    @Test
    fun stop_reason_is_redacted_before_persistence() {
        val root = Files.createTempDirectory("atropos-daemon-stop-")
        val store = AgentDaemonStore(repoRoot = root, daemonRootOverride = root.resolve("daemon"))

        store.requestStop("operator token=super-secret-value")

        assertFalse(Files.readString(store.stopFile()).contains("super-secret-value"))
    }
}
