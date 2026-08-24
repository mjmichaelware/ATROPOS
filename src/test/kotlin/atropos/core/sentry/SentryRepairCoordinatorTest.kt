package atropos.core.sentry

import atropos.core.evaluation.EvidenceStore
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SentryRepairCoordinatorTest {
    @Test
    fun `prepare maps frame into territory and writes evidence`() {
        val root = Files.createTempDirectory("atropos-sentry-")
        val coordinator = SentryRepairCoordinator(repoRoot = root, evidenceStore = EvidenceStore(root))
        val context = coordinator.prepare(
            SentryIssue(
                id = "42", title = "Crash", culprit = "src/App.kt",
                frames = listOf(SentryStackFrame("${root.toAbsolutePath()}/src/App.kt", 9)), raw = "raw"
            ),
            listOf("src")
        )

        assertEquals("src/App.kt", context.relativeFile)
        assertEquals(9, context.lineNumber)
        assertEquals("src", context.territory.single())
        assertEquals(context.evidenceHash.length, 64)
        assertEquals(true, Files.exists(root.resolve(".atropos/evidence").resolve(context.evidenceHash.take(2)).resolve(context.evidenceHash)))
    }

    @Test
    fun `prepare refuses frame outside declared territory`() {
        val root = Files.createTempDirectory("atropos-sentry-")
        val coordinator = SentryRepairCoordinator(repoRoot = root, evidenceStore = EvidenceStore(root))

        assertThrows(IllegalArgumentException::class.java) {
            coordinator.prepare(
                SentryIssue("42", "Crash", "", listOf(SentryStackFrame("${root.toAbsolutePath()}/test/App.kt", 1)), "raw"),
                listOf("src")
            )
        }
    }
}
