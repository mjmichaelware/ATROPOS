/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ingest

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MentionResolverTest {

    private val roots = listOf(Path.of("/work/atropos"))
    private val resolver = MentionResolver(roots, maxBytes = 1_000)

    @Test
    fun `a mention inside the territory resolves`() {
        val result = resolver.resolve("@docs/notes.md", 100)
        assertTrue(result.ingestible)
        assertEquals("md", (result as MentionResolution.Resolved).extension)
    }

    @Test
    fun `traversal out of the territory is refused`() {
        val refused = resolver.resolve("@../../.ssh/id_rsa", 10)
        assertTrue(refused is MentionResolution.Refused)
    }

    @Test
    fun `a disallowed extension is refused and the allow-list is named`() {
        val refused = resolver.resolve("@secrets.env", 10) as MentionResolution.Refused
        assertTrue(refused.reason.contains("not ingestible"))
        assertTrue(refused.remedy.contains("md"))
    }

    @Test
    fun `an oversized file is refused rather than truncated`() {
        val refused = resolver.resolve("@big.pdf", 5_000) as MentionResolution.Refused
        assertTrue(refused.reason.contains("ingest ceiling"))
    }

    @Test
    fun `an empty mention names no file`() {
        assertTrue(resolver.resolve("@", 1) is MentionResolution.Refused)
    }

    @Test
    fun `no granted territory permits nothing`() {
        val none = MentionResolver(emptyList())
        assertTrue(none.resolve("@a.md", 1) is MentionResolution.Refused)
    }

    @Test
    fun `every allowed extension is accepted`() {
        listOf("txt", "md", "docx", "pdf", "png", "jpg", "jpeg").forEach { ext ->
            assertTrue(resolver.resolve("@file.$ext", 10).ingestible, "$ext should be ingestible")
        }
    }
}
