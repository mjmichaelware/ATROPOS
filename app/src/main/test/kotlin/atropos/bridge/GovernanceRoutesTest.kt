/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.core.phase20.AuthorityAmendment
import atropos.core.phase20.GovernanceCounts
import atropos.core.phase20.ImprovementProposal
import atropos.core.phase20.MetricDeclaration
import atropos.core.phase20.ObservationPeriod
import atropos.core.storage.RetentionTier
import atropos.core.storage.StorageClass
import atropos.core.storage.StorageConstitution
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GovernanceRoutesTest {

    private val now: Instant = Instant.parse("2026-08-04T00:00:00Z")

    private fun proposal() = ImprovementProposal(
        id = "prop-1",
        proposedBy = "advisor",
        summary = "reduce false verified",
        necessity = listOf("hash-1"),
        baseline = "3%",
        target = "1%",
        guardrails = listOf("no gate weakening"),
        territory = listOf("core/provider"),
        risk = "low",
        rollback = "revert",
        metric = MetricDeclaration("false_verified_rate", 3.0, 1.0, lowerIsBetter = true),
        createdAt = now
    )

    private fun routes(
        proposals: List<ImprovementProposal> = emptyList(),
        amendments: List<AuthorityAmendment> = emptyList(),
        periods: List<ObservationPeriod> = emptyList(),
        counts: GovernanceCounts = GovernanceCounts(),
        storage: StorageConstitution? = null
    ) = BridgeRoutes(
        proposals = { proposals },
        amendments = { amendments },
        observationPeriods = { periods },
        governanceCounts = { counts },
        storage = { storage },
        clock = { now }
    ).table()

    private fun get(table: atropos.bridge.http.HttpRouteTable, path: String) =
        table.resolve(HttpRequest("GET", path, emptyMap(), emptyMap(), ""))

    @Test
    fun `an empty proposal list is served truthfully rather than filled`() {
        val response = get(routes(), "/v1/proposals")
        assertEquals(200, response.status)
        assertTrue(response.body.contains("\"proposals\":[]"))
    }

    @Test
    fun `a proposal exposes its predeclared metric`() {
        val body = get(routes(proposals = listOf(proposal())), "/v1/proposals").body
        assertTrue(body.contains("false_verified_rate"))
        assertTrue(body.contains("\"declared\":true"))
        assertTrue(body.contains("\"baseline\":3.0"))
    }

    @Test
    fun `an incomplete proposal names the missing declarations`() {
        val incomplete = proposal().copy(rollback = "", necessity = emptyList())
        val body = get(routes(proposals = listOf(incomplete)), "/v1/proposals").body
        assertTrue(body.contains("\"complete\":false"))
        assertTrue(body.contains("rollback"))
        assertTrue(body.contains("necessity"))
    }

    @Test
    fun `an open observation period is surfaced as a cooldown with its remaining time`() {
        val body = get(
            routes(periods = listOf(ObservationPeriod("core/provider", now.minusSeconds(60), 600))),
            "/v1/proposals"
        ).body
        assertTrue(body.contains("core/provider"))
        assertTrue(body.contains("remainingSeconds"))
    }

    @Test
    fun `an expired period is not reported as a cooldown`() {
        val body = get(
            routes(periods = listOf(ObservationPeriod("core/provider", now.minusSeconds(9_999), 60))),
            "/v1/proposals"
        ).body
        assertTrue(body.contains("\"cooldowns\":[]"))
    }

    @Test
    fun `an amendment keeps the superseded hash intact alongside its own`() {
        val amendment = AuthorityAmendment(
            "amd-1", "prop-1", "new-hash", "original-hash", "auditor", now, listOf("e1")
        )
        val body = get(routes(amendments = listOf(amendment)), "/v1/amendments").body
        assertTrue(body.contains("\"sha256\":\"new-hash\""))
        assertTrue(body.contains("\"supersedes\":\"original-hash\""))
    }

    @Test
    fun `an unmeasured metric crosses the wire as null, never zero`() {
        val body = get(routes(), "/v1/metrics").body
        assertTrue(body.contains("\"falseVerifiedRate\":null"), body)
        assertTrue(body.contains("unmeasured"))
    }

    @Test
    fun `a measured metric carries its real rate`() {
        val counts = GovernanceCounts(completionClaims = 100, falseVerified = 2)
        val body = get(routes(counts = counts), "/v1/metrics").body
        assertTrue(body.contains("\"falseVerifiedRate\":0.02"), body)
        assertTrue(body.contains("\"healthy\":false"))
    }

    @Test
    fun `an undeclared storage ceiling refuses rather than reporting unlimited`() {
        val response = get(routes(), "/v1/storage")
        assertEquals(503, response.status)
        assertTrue(response.body.contains("storage-unmeasured"))
        assertTrue(response.body.contains("not an unlimited one"))
    }

    @Test
    fun `storage reports what may and may not be reclaimed`() {
        val constitution = StorageConstitution(
            ceilingBytes = 1_000,
            classes = listOf(
                StorageClass("active", RetentionTier.HOT, 200),
                StorageClass("archive", RetentionTier.COLD, 100)
            )
        )
        val body = get(routes(storage = constitution), "/v1/storage").body
        assertTrue(body.contains("\"reclaimableBytes\":100"))
        assertTrue(body.contains("\"tier\":\"hot\""))
        assertTrue(body.contains("\"reclaimable\":false"))
    }

    @Test
    fun `the new routes are advertised`() {
        val body = get(routes(), "/v1/routes").body
        listOf("/v1/proposals", "/v1/amendments", "/v1/metrics", "/v1/storage", "/v1/approvals")
            .forEach { assertTrue(body.contains(it), "route $it not advertised") }
    }
}
