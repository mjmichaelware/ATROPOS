/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdversarialResponseNormalizerTest {

    private val normalizer = AdversarialResponseNormalizer()

    @Test
    fun `extractAndNormalize strips conversational greetings and extracts markdown code block`() {
        val raw = """
            Hello! Certainly, I would be happy to help fix your problem.
            Here is the updated file:
            ```kotlin:src/main/kotlin/example/Foo.kt
            package example

            class Foo {
                fun bar(): String = "baz"
            }
            ```
            I hope this resolves your error! Let me know if you need any other changes.
        """.trimIndent()

        val extraction = normalizer.extractAndNormalize(raw)
        assertEquals(1, extraction.files.size)
        assertTrue(extraction.hadConversationalProse)
        assertEquals("src/main/kotlin/example/Foo.kt", extraction.files.first().relativePath)
        assertTrue(extraction.files.first().content.contains("fun bar(): String = \"baz\""))
        assertFalse(extraction.files.first().content.contains("Hello! Certainly"))
        assertFalse(extraction.files.first().content.contains("I hope this resolves"))
    }

    @Test
    fun `extractAndNormalize extracts from file block headers`() {
        val raw = """
            --- FILE: src/main/kotlin/example/Config.kt ---
            package example

            data class Config(val port: Int)
            --- END FILE ---
        """.trimIndent()

        val extraction = normalizer.extractAndNormalize(raw)
        assertEquals(1, extraction.files.size)
        assertEquals("src/main/kotlin/example/Config.kt", extraction.files.first().relativePath)
        assertEquals("package example\n\ndata class Config(val port: Int)", extraction.files.first().content)
    }

    @Test
    fun `extractAndNormalize extracts from equals wrappers`() {
        val raw = """
            === FILE: app/build.gradle.kts ===
            plugins {
                kotlin("jvm")
            }
            === END FILE ===
        """.trimIndent()

        val extraction = normalizer.extractAndNormalize(raw)
        assertEquals(1, extraction.files.size)
        assertEquals("app/build.gradle.kts", extraction.files.first().relativePath)
        assertTrue(extraction.files.first().content.contains("plugins {"))
    }

    @Test
    fun `validateSyntax catches unclosed delimiters in Kotlin code`() {
        val invalidCode = """
            package example
            class Broken {
                fun test() {
                    if (true) {
                        println("missing closing brace")
                }
            }
        """.trimIndent()

        val result = normalizer.validateSyntax("example/Broken.kt", invalidCode)
        assertFalse(result.valid)
        assertTrue(result.violations.any { it.contains("Unclosed delimiter") })
    }

    @Test
    fun `validateSyntax catches hollow TODO stubs in Kotlin code`() {
        val hollowCode = """
            package example
            class Stubbed {
                fun doWork() {
                    TODO("implement later")
                }
            }
        """.trimIndent()

        val result = normalizer.validateSyntax("example/Stubbed.kt", hollowCode)
        assertFalse(result.valid)
        assertTrue(result.violations.any { it.contains("TODO()") })
    }

    @Test
    fun `applyValidatedPatches blocks writing to disk on syntax violation`() {
        val tempDir = Files.createTempDirectory("normalizer-test-block")
        val brokenResponse = """
            Here is your fix:
            ```kotlin:broken/Sample.kt
            package broken
            class Sample {
                fun incomplete(
            ```
        """.trimIndent()

        val outcome = normalizer.applyValidatedPatches(brokenResponse, tempDir)
        assertTrue(outcome is PatchApplicationResult.Failure)
        assertFalse(Files.exists(tempDir.resolve("broken/Sample.kt")), "File should NOT be written to disk when syntax check fails")
    }

    @Test
    fun `applyValidatedPatches writes valid code to disk`() {
        val tempDir = Files.createTempDirectory("normalizer-test-pass")
        val validResponse = """
            --- FILE: src/main/kotlin/demo/Success.kt ---
            package demo

            class Success {
                fun compute(): Int = 42
            }
            --- END FILE ---
        """.trimIndent()

        val outcome = normalizer.applyValidatedPatches(validResponse, tempDir)
        assertTrue(outcome is PatchApplicationResult.Success)
        val targetFile = tempDir.resolve("src/main/kotlin/demo/Success.kt")
        assertTrue(Files.exists(targetFile))
        assertTrue(Files.readString(targetFile).contains("fun compute(): Int = 42"))
    }
}
