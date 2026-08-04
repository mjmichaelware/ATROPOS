/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.core.welcome.WelcomeArtifact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WelcomeProjectionTest {

    @Test
    fun `the content id travels with the body`() {
        val artifact = WelcomeArtifact(listOf("local"), 1_000L)
        val json = WelcomeProjection().render(artifact)

        assertTrue(json.contains("\"contentId\":\"${artifact.contentId()}\""))
        assertTrue(json.contains("ATROPOS"))
    }

    @Test
    fun `the same inputs always project the same id`() {
        // Deterministic is the whole atom: a welcome assembled from whatever
        // providers happened to answer cannot be cached by hash.
        val a = WelcomeProjection().render(WelcomeArtifact(listOf("b", "a"), 10L))
        val b = WelcomeProjection().render(WelcomeArtifact(listOf("a", "b"), 10L))

        assertEquals(a, b, "provider order must not change the artifact")
    }

    @Test
    fun `different content yields a different id`() {
        val a = WelcomeProjection().render(WelcomeArtifact(listOf("local"), 10L))
        val b = WelcomeProjection().render(WelcomeArtifact(listOf("local"), 20L))

        assertTrue(a != b, "a changed welcome must be addressable as changed")
    }

    @Test
    fun `an undeclared ceiling is not rendered as unlimited`() {
        val json = WelcomeProjection().render(WelcomeArtifact(listOf("local"), null))

        assertTrue(json.contains("not declared"))
        assertTrue(json.contains("unbounded until you set one"))
    }

    @Test
    fun `no free provider is stated honestly rather than encouragingly`() {
        val json = WelcomeProjection().render(WelcomeArtifact(emptyList(), 10L))

        assertTrue(json.contains("none configured"))
    }
}
