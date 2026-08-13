/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.lakehouse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What an atom retrieves is decided entirely here, so a wrong keyword is not a
 * ranking nuisance — it is the atom being handed a document about something
 * else and generating from it.
 */
class AtomKeywordExtractorTest {

    /**
     * The regression that motivated leading-directive removal. "Build a
     * command-line note keeper" retrieved `N/build/build_systems`,
     * `N/build/ci` and `N/build/release`: ordering by first appearance made the
     * imperative the strongest keyword in a statement that is about notes.
     */
    @Test
    fun `an opening imperative is not the subject of the statement`() {
        val keywords = AtomKeywordExtractor.keywords("Build a command-line note keeper.")

        assertFalse("build" in keywords, "the verb names the act, not the subject")
        assertTrue("note" in keywords, "the subject must survive")
    }

    /**
     * The same word mid-sentence *is* the subject. A blanket stop word would
     * have erased this along with the noise, which is why the rule is
     * positional.
     */
    @Test
    fun `the same word is kept when it is the subject`() {
        val keywords = AtomKeywordExtractor.keywords("The build system must publish a release artifact.")

        assertTrue("build" in keywords, "here the word is what the atom is about")
    }

    @Test
    fun `only the first word is treated as a directive`() {
        val keywords = AtomKeywordExtractor.keywords("Create a parser that can create nested scopes.")

        assertTrue("parser" in keywords)
        assertTrue("nested" in keywords)
    }

    @Test
    fun `a statement of only a directive yields nothing rather than misleading keywords`() {
        assertEquals(emptyList(), AtomKeywordExtractor.keywords("Build."))
    }

    @Test
    fun `short language names survive the length floor`() {
        val keywords = AtomKeywordExtractor.keywords("Implement the parser in C with a CLI front end.")

        assertTrue("c" in keywords, "C/languages/c/syntax is unreachable without this")
        assertTrue("cli" in keywords)
    }

    @Test
    fun `aliases map an atom's words onto the registry's words`() {
        val keywords = AtomKeywordExtractor.keywords("The REST endpoint must reject an invalid payload.")

        assertTrue("http" in keywords, "the registry shelves this under http, not rest")
    }
}
