/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthCascadeResolverTest {

    private val resolver = AuthCascadeResolver()

    private fun layers(vararg pairs: Pair<String, Map<String, String>>): List<AuthorityLayer> =
        pairs.mapIndexed { index, (name, values) -> AuthorityLayer(name, index, values) }

    @Test
    fun `an ordinary key takes the highest-precedence definition`() {
        val result = resolver.resolve(
            "theme",
            layers("Agents.md" to mapOf("theme" to "dark"), "settings.json" to mapOf("theme" to "light"))
        )

        assertTrue(result is CascadeResolution.Resolved)
        assertEquals("dark", (result as CascadeResolution.Resolved).value)
        assertEquals("Agents.md", result.source)
        assertFalse(result.final)
    }

    @Test
    fun `a lower layer supplies a key the higher one does not define`() {
        val result = resolver.resolve(
            "editor",
            layers("Agents.md" to emptyMap(), "settings.json" to mapOf("editor" to "vim"))
        )

        assertEquals("vim", (result as CascadeResolution.Resolved).value)
    }

    @Test
    fun `a core invariant cannot be overridden by a lower layer`() {
        val result = resolver.resolve(
            "secretPolicy",
            layers(
                "Agents.md" to mapOf("secretPolicy" to "strict"),
                "settings.json" to mapOf("secretPolicy" to "off")
            )
        )

        assertTrue(result is CascadeResolution.Violation, "a safety rule a project file can turn off is not a rule")
        val violation = result as CascadeResolution.Violation
        assertEquals("Agents.md", violation.heldBy)
        assertTrue(violation.attemptedBy.contains("settings.json"))
    }

    @Test
    fun `a core invariant defined only at the top resolves and is marked final`() {
        val result = resolver.resolve(
            "territoryAtDispatch",
            layers("Agents.md" to mapOf("territoryAtDispatch" to "required"), "settings.json" to emptyMap())
        )

        val resolved = result as CascadeResolution.Resolved
        assertEquals("required", resolved.value)
        assertTrue(resolved.final)
    }

    @Test
    fun `every named core key is protected`() {
        AuthCascadeResolver.CORE_KEYS.forEach { key ->
            val result = resolver.resolve(
                key,
                layers("Agents.md" to mapOf(key to "on"), "local" to mapOf(key to "off"))
            )
            assertTrue(result is CascadeResolution.Violation, "$key must not be overridable")
        }
    }

    @Test
    fun `an undefined key is undefined rather than empty-valued`() {
        assertTrue(resolver.resolve("nope", layers("Agents.md" to emptyMap())) is CascadeResolution.Undefined)
    }

    @Test
    fun `the snapshot covers every key across every layer`() {
        val snapshot = resolver.snapshot(
            layers("Agents.md" to mapOf("a" to "1"), "settings.json" to mapOf("b" to "2"))
        )
        assertEquals(2, snapshot.size)
    }

    @Test
    fun `attestation states are distinct and only Attested is trusted`() {
        val doc = AuthorityDocument("AGENTS.md", "abc", 0)
        assertTrue(AttestationResult.Attested(doc).trusted)
        assertFalse(AttestationResult.Mismatch("AGENTS.md", "abc", "def").trusted)
        assertFalse(AttestationResult.Missing("AGENTS.md").trusted)
    }

    @Test
    fun `a mismatch names both hashes so the change is provable`() {
        val reason = AttestationResult.Mismatch("AGENTS.md", "abc", "def").reason()
        assertTrue(reason.contains("abc"))
        assertTrue(reason.contains("def"))
    }
}
