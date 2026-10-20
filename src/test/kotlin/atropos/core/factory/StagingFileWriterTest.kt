/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.util.CodeBlockExtractor
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class StagingFileWriterTest {

    @Test
    fun unescapes_escaped_quotes_in_code_block_extractor() {
        val raw = "val msg = \\\"hello\\\" + \\\'world\\\'"
        val normalized = CodeBlockExtractor.normalize(raw)
        assertEquals("val msg = \"hello\" + 'world'", normalized)
    }

    @Test
    fun extracts_and_unescapes_code_fence() {
        val raw = "```kotlin\nval s = \\\"test\\\"\n```"
        val extracted = CodeBlockExtractor.extract(raw)
        assertEquals("val s = \"test\"", extracted)
    }

    @Test
    fun writes_normalized_source_to_disk() {
        val tempDir = createTempDirectory("staging-writer-test")
        try {
            val dest = tempDir.resolve("sub/dir/Module.kt")
            val raw = "class Module { val name = \\\"test\\\" }"
            StagingFileWriter.writeSource(dest, raw)
            val written = Files.readString(dest)
            assertEquals("class Module { val name = \"test\" }", written)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
