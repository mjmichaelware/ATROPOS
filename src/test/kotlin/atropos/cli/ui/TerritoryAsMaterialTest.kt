/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.territory.TerritoryAssignment
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TerritoryAsMaterialTest {
    private fun assignment(
        ownerId: String = "operator-1",
        allowedPrefix: String = "/home/user/project",
        readOnly: Boolean = false
    ): TerritoryAssignment = TerritoryAssignment(
        ownerId = ownerId,
        ownerRole = "operator",
        allowedPrefix = allowedPrefix,
        readOnly = readOnly,
        grantedAt = Instant.now(),
        expiresAt = null,
        parentTerritoryId = null,
        maxFileSizeBytes = 1024 * 1024,
        boundActorIdentity = null
    )

    @Test
    fun material_extracts_assignment_properties() {
        val material = TerritoryAsMaterial()
        val assignment = this.assignment()

        val materialResult = material.material(assignment)

        assertEquals("operator-1", materialResult.owner)
        assertEquals("/home/user/project", materialResult.prefix)
        assertEquals("writable", materialResult.state)
        assertFalse(materialResult.readOnly)
    }

    @Test
    fun read_only_assignment_marked_read_only() {
        val material = TerritoryAsMaterial()
        val assignment = assignment(readOnly = true)

        val materialResult = material.material(assignment)

        assertEquals("read-only", materialResult.state)
        assertTrue(materialResult.readOnly)
    }

    @Test
    fun render_formats_material_for_display() {
        val material = TerritoryAsMaterial()
        val assignment = assignment()

        val rendered = material.render(assignment, 80)

        assertTrue(rendered.contains("territory"))
        assertTrue(rendered.contains("/home/user/project"))
        assertTrue(rendered.contains("operator-1"))
        assertTrue(rendered.contains("writable"))
    }

    @Test
    fun render_ellipsizes_long_prefix() {
        val material = TerritoryAsMaterial()
        val assignment = assignment(allowedPrefix = "/a".repeat(100))

        val rendered = material.render(assignment, 40)

        assertTrue(rendered.length <= 40)
        assertTrue(rendered.endsWith("…"))
    }
}