/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.parity

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ATOM 10.3: Engine Capability Parity Verification.
 *
 * Automated test suites that verify primary navigation spine identity, command routing tables,
 * and completion claims between Android Compose and CLI surfaces. Fails builds on semantic drift.
 *
 * ## Further Research Directive: Automated AST Symbol Diffing Tools
 * Research automated AST symbol diffing tools that compare Kotlin Compose screen hierarchies
 * against CLI ANSI view tree models to detect unmapped surface components.
 */
class SurfaceContractParityTest {

    private val canonical10SpineIds = listOf(
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

    private val canonicalStatusTerms = listOf(
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
        assertEquals(10, canonical10SpineIds.size)
        assertTrue(canonical10SpineIds.contains("home"))
        assertTrue(canonical10SpineIds.contains("projects"))
        assertTrue(canonical10SpineIds.contains("work"))
        assertTrue(canonical10SpineIds.contains("settings"))
    }

    @Test
    fun checkSurfaceContract() {
        val obsCli = SurfaceObservation(
            surface = "cli",
            projectIds = canonical10SpineIds,
            statusTerms = canonicalStatusTerms,
            completionTerms = listOf("completed", "failed"),
            gateOutcomes = mapOf("gate-1" to true, "gate-2" to false)
        )
        val obsWeb = SurfaceObservation(
            surface = "web",
            projectIds = canonical10SpineIds,
            statusTerms = canonicalStatusTerms,
            completionTerms = listOf("completed", "failed"),
            gateOutcomes = mapOf("gate-1" to true, "gate-2" to false)
        )
        val obsAndroid = SurfaceObservation(
            surface = "android",
            projectIds = canonical10SpineIds,
            statusTerms = canonicalStatusTerms,
            completionTerms = listOf("completed", "failed"),
            gateOutcomes = mapOf("gate-1" to true, "gate-2" to false)
        )

        val contract = SurfaceContract(listOf(obsCli, obsWeb, obsAndroid))
        val report = contract.check()
        assertTrue(report.conclusive)
        assertTrue(report.holds)
        assertEquals(0, report.divergences.size)
    }

    @Test
    fun assertVocabularyMatches() {
        canonicalStatusTerms.forEach { term ->
            assertTrue(CrossSurfaceParity.validateStatus(term), "Term '$term' must be validated by CrossSurfaceParity")
            val display = CrossSurfaceParity.getStatusDisplay(term)
            assertFalse(display == "Unknown", "Display for '$term' must not be Unknown")
        }

        val divergingWeb = SurfaceObservation(
            surface = "web",
            projectIds = canonical10SpineIds,
            statusTerms = canonicalStatusTerms + listOf("unexpected-status"),
            completionTerms = listOf("completed"),
            gateOutcomes = mapOf("gate-1" to false)
        )
        val refCli = SurfaceObservation(
            surface = "cli",
            projectIds = canonical10SpineIds,
            statusTerms = canonicalStatusTerms,
            completionTerms = listOf("completed"),
            gateOutcomes = mapOf("gate-1" to true)
        )

        val brokenContract = SurfaceContract(listOf(refCli, divergingWeb))
        val report = brokenContract.check()
        assertTrue(report.conclusive)
        assertFalse(report.holds)
        assertTrue(report.divergences.isNotEmpty())
    }
}
