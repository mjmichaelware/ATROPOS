package atropos.core.director

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectorServiceTest {
    @Test
    fun observeAndRetrieve() {
        val dir = java.nio.file.Files.createTempDirectory("director-test-")
        val store = DirectorStore(dir)
        val svc = DirectorService(store, dir)

        val obs = svc.observe(ObservationKind.DIFF_DRIFT, DriftSeverity.ADVISORY, "test", "test observation")
        assertEquals(ObservationKind.DIFF_DRIFT, obs.kind)
        assertEquals(DriftSeverity.ADVISORY, obs.severity)

        val all = store.readAll()
        assertTrue(all.any { it.id == obs.id })
    }

    @Test
    fun advisoryReportListsUnacknowledged() {
        val dir = java.nio.file.Files.createTempDirectory("director-report-")
        val store = DirectorStore(dir)
        val svc = DirectorService(store, dir)

        svc.observe(ObservationKind.DIFF_DRIFT, DriftSeverity.INFO, "test", "info obs")
        svc.observe(ObservationKind.TERRITORY_VIOLATION, DriftSeverity.WARNING, "test", "violation obs")

        val report = svc.advisoryReport()
        assertEquals(2, report.observations.size)
        assertEquals(1, report.territoryViolations)
    }

    @Test
    fun acknowledgeAndDismiss() {
        val dir = java.nio.file.Files.createTempDirectory("director-ack-")
        val store = DirectorStore(dir)
        val svc = DirectorService(store, dir)

        val obs = svc.observe(ObservationKind.POLICY_VIOLATION, DriftSeverity.WARNING, "test", "policy")
        assertEquals(1, store.unacknowledged().size)

        svc.acknowledge(obs.id)
        assertEquals(0, store.unacknowledged().size)

        val obs2 = svc.observe(ObservationKind.COMPILE_ERROR, DriftSeverity.CRITICAL, "test", "compile fail")
        svc.dismiss(obs2.id)
        assertEquals(0, store.unacknowledged().size)
    }

    @Test
    fun promotionAdvisoryBindsObservationsToGoalTerritoryAndFiles() {
        val dir = java.nio.file.Files.createTempDirectory("director-promotion-")
        val store = DirectorStore(dir)
        val svc = DirectorService(store, dir)

        svc.observe(
            ObservationKind.TERRITORY_VIOLATION,
            DriftSeverity.WARNING,
            "test",
            "outside territory",
            files = listOf("src/main/kotlin/atropos/core/agent/SelfHostGoalService.kt"),
            goalId = "goal-1",
            territoryId = "terr-core"
        )
        svc.observe(
            ObservationKind.DIFF_DRIFT,
            DriftSeverity.ADVISORY,
            "test",
            "unrelated",
            files = listOf("docs/readme.md"),
            goalId = "goal-2",
            territoryId = "terr-docs"
        )

        val advisory = svc.advisoryBeforePromotion(
            goalId = "goal-1",
            territoryIds = listOf("terr-core"),
            files = listOf("src/main/kotlin/atropos/core/agent/SelfHostGoalService.kt")
        )

        assertEquals(false, advisory.allowed)
        assertEquals(1, advisory.blockingObservations.size)
        assertEquals("goal-1", store.readAll().first { it.kind == ObservationKind.TERRITORY_VIOLATION }.goalId)
    }

    @Test
    fun redactsObservationDetailsPathsAndSymbolsBeforePersistenceWithoutChangingActiveAdvisory() {
        val dir = java.nio.file.Files.createTempDirectory("director-redaction-")
        val store = DirectorStore(dir)
        val svc = DirectorService(store, dir)
        val path = "src/main/kotlin/atropos/core/director/DirectorStore.kt"
        val symbol = "DirectorStore.appendObservation"
        val details = "api_key=super-secret-value at $path#$symbol"

        svc.observe(
            kind = ObservationKind.TERRITORY_VIOLATION,
            severity = DriftSeverity.WARNING,
            source = "test",
            details = details,
            files = listOf(path),
            symbols = listOf(symbol),
            goalId = "goal-1",
            territoryId = "territory-1"
        )

        val persisted = java.nio.file.Files.readString(dir.resolve(".atropos/director/observations.jsonl"))
        assertFalse(persisted.contains(details))
        assertFalse(persisted.contains(path))
        assertFalse(persisted.contains(symbol))
        assertTrue(persisted.contains("<redacted:details:"))
        assertTrue(persisted.contains("<redacted:path:"))
        assertTrue(persisted.contains("<redacted:symbol:"))

        val advisory = svc.advisoryBeforePromotion(
            goalId = "goal-1",
            territoryIds = listOf("territory-1"),
            files = listOf(path)
        )
        assertFalse(advisory.allowed)
        assertEquals(1, advisory.blockingObservations.size)
    }
}
