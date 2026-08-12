package atropos.core.memory

import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SqliteVecMemoryIndexTest {
    @Test
    fun preflight_and_write_use_the_same_normalized_database_path() {
        val commands = mutableListOf<List<String>>()
        val chunk = MemorySourceChunk(
            index = 0,
            tokenStart = 0,
            tokenEndExclusive = 1,
            text = "source",
            sha256 = sha256("source")
        )
        val database = File("build/../.atropos-test-relative/source-vectors.db")
        val index = SqliteVecMemoryIndex(database, process = { command, _ ->
            commands += command
            SqliteVecMemoryIndex.ProcessResult(0, "")
        })

        index.index(listOf(chunk), mapOf(chunk.sha256 to listOf(1.0f, 0.0f)))

        val expected = Path.of(database.path).toAbsolutePath().normalize().toString()
        assertEquals(expected, commands[0][1])
        assertEquals(expected, commands[1][1])
    }

    private fun sha256(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
