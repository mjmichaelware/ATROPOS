/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import atropos.core.ast.CompilerState
import atropos.core.ast.DecomposedAttentionNode
import atropos.core.ast.AttentionRole
import atropos.core.ast.MdpCompilerState
import atropos.core.ast.MonteCarloBranchPruner
import atropos.core.ast.TopologicalMutationVector
import atropos.core.ast.CodebaseDeltaTreeTracker
import atropos.core.ast.PreconditionChecker
import atropos.core.ast.ErrorGradientExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdversarialValidatorTest {

    @Test
    fun `MDP compiler state performs mock code transitions`() {
        val mdp = MdpCompilerState("initial")
        val state = mdp.transition("ADD", " + next")
        assertEquals("initial + next", state.code)
    }

    @Test
    fun `MonteCarloBranchPruner filters failing mutations`() {
        val pruner = MonteCarloBranchPruner()
        val list = pruner.sampleAndPrune(
            CompilerState("code", 0, emptyList()),
            listOf(" ok", " fail"),
            compileCheck = { !it.contains("fail") }
        )
        assertEquals(listOf(" ok"), list)
    }

    @Test
    fun `DecomposedAttentionNode respects viewer vs editor roles`() {
        val viewer = DecomposedAttentionNode("1", AttentionRole.VIEWER, "data")
        assertTrue(viewer.processContext().startsWith("View-Only:"))
    }

    @Test
    fun `CodebaseDeltaTreeTracker isolates code delta lines`() {
        val tracker = CodebaseDeltaTreeTracker()
        val diff = tracker.trackTreeDelta("line1\nline2", "line1\nchanged")
        assertEquals("changed", diff)
    }

    @Test
    fun `PreconditionChecker requires zero HIG and HUD values`() {
        assertTrue(PreconditionChecker.verifyCommitPrecondition(0.0, 0.0))
        assertFalse(PreconditionChecker.verifyCommitPrecondition(1.0, 0.0))
    }

    @Test
    fun `ErrorGradientExtractor isolates compiler error statements`() {
        val extractor = ErrorGradientExtractor()
        val log = "info: starting\ne: compiler failure\ninfo: exit"
        assertEquals(listOf("e: compiler failure"), extractor.extractFailingSubgraph(log))
    }

    @Test
    fun `OnDeviceAdversarialValidator flags syntax issues`() {
        val invalid = OnDeviceAdversarialValidator.validate("fun (")
        assertFalse(invalid.syntaxValid)

        val missing = OnDeviceAdversarialValidator.validate("import custom.dep")
        assertTrue(missing.missingImports.contains("unknown.dependency"))
    }

    @Test
    fun `AsyncFanOutController maps elements concurrently`() {
        val controller = AsyncFanOutController()
        val out = controller.fanOutAndCombine(listOf(1, 2, 3)) { it * 2 }
        assertEquals(listOf(2, 4, 6), out)
    }

    @Test
    fun `ManifestOrchestrator sorts nodes topologically`() {
        val orch = ManifestOrchestrator()
        val manifest = orch.generateExecutionManifest(
            listOf("B", "A"),
            mapOf("B" to listOf("A"))
        )
        assertTrue(manifest.contains("\"A\",\"B\"") || manifest.contains("\"A\", \"B\""))
    }
}
