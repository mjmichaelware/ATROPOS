package atropos.core.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `the empty extraction carries no hunk body`() {
        val empty = AgentPatchResponseValidator.emptyExtraction()
        assertEquals("", empty.diff)
        assertFalse(empty.hasHunkBody)
        assertTrue(empty.touchedPaths.isEmpty())
    }
}
