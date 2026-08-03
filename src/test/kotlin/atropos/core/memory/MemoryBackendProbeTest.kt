package atropos.core.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryBackendProbeTest {

    @Test
    fun `a zero exit means the command exists`() {
        assertTrue(MemoryBackendProbe { 0 }.commandExists("sqlite3"))
    }

    @Test
    fun `a nonzero exit means it does not`() {
        assertFalse(MemoryBackendProbe { 1 }.commandExists("sqlite3"))
    }

    @Test
    fun `a throwing probe reports unavailable rather than propagating`() {
        val probe = MemoryBackendProbe { error("no shell in this sandbox") }
        assertFalse(
            probe.commandExists("sqlite3"),
            "an optional accelerator must not take down the memory subsystem"
        )
        assertFalse(probe.sqliteVecAvailable())
    }

    @Test
    fun `the vec probe asks sqlite to actually load the extension`() {
        var script = ""
        MemoryBackendProbe { command -> script = command; 0 }.sqliteVecAvailable()
        assertTrue(
            script.contains("load_extension('sqlite_vec')"),
            "a present sqlite3 without the extension would otherwise report available and fail on first use"
        )
    }

    @Test
    fun `the vec probe requires both the binary and the extension`() {
        assertFalse(MemoryBackendProbe { 1 }.sqliteVecAvailable())
        assertTrue(MemoryBackendProbe { 0 }.sqliteVecAvailable())
    }

    @Test
    fun `ordinary command names are accepted`() {
        var script = ""
        val probe = MemoryBackendProbe { command -> script = command; 0 }
        listOf("sqlite3", "python3.11", "some_tool", "a-tool", "g++").forEach { name ->
            probe.commandExists(name)
            assertTrue(script.contains(name))
        }
    }

    @Test
    fun `a name that could break out of the command is refused`() {
        val probe = MemoryBackendProbe { 0 }
        listOf("sqlite3; rm -rf /", "a b", "\$(whoami)", "`id`", "x|y", "").forEach { name ->
            assertFailsWith<IllegalArgumentException>("'$name' must not reach the shell") {
                probe.commandExists(name)
            }
        }
    }

    @Test
    fun `the probe runs through sh so the caller cannot be platform-specific`() {
        var invocations = 0
        MemoryBackendProbe { invocations++; 0 }.commandExists("sqlite3")
        assertEquals(1, invocations, "one probe should mean one shell invocation")
    }
}
