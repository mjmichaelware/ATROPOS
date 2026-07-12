package atropos.core.territory

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TerritoryServiceTest {
    @Test
    fun assignAndCheckAllows() {
        val dir = Files.createTempDirectory("territory-test-")
        val store = TerritoryStore(dir)
        val svc = TerritoryService(store)

        val t = svc.assign("owner1", "WORKER", "src/main/kotlin/atropos/core")
        assertTrue(t.allows("src/main/kotlin/atropos/core/director/DirectorService.kt"))
        assertFalse(t.allows("src/main/kotlin/atropos/cli/CommandRouter.kt"))
    }

    @Test
    fun revokeRemovesAssignment() {
        val dir = Files.createTempDirectory("territory-revoke-")
        val store = TerritoryStore(dir)
        val svc = TerritoryService(store)

        val t = svc.assign("owner2", "WORKER", "src/test")
        assertEquals(1, svc.getAll().size)
        svc.revoke(t.id)
        assertEquals(0, svc.getAll().size)
    }

    @Test
    fun violationRecordedAndResolved() {
        val dir = Files.createTempDirectory("territory-viol-")
        val store = TerritoryStore(dir)
        val svc = TerritoryService(store)

        val v = svc.checkViolation("terr-1", "src/evil/leak.kt", "outside allowed prefix")
        assertEquals(1, svc.getViolations().size)

        svc.resolveViolation(v.id)
        assertTrue(svc.getViolations().first().resolved)
    }

    @Test
    fun assignmentWithExpiry() {
        val store = TerritoryStore()
        val svc = TerritoryService(store)
        val t = svc.assign("expire-test", "SPECIALIST", "src/", expiresInMinutes = 0)
        assertFalse(t.allows("src/anything.kt"))
    }
}
