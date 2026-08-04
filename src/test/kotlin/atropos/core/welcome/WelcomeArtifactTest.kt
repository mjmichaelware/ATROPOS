/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.welcome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WelcomeArtifactTest {

    @Test
    fun `the same inputs always produce the same content id`() {
        val a = WelcomeArtifact(listOf("groq", "local"), 4_000).contentId()
        val b = WelcomeArtifact(listOf("groq", "local"), 4_000).contentId()
        assertEquals(a, b, "a welcome that differs between boots cannot be cached by hash")
    }

    @Test
    fun `provider order does not change the artifact`() {
        assertEquals(
            WelcomeArtifact(listOf("local", "groq"), 1).contentId(),
            WelcomeArtifact(listOf("groq", "local"), 1).contentId()
        )
    }

    @Test
    fun `different inputs produce different ids`() {
        assertNotEquals(
            WelcomeArtifact(listOf("groq"), 1).contentId(),
            WelcomeArtifact(listOf("groq", "local"), 1).contentId()
        )
    }

    @Test
    fun `free providers are listed and named`() {
        val rendered = WelcomeArtifact(listOf("groq", "local"), 1).render()
        assertTrue(rendered.contains("groq"))
        assertTrue(rendered.contains("local"))
    }

    @Test
    fun `no configured free provider is stated honestly rather than encouraged`() {
        val rendered = WelcomeArtifact(emptyList(), 1).render()
        assertTrue(rendered.contains("none configured"))
        // Claiming a free path that does not exist strands the operator at the
        // first prompt.
        assertTrue(rendered.contains("/providers"))
    }

    @Test
    fun `an undeclared ceiling is not presented as unlimited`() {
        val rendered = WelcomeArtifact(listOf("local"), null).render()
        assertTrue(rendered.contains("not declared"))
        assertTrue(rendered.contains("unbounded until you set one"))
    }

    @Test
    fun `the content id is a full sha-256`() {
        assertEquals(64, WelcomeArtifact(listOf("local"), 1).contentId().length)
    }

    @Test
    fun `the welcome leads with local-first and ends with a first command`() {
        val rendered = WelcomeArtifact(listOf("local"), 1).render()
        assertTrue(rendered.contains("Local-first"))
        assertTrue(rendered.trimEnd().endsWith("/help"))
    }
}
