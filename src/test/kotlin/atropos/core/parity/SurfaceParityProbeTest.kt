/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.parity

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `SUP.PROV.SURFACE-PARITY` against the live registries.
 *
 * The assertions are about the intersection, not the totals. The CLI offers
 * more than the bridge on purpose — a check that flagged that intended
 * asymmetry every run would be one nobody reads.
 */
class SurfaceParityProbeTest {

    private val probe = SurfaceParityProbe()

    @Test
    fun `every menu action names a route that exists`() {
        val dangling = probe.danglingActions()

        assertTrue(
            dangling.isEmpty(),
            "menu action(s) point at no route, which on a phone is a button that " +
                "silently does nothing: $dangling"
        )
    }

    @Test
    fun `no surface reachable over a port advertises shell, cd or exec`() {
        val forbidden = probe.forbiddenOnPort()

        assertTrue(
            forbidden.isEmpty(),
            "presenting these would mean the bridge had to execute them: $forbidden"
        )
    }

    @Test
    fun `the shared vocabulary is non-empty, so parity is actually being checked`() {
        assertTrue(
            probe.sharedVocabulary().isNotEmpty(),
            "an empty intersection would make the parity check vacuously true"
        )
    }

    @Test
    fun `observed surfaces agree on the vocabulary they share`() {
        val report = probe.check()

        assertTrue(report.conclusive, "fewer than two surfaces cannot disagree")
        assertTrue(report.holds, "surfaces diverged: ${report.divergences}")
    }

    @Test
    fun `the CLI is allowed to offer more than the bridge`() {
        assertTrue(
            probe.cliOnly().isNotEmpty(),
            "the CLI reaching further than a port-exposed surface is the design"
        )
    }
}
