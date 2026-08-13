/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A prompt beginning "Build a Kotlin HTTP client" derived the app name from
 * its first meaningful word, so the generated source declared `package kotlin`
 * and kotlinc refused the whole project:
 *
 *     only the Kotlin standard library is allowed to use the "kotlin" package
 *
 * The keyword guard did not catch it, because `kotlin` is an ordinary
 * identifier reserved by the toolchain rather than by the grammar. Those are
 * two different lists and the generator only had one.
 */
class ReservedPackageNameTest {

    @Test
    fun `a toolchain-reserved root is never emitted as a package`() {
        listOf("kotlin", "Kotlin", "KOTLIN", "kotlinx", "java", "javax", "android", "androidx")
            .forEach { name ->
                val generated = AppProjectGenerator.safeName(name)
                assertTrue(
                    generated.startsWith("app_"),
                    "'$name' must not become a package root the toolchain owns, got '$generated'"
                )
            }
    }

    @Test
    fun `an ordinary name is left alone`() {
        assertEquals("httpclient", AppProjectGenerator.safeName("HttpClient"))
        assertEquals("todo", AppProjectGenerator.safeName("todo"))
        assertEquals("my_app", AppProjectGenerator.safeName("my-app"))
    }

    @Test
    fun `a Kotlin keyword is still guarded`() {
        assertTrue(AppProjectGenerator.safeName("object").startsWith("app_"))
        assertTrue(AppProjectGenerator.safeName("fun").startsWith("app_"))
    }

    @Test
    fun `a name that cannot start an identifier is prefixed`() {
        assertTrue(AppProjectGenerator.safeName("9lives").startsWith("app_"))
    }

    @Test
    fun `an empty or punctuation-only name falls back`() {
        assertEquals("app", AppProjectGenerator.safeName(""))
        assertEquals("app", AppProjectGenerator.safeName("---"))
    }
}
