/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import kotlin.test.Test
import kotlin.test.assertFailsWith

class AppGeneratedBehaviorGuardTest {
    private val guard = AppGeneratedBehaviorGuard()

    @Test
    fun python_scaffold_passes_guard() {
        val prompt = "Build a python music app using fastapi"
        val spec = AppProjectSpecParser().parse(prompt)
        val files = mapOf(
            "music/__init__.py" to """
                # ATROPOS lineage: project_id=factory-1
                # prompt_sha256=123
                # prompt_fingerprint=abc
                # prompt_spans=none
                # research_sha256=456
                
                def describe():
                    return "music"
            """.trimIndent(),
            "tests/test_music.py" to """
                # ATROPOS lineage: project_id=factory-1
                # prompt_sha256=123
                # prompt_fingerprint=abc
                # prompt_spans=none
                # research_sha256=456
                
                def test_describe():
                    assert describe() == "music"
            """.trimIndent()
        )
        guard.requireRealBehavior(spec, files) // Should not throw
    }

    @Test
    fun python_without_an_executable_assertion_fails_guard() {
        val spec = AppProjectSpecParser().parse("Build a python notes app")
        val files = mapOf(
            "notes/__init__.py" to lineage("#") + "\ndef describe():\n    return \"notes\"\n",
            "tests/test_notes.py" to lineage("#") + "\ndef test_describe():\n    return True\n"
        )
        assertFailsWith<IllegalArgumentException> {
            guard.requireRealBehavior(spec, files)
        }
    }

    @Test
    fun typescript_scaffold_uses_native_test_contract() {
        val spec = AppProjectSpecParser().parse("Build a typescript notes app")
        val files = mapOf(
            "src/index.ts" to lineage("//") + "\nexport function describe(): string { return \"notes\"; }\n",
            "src/index.test.ts" to lineage("//") +
                "\ntest(\"describes itself\", () => { expect(describe()).toBe(\"notes\"); });\n"
        )
        guard.requireRealBehavior(spec, files)
    }

    private fun lineage(prefix: String): String =
        "$prefix ATROPOS lineage: project_id=factory-1\n" +
            "$prefix prompt_sha256=123\n" +
            "$prefix prompt_fingerprint=abc\n" +
            "$prefix prompt_spans=none\n" +
            "$prefix research_sha256=456\n"

    @Test
    fun kotlin_empty_scaffold_fails_guard() {
        val prompt = "Build a kotlin calculator"
        val spec = AppProjectSpecParser().parse(prompt)
        assertFailsWith<IllegalArgumentException> {
            guard.requireRealBehavior(spec, emptyMap())
        }
    }

    @Test
    fun kotlin_invalid_lineage_fails_guard() {
        val prompt = "Build a kotlin calculator"
        val spec = AppProjectSpecParser().parse(prompt)
        val files = mapOf(
            "src/main/kotlin/calculator/Main.kt" to """
                package calculator
                fun main() { println("hello") }
            """.trimIndent(),
            "src/test/kotlin/calculator/MainTest.kt" to """
                package calculator
                fun main() { check(true) }
            """.trimIndent()
        )
        assertFailsWith<IllegalArgumentException> {
            guard.requireRealBehavior(spec, files)
        }
    }
}
