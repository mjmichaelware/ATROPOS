/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FederatedIssueSolveCoordinatorTest {

    @Test
    fun `executes full federated pipeline`() {
        val coordinator = FederatedIssueSolveCoordinator()
        val problem = "Issue in astropy/wcs/wcs.py where fits header parsing crashes on NaN."
        val patch = coordinator.executeFullPipeline("astropy__astropy-11693", problem)

        assertNotNull(patch)
        assertTrue(patch.contains("patched logic resolving astropy__astropy-11693"))
    }
}
