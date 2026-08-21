package atropos.core.factory

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactoryLanguageContractTest {
    @Test
    fun python_completion_contract_accepts_real_source_and_tests() {
        val assessment = FactoryLanguageContract.assess(
            ProjectLanguage.PYTHON.name,
            mapOf(
                "music/__init__.py" to "def describe():\n    return 'music'\n",
                "tests/test_music.py" to "def test_describe():\n    assert True\n",
                "static/index.html" to "<main>music</main>"
            )
        )

        assertTrue(assessment.canComplete)
        assertTrue(assessment.sourceValid)
        assertTrue(assessment.testsValid)
        assertTrue(assessment.sourceFiles.single() == "music/__init__.py")
        assertTrue(assessment.testFiles.single() == "tests/test_music.py")
    }

    @Test
    fun python_completion_contract_rejects_missing_source_or_tests() {
        val sourceOnly = FactoryLanguageContract.assess(
            ProjectLanguage.PYTHON.name,
            mapOf("static/index.html" to "<main>music</main>")
        )
        val testOnly = FactoryLanguageContract.assess(
            ProjectLanguage.PYTHON.name,
            mapOf("tests/test_music.py" to "def test_music():\n    assert True\n")
        )

        assertFalse(sourceOnly.sourceValid)
        assertFalse(sourceOnly.testsValid)
        assertFalse(sourceOnly.canComplete)
        assertFalse(testOnly.sourceValid)
        assertTrue(testOnly.testsValid)
        assertFalse(testOnly.canComplete)
    }

    @Test
    fun kotlin_contract_still_requires_native_paths_and_markers() {
        val valid = FactoryLanguageContract.assess(
            ProjectLanguage.KOTLIN.name,
            mapOf(
                "src/main/kotlin/app/Main.kt" to "fun main(args: Array<String>) { exitProcess(0) }",
                "src/test/kotlin/app/MainTest.kt" to "fun main() { check(true) }"
            )
        )
        val staticOnly = FactoryLanguageContract.assess(
            ProjectLanguage.KOTLIN.name,
            mapOf("static/index.html" to "<main>not source</main>")
        )

        assertTrue(valid.sourceValid)
        assertTrue(valid.testsValid)
        assertFalse(staticOnly.sourceValid)
        assertFalse(staticOnly.testsValid)
    }
}
