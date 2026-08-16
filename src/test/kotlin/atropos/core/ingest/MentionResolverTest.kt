/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ingest

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MentionResolverTest {

    private val roots = listOf(Path.of("/work/atropos"))

    /**
     * A resolver over a territory that exists only as paths.
     *
     * Existence is stubbed true so each test below isolates the rule it is
     * about — extension, ceiling, traversal. The existence rule has its own
     * tests at the bottom, where stubbing it would defeat the point.
     */
    private val resolver = MentionResolver(roots, maxBytes = 1_000, isReadableFile = { true })

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
        val none = MentionResolver(emptyList(), isReadableFile = { true })
        assertTrue(none.resolve("@a.md", 1) is MentionResolution.Refused)
    }

    @Test
    fun `every allowed extension is accepted`() {
        listOf("txt", "md", "docx", "pdf", "png", "jpg", "jpeg").forEach { ext ->
            assertTrue(resolver.resolve("@file.$ext", 10).ingestible, "$ext should be ingestible")
        }
    }

    @Test
    fun `a mention naming a file that is not there is refused`() {
        val missing = MentionResolver(roots, maxBytes = 1_000, isReadableFile = { false })

        // Size cannot answer this: a missing file reports -1, which is under
        // every ceiling, so a mistyped name inside a granted territory used to
        // resolve clean and attach nothing while the operator was told it had.
        val refused = missing.resolve("@docs/typo.md", -1) as MentionResolution.Refused

        assertTrue(refused.reason.contains("not a readable file"), refused.reason)
        assertTrue(refused.remedy.contains("spelling"), refused.remedy)
    }

    @Test
    fun `existence is only checked inside the territory`() {
        val probed = mutableListOf<Path>()
        val watcher = MentionResolver(roots, maxBytes = 1_000, isReadableFile = { path ->
            // add(), not +=: Path is itself Iterable<Path>, so `+=` appends
            // the path's segments rather than the path.
            probed.add(path)
            true
        })

        watcher.resolve("@../../.ssh/id_rsa.md", 10)

        // A path the grant refused is never stat-ed, so the refusal cannot be
        // used to probe for files outside the territory.
        assertTrue(probed.isEmpty(), "probed outside territory: $probed")
    }
}
