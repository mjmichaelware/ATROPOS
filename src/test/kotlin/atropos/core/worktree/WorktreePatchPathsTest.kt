package atropos.core.worktree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorktreePatchPathsTest {

    @Test
    fun `both sides of a diff are collected`() {
        val diff = """
            diff --git a/src/Main.kt b/src/Main.kt
            --- a/src/Old.kt
            +++ b/src/Main.kt
            @@ -1 +1 @@
            -old
            +new
        """.trimIndent()

        val paths = WorktreePatchPaths.extract(diff)
        assertTrue("src/Main.kt" in paths)
        assertTrue(
            "src/Old.kt" in paths,
            "the old side names the file a rename or delete removes and must not be skipped"
        )
    }

    @Test
    fun `dev-null is not a path`() {
        val diff = """
            diff --git a/src/New.kt b/src/New.kt
            --- /dev/null
            +++ b/src/New.kt
            @@ -0,0 +1 @@
            +created
        """.trimIndent()

        assertEquals(listOf("src/New.kt"), WorktreePatchPaths.extract(diff))
    }

    @Test
    fun `a path repeated across headers is listed once`() {
        val diff = """
            diff --git a/src/Main.kt b/src/Main.kt
            --- a/src/Main.kt
            +++ b/src/Main.kt
        """.trimIndent()

        assertEquals(listOf("src/Main.kt"), WorktreePatchPaths.extract(diff))
    }

    @Test
    fun `prose with no headers yields no paths`() {
        assertTrue(WorktreePatchPaths.extract("I would edit src/Main.kt").isEmpty())
    }

    @Test
    fun `ordinary repository paths are safe`() {
        assertFalse(WorktreePatchPaths.isUnsafeRelativePath("src/main/kotlin/Main.kt"))
        assertFalse(WorktreePatchPaths.isUnsafeRelativePath("a.txt"))
    }

    @Test
    fun `absolute paths are refused`() {
        assertTrue(WorktreePatchPaths.isUnsafeRelativePath("/etc/passwd"))
    }

    @Test
    fun `traversal is refused even when it would resolve back inside`() {
        assertTrue(
            WorktreePatchPaths.isUnsafeRelativePath("src/../src/Main.kt"),
            "refused by shape: a resolution-based check has to be right about every platform"
        )
        assertTrue(WorktreePatchPaths.isUnsafeRelativePath("../outside.kt"))
        assertTrue(WorktreePatchPaths.isUnsafeRelativePath("a/b/../../../escape.kt"))
    }

    @Test
    fun `backslash separators cannot smuggle a traversal segment`() {
        assertTrue(WorktreePatchPaths.isUnsafeRelativePath("src\\..\\..\\escape.kt"))
    }

    @Test
    fun `blank and dev-null are refused`() {
        assertTrue(WorktreePatchPaths.isUnsafeRelativePath(""))
        assertTrue(WorktreePatchPaths.isUnsafeRelativePath("   "))
        assertTrue(WorktreePatchPaths.isUnsafeRelativePath("/dev/null"))
    }

    @Test
    fun `firstUnsafe names the offending path and passes a clean list`() {
        assertEquals(
            "../escape.kt",
            WorktreePatchPaths.firstUnsafe(listOf("src/Ok.kt", "../escape.kt", "src/Also.kt"))
        )
        assertNull(WorktreePatchPaths.firstUnsafe(listOf("src/Ok.kt", "docs/README.md")))
    }
}
