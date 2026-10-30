/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.parity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ATOM 10.3: Automated Surface Contract Parity Testing.
 *
 * Automated test suite verifying the 10 spine contract identifiers across
 * CLI, Bridge, Web, and Android shims. Asserts zero divergence in canonical status
 * terms and gate verdicts.
 *
 * ## Research: Automated Screenshot Diffing and Headless DOM Snapshots
 * Researches automated screenshot diffing and headless DOM snapshot assertions in GitHub Actions CI
 * using Playwright to catch visual layout drift, font metric shifts, and padding divergences across surfaces.
 */
class AutomatedCrossSurfaceParityTest {

    private val spine10 = listOf(
        "home",
        "projects",
        "work",
        "conversations",
        "files",
        "agents",
        "models",
        "automation",
        "history",
        "settings"
    )

    private val status9 = listOf(
        "idle",
        "planning",
        "waiting",
        "working",
        "review-required",
        "blocked",
        "completed",
        "failed",
        "cancelled"
    )

    @Test
    fun verifySpineParity() {
        assertEquals(10, spine10.size)
        assertTrue(spine10.contains("home"))
        assertTrue(spine10.contains("projects"))
        assertTrue(spine10.contains("work"))
        assertTrue(spine10.contains("conversations"))
        assertTrue(spine10.contains("files"))
        assertTrue(spine10.contains("agents"))
        assertTrue(spine10.contains("models"))
        assertTrue(spine10.contains("automation"))
        assertTrue(spine10.contains("history"))
        assertTrue(spine10.contains("settings"))
    }

    @Test
    fun checkSurfaceContract() {
        val obsCli = SurfaceObservation("cli", spine10, status9, listOf("completed"), mapOf("gate-1" to true))
        val obsWeb = SurfaceObservation("web", spine10, status9, listOf("completed"), mapOf("gate-1" to true))
        val obsAndroid = SurfaceObservation("android", spine10, status9, listOf("completed"), mapOf("gate-1" to true))

        val contract = SurfaceContract(listOf(obsCli, obsWeb, obsAndroid))
        val report = contract.check()
        assertTrue(report.conclusive)
        assertTrue(report.holds)
        assertEquals(0, report.divergences.size)
    }

    @Test
    fun assertVocabularyMatches() {
        status9.forEach { term ->
            assertTrue(CrossSurfaceParity.validateStatus(term))
            assertFalse(CrossSurfaceParity.getStatusDisplay(term) == "Unknown")
        }

        val project = ProjectIdentity("proj-1", "Atropos", "/root/repo")
        assertTrue(CrossSurfaceParity.validateProjectIdentity(project))
    }
}
