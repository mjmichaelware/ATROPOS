package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.nio.file.Files

/**
 * Pins the single answer to "is this response a usable patch".
 *
 * The rules used to exist twice — once in the patch cascade, once in repair —
 * so these assertions now cover both paths at once.
 */
class AgentPatchResponseValidatorTest {

    private val validator = AgentPatchResponseValidator(AgentPatchExtractor())

    private val wellFormedDiff = """
        diff --git a/src/Example.kt b/src/Example.kt
        new file mode 100644
        --- /dev/null
        +++ b/src/Example.kt
        @@ -0,0 +1,2 @@
        +package example
        +object Example
    """.trimIndent()

    /**
     * The same change written with surrounding context lines.
     *
     * Kept separate because [AgentPatchExtractor] does not currently accept it —
     * see [context lines truncate the hunk body]. The fixture above is
     * addition-only for that reason, matching the shape the rest of the suite uses.
     */
    private val diffWithContextLines = """
        diff --git a/src/Example.kt b/src/Example.kt
        --- a/src/Example.kt
        +++ b/src/Example.kt
        @@ -1,3 +1,3 @@
         fun example() {
        -    println("before")
        +    println("after")
         }
    """.trimIndent()

    @Test
    fun `a well-formed unified diff is usable`() {
        assertNotNull(validator.usableDiff(wellFormedDiff))
    }

    @Test
    fun `prose with no diff at all is not usable`() {
        assertNull(validator.usableDiff("I would change the println on line 2."))
    }

    @Test
    fun `prose is reported as no diff found rather than a body problem`() {
        assertEquals(
            AgentPatchResponseValidator.NO_DIFF_FOUND,
            validator.rejectionReason("I would change the println on line 2.")
        )
    }

    @Test
    fun `headers with no hunk are reported as a missing body`() {
        val headersOnly = """
            diff --git a/src/Example.kt b/src/Example.kt
            --- a/src/Example.kt
            +++ b/src/Example.kt
        """.trimIndent()

        assertNull(
            validator.usableDiff(headersOnly),
            "a header-only response describes a change without making one"
        )
        assertEquals(
            AgentPatchResponseValidator.DIFF_BODY_MISSING,
            validator.rejectionReason(headersOnly)
        )
    }

    @Test
    fun `a diff header is recognised in all three shapes`() {
        assertTrue(validator.containsDiffHeader("diff --git a/x b/x"))
        assertTrue(validator.containsDiffHeader("noise\n--- a/x"))
        assertTrue(validator.containsDiffHeader("--- a/x"))
    }

    @Test
    fun `plain prose carries no diff header`() {
        assertFalse(validator.containsDiffHeader("no patch here, just an explanation"))
    }

    @Test
    fun `context lines remain part of a usable hunk`() {
        assertNotNull(validator.usableDiff(diffWithContextLines))
    }

    @Test
    fun `strict create envelope is materialized into the canonical patch shape`() {
        val root = kotlin.io.path.createTempDirectory("atropos-edit").toAbsolutePath()
        val editValidator = AgentPatchResponseValidator(
            AgentPatchExtractor(),
            editMaterializer = AgentEditMaterializer(root)
        )
        val response = "<atropos-create path=\"src/Main.kt\">package demo\n\nfun main() = println(42)</atropos-create>"
        val extraction = editValidator.usableEdit(response)
        assertNotNull(extraction)
        assertEquals(listOf("src/Main.kt"), extraction.touchedPaths)
        assertTrue(extraction.diff.contains("@@ -0,0 +1,3 @@"))
    }

    @Test
    fun `exact replacement refuses ambiguous or stale source`() {
        val root = kotlin.io.path.createTempDirectory("atropos-edit").toAbsolutePath()
        val file = root.resolve("src/Main.kt")
        Files.createDirectories(file.parent)
        Files.writeString(file, "one\none\n")
        val materializer = AgentEditMaterializer(root)
        assertFailsWith<IllegalArgumentException> {
            materializer.materialize(
                listOf(AgentEditOperation.Replace("src/Main.kt", "one", "two"))
            )
        }
        assertFailsWith<IllegalArgumentException> {
            materializer.materialize(
                listOf(AgentEditOperation.Replace("src/Main.kt", "missing", "two"))
            )
        }
    }

    @Test
    fun `exact replacement produces a patch with stable context`() {
        val root = kotlin.io.path.createTempDirectory("atropos-edit").toAbsolutePath()
        val file = root.resolve("src/Main.kt")
        Files.createDirectories(file.parent)
        Files.writeString(file, "fun main() {\n    println(1)\n}\n")
        val materialized = AgentEditMaterializer(root).materialize(
            listOf(AgentEditOperation.Replace("src/Main.kt", "println(1)", "println(2)"))
        )
        val extraction = AgentPatchExtractor().extract(materialized.diff)
        assertNotNull(extraction)
        assertTrue(extraction.hasHunkBody)
        assertTrue(extraction.diff.contains("-    println(1)"))
        assertTrue(extraction.diff.contains("+    println(2)"))
    }

    @Test
    fun `the empty extraction carries no hunk body`() {
        val empty = AgentPatchResponseValidator.emptyExtraction()
        assertEquals("", empty.diff)
        assertFalse(empty.hasHunkBody)
        assertTrue(empty.touchedPaths.isEmpty())
    }
}
