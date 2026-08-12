package atropos.core.agent

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentDaemonLogWriterTest {
    @Test
    fun daemon_output_is_redacted_before_persistence() {
        val log = Files.createTempDirectory("atropos-daemon-log-").resolve("daemon.log")
        val process = ProcessBuilder("printf", "api_key=daemon-secret\\n").start()

        AgentDaemonLogWriter().attach(process, log).join(2_000L)

        val persisted = Files.readString(log)
        assertFalse(persisted.contains("daemon-secret"))
        assertTrue(persisted.contains("<redacted:secret>"))
    }
}
