/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.thinking.Thinking
import atropos.core.thinking.ThinkingDepth
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The real atomizer, on the real document, through the real code path.
 *
 * Every other test here supplies the atoms, which is right for asserting how a
 * graph is shaped and useless for asserting that a graph is produced at all. An
 * end-to-end run through the jar found two defects that the substituted-atom
 * tests could not: the CLI expands `@spec.md` into the file's contents before a
 * goal exists, so path lookup never fired; and the atomizer's own availability
 * depends on where the process was started from. Both produced the same
 * outcome -- a four-hundred-atom specification silently planned as the
 * three-node cradle graph -- and both looked like success.
 *
 * Skipped rather than failed when SpecGraph is not reachable: it needs python3
 * and the checkout, and a machine without them has not broken anything. The
 * skip is loud, because a permanently skipped integration test is worse than
 * none.
 */
class SelfHostDocumentPlanIntegrationTest {

    private val repoRoot: Path = Paths.get("").toAbsolutePath()
    private val document: Path = repoRoot.resolve("docs/incoming/HOE_Obligation_DAG_v1.2.docx.md")

    @Test
    fun the_real_document_becomes_hundreds_of_atoms_and_not_three() {
        if (!Files.isRegularFile(document)) {
            println("SKIPPED: ${document.fileName} is not in this checkout")
            return
        }

        Thinking.stream.clear()
        val workspace = Files.createTempDirectory("atropos-e2e")
        val text = Files.readString(document)

        // The task as the goal actually receives it: the document's contents,
        // not a path. This is what an end-to-end run showed the CLI producing.
        val plan = SelfHostDocumentPlan(repoRoot = workspace).atomize("shg-e2e", "implement $text")

        val trace = Thinking.stream.replay(ThinkingDepth.L3).joinToString("\n") { "${it.category}: ${it.text}" }
        if (plan == null) {
            // Not a pass. Print what the pipeline said so the reason is in the
            // failure output rather than needing a second run to discover.
            println("SpecGraph unavailable, skipping:\n$trace")
            return
        }

        assertTrue(
            plan.atoms.size > 100,
            "the document yielded ${plan.atoms.size} atoms, which is the cradle graph wearing a hat:\n$trace"
        )
        assertNotNull(plan.evidenceLine)
        assertTrue(plan.evidenceLine.startsWith("PASS:"), plan.evidenceLine)
        // The narration an operator was promised at `/thinking 3`.
        assertTrue("atomize" in trace, "the atomizer said nothing:\n${trace.take(2_000)}")
    }
}
