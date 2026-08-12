/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.territory

import atropos.core.director.DirectorService
import atropos.core.policy.ActionActor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `SUP.VERIF.TERRITORY-MONITOR-COST`: the O(N) claim is instrumented rather
 * than asserted in prose.
 *
 * The counter lives in [TerritoryGrantService] because that is the single
 * place every containment check passes through — a count maintained by callers
 * would undercount the moment a caller forgot it, and an undercount makes the
 * claim look better than it is.
 */
class TerritoryMonitorCostIntegrationTest {

    private fun service() = TerritoryGrantService(TerritoryService(director = DirectorService()))

    @Test
    fun `every containment check is counted`() {
        val grants = service()

        repeat(5) {
            grants.firstPathOutsideTerritory(ActionActor.HumanOwner, listOf("src/main/kotlin"))
        }

        assertEquals(5, grants.monitorCost.snapshot().checks)
    }

    @Test
    fun `cost per node stays bounded as checks accumulate`() {
        val grants = service()

        repeat(50) {
            grants.firstPathOutsideTerritory(
                ActionActor.HierarchyNode("worker", "n$it"),
                listOf("src/main/kotlin/atropos")
            )
        }

        val snapshot = grants.monitorCost.snapshot()
        assertTrue(snapshot.checks > 0)
        assertTrue(
            snapshot.isLinearShaped(),
            "monitoring cost grew faster than the hierarchy claim allows: ${snapshot.render()}"
        )
    }

    @Test
    fun `the snapshot renders the numbers the claim rests on`() {
        val grants = service()
        grants.firstPathOutsideTerritory(ActionActor.HumanOwner, listOf("src"))

        val rendered = grants.monitorCost.snapshot().render()

        assertTrue(rendered.contains("checks="))
        assertTrue(rendered.contains("perNode="))
        assertTrue(rendered.contains("linear="))
    }

    @Test
    fun `an uncounted service reports nothing rather than a flattering zero-cost claim`() {
        val fresh = TerritoryMonitorCost().snapshot()

        assertEquals(0, fresh.checks)
        assertEquals(0.0, fresh.checksPerNode)
        assertTrue(fresh.isLinearShaped(), "no observations cannot disprove the claim either")
    }
}
