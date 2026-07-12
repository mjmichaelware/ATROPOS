package atropos.core.director

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
