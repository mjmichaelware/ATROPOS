/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.parity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceContractTest {

    private fun observation(
        surface: String,
        projects: List<String> = listOf("p1"),
        status: List<String> = listOf("working"),
        completion: List<String> = listOf("verified"),
        gates: Map<String, Boolean> = mapOf("n1" to true)
    ) = SurfaceObservation(surface, projects, status, completion, gates)

    @Test
    fun `matching surfaces hold parity`() {
        val report = SurfaceContract(listOf(observation("cli"), observation("web"))).check()
        assertTrue(report.holds)
        assertTrue(report.render().contains("parity holds"))
    }

    @Test
    fun `a single surface is inconclusive rather than passing`() {
        val report = SurfaceContract(listOf(observation("cli"))).check()
        assertFalse(report.holds, "one surface cannot disagree with itself")
        assertFalse(report.conclusive)
        assertTrue(report.render().contains("inconclusive"))
    }

    @Test
    fun `no observations at all is inconclusive`() {
        assertFalse(SurfaceContract(emptyList()).check().holds)
    }

    @Test
    fun `differing project identity breaks parity`() {
        val report = SurfaceContract(
            listOf(observation("cli"), observation("web", projects = listOf("p2")))
        ).check()
        assertFalse(report.holds)
        assertTrue(report.divergences.any { it.field == "projectIds" })
    }

    @Test
    fun `differing status vocabulary breaks parity`() {
        val report = SurfaceContract(
            listOf(observation("cli"), observation("web", status = listOf("running")))
        ).check()
        assertTrue(report.divergences.any { it.field == "statusTerms" })
        assertTrue(report.render().contains("running"))
    }

    @Test
    fun `a differing gate outcome for the same node breaks parity`() {
        val report = SurfaceContract(
            listOf(observation("cli"), observation("web", gates = mapOf("n1" to false)))
        ).check()
        assertTrue(report.divergences.any { it.field == "gateOutcome:n1" })
    }

    @Test
    fun `gate outcomes only compared where both surfaces observed the node`() {
        val report = SurfaceContract(
            listOf(observation("cli"), observation("web", gates = mapOf("other" to false)))
        ).check()
        assertTrue(report.holds, "a node one surface never saw is not a divergence")
    }

    @Test
    fun `three surfaces are all compared against the reference`() {
        val report = SurfaceContract(
            listOf(observation("cli"), observation("web"), observation("android", status = listOf("idle")))
        ).check()
        assertEquals(3, report.compared)
        assertTrue(report.divergences.any { it.right.contains("android") })
    }
}
