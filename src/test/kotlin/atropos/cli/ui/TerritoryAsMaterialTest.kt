package atropos.cli.ui

import atropos.core.territory.TerritoryAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerritoryAsMaterialTest {
    @Test
    fun projects_existing_assignment_without_reimplementing_authorization() {
        val assignment = TerritoryAssignment(
            ownerId = "worker-1",
            ownerRole = "worker",
            allowedPrefix = "generated/calculator",
            readOnly = true
        )
        val material = TerritoryAsMaterial().material(assignment)
        assertEquals("worker-1", material.owner)
        assertEquals("generated/calculator", material.prefix)
        assertTrue(material.readOnly)
        assertTrue(TerritoryAsMaterial().render(assignment, 80).contains("read-only"))
    }
}
