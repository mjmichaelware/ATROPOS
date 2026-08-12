/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.lakehouse

import atropos.core.AtroposConfig
import atropos.core.ApiKeys
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Each atom targets its own lakehouse fetch through the keywords in its
 * statement, and the retrieved documents ride into the node that executes it.
 *
 * The registry is human taxonomy — `E/networking/http`, `I/uiux/a11y_design` —
 * so matching is token overlap against path segments. Opaque ontological codes
 * would be the wrong instrument: the registry's readability is the property
 * that makes it matchable at all.
 */
class AtomLakehouseContextTest {

    private fun indexOver(vararg paths: String): LakehousePathIndex {
        val mount = Files.createTempDirectory("atropos-lh").toFile()
        File(mount, "index").mkdirs()
        File(mount, "index/paths.txt").writeText(paths.joinToString("\n"))
        return LakehousePathIndex(
            AtroposConfig(
                ApiKeys("", "", "", ""),
                LakehouseConfig(mount.absolutePath, "${mount.absolutePath}/vector_storage.db"),
                RuntimeConfig("groq", 0.2)
            )
        )
    }

    private val registry = arrayOf(
        "E/networking/http",
        "E/networking/tls",
        "E/networking/auth_protocols",
        "C/languages/kotlin/syntax",
        "I/uiux/a11y_design",
        "M/devtools/git"
    )

    // ── keywords ─────────────────────────────────────────────────────────

    @Test
    fun `requirement filler words are dropped`() {
        val keywords = AtomKeywordExtractor.keywords(
            "The system must not use the file for any value that it has."
        )

        assertFalse(keywords.contains("must"))
        assertFalse(keywords.contains("system"))
        assertFalse(keywords.contains("the"))
    }

    @Test
    fun `an alias reaches the segment the registry actually uses`() {
        assertTrue(AtomKeywordExtractor.keywords("Every REST endpoint returns a status code").contains("http"))
        assertTrue(AtomKeywordExtractor.keywords("Certificates are validated over SSL").contains("tls"))
        assertTrue(AtomKeywordExtractor.keywords("WCAG contrast must hold").contains("a11y_design"))
        assertTrue(AtomKeywordExtractor.keywords("A coroutine must not block").contains("kotlin"))
        assertTrue(AtomKeywordExtractor.keywords("Rebase before merging a branch").contains("git"))
    }

    @Test
    fun `keyword extraction is deterministic`() {
        val statement = "OAuth tokens must never be logged during an HTTP request."

        assertEquals(
            AtomKeywordExtractor.keywords(statement),
            AtomKeywordExtractor.keywords(statement),
            "a probabilistic keyword set would make identical plans hash differently"
        )
    }

    // ── path matching ────────────────────────────────────────────────────

    @Test
    fun `an atom about TLS selects the TLS shelf`() {
        val matches = indexOver(*registry)
            .match(AtomKeywordExtractor.keywords("Certificate pinning is required for every SSL handshake."))

        assertEquals("E/networking/tls", matches.first().path)
    }

    @Test
    fun `an atom about accessibility selects the a11y shelf`() {
        val matches = indexOver(*registry)
            .match(AtomKeywordExtractor.keywords("ARIA labels must satisfy WCAG contrast ratios."))

        assertEquals("I/uiux/a11y_design", matches.first().path)
    }

    @Test
    fun `the domain shelf letter never matches on its own`() {
        val matches = indexOver(*registry).match(listOf("e", "i", "c", "m"))

        assertTrue(
            matches.isEmpty(),
            "a single-letter shelf marker matching would pull in a quarter of the registry"
        )
    }

    @Test
    fun `an atom about nothing in the registry retrieves nothing`() {
        val matches = indexOver(*registry)
            .match(AtomKeywordExtractor.keywords("The worktree record is written atomically."))

        assertTrue(matches.none { it.path.contains("networking") })
    }

    @Test
    fun `matching is bounded so one atom cannot carry a library`() {
        val matches = indexOver(*registry)
            .match(AtomKeywordExtractor.keywords("http tls auth_protocols kotlin a11y_design git"), limit = 3)

        assertEquals(3, matches.size)
    }

    @Test
    fun `an absent mount yields no context rather than an error`() {
        val index = indexOver()

        assertFalse(index.available)
        assertTrue(index.match(listOf("http")).isEmpty())
    }

    @Test
    fun `an exact segment outranks a partial one`() {
        val index = indexOver("E/networking/http", "E/networking/http_legacy_notes")

        assertEquals("E/networking/http", index.match(listOf("http")).first().path)
    }

    // ── provenance ───────────────────────────────────────────────────────

    @Test
    fun `provenance names the path, the status and the hash`() {
        val context = atropos.core.planning.AtomContext(
            path = "E/networking/http",
            sha256 = "abcdef0123456789abcdef",
            status = "HIT",
            reason = "local_cas",
            content = "…"
        )

        val line = context.provenance()

        assertTrue(line.contains("path=E/networking/http"))
        assertTrue(line.contains("status=HIT"))
        assertTrue(line.contains("sha256=abcdef0123456789"))
        assertTrue(context.hit)
    }

    @Test
    fun `a miss is still recorded`() {
        val context = atropos.core.planning.AtomContext(
            path = "E/networking/tls",
            sha256 = null,
            status = "MISS",
            reason = "remote_unavailable_or_hash_mismatch",
            content = ""
        )

        assertFalse(context.hit)
        assertTrue(
            context.provenance().contains("status=MISS"),
            "an atom that asked and got nothing differs from one that never asked"
        )
    }
}
