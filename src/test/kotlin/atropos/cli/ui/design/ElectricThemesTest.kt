/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `SUP.UX.ANSI-SCHEME-TOKENS`: the four accents Source Doc 5 asks for, and the
 * readability invariant that makes them safe — a theme may change the product's
 * identity colour, never the colour that answers "did it work?".
 */
class ElectricThemesTest {

    @Test
    fun `red stays the default and all four accents are registered`() {
        assertEquals("atropos-dark", ThemeCatalog.DEFAULT_ID)
        assertEquals("atropos-dark", ThemeCatalog.byId(null).id)

        val ids = ThemeCatalog.all.map { it.id }
        assertTrue(ids.containsAll(listOf("atropos-blue", "atropos-orange", "atropos-yellow", "atropos-purple")))
    }

    @Test
    fun `every accent theme defines every role at every tier`() {
        // ThemePalette refuses a partial map at construction, so building all
        // four is the assertion: a role added later fails here rather than
        // rendering unstyled in whichever theme nobody looked at.
        ElectricThemes.palettes().forEach { palette ->
            Role.entries.forEach { role ->
                ColorTier.entries.forEach { tier ->
                    palette.style(role, tier)
                }
            }
        }
    }

    @Test
    fun `outcome colours are identical across every accent`() {
        val outcomeRoles = listOf(Role.STATUS_VERIFIED, Role.STATUS_ERROR, Role.STATUS_PENDING)
        val palettes = ElectricThemes.palettes() + ThemeCatalog.byId("atropos-dark")

        outcomeRoles.forEach { role ->
            val rendered = palettes.map { it.style(role, ColorTier.TRUECOLOR) }.distinct()
            assertEquals(
                1, rendered.size,
                "$role must not change with the accent; a reader should not need to know the theme"
            )
        }
    }

    @Test
    fun `the accent actually changes the brand`() {
        val blue = ElectricThemes.palette(ElectricThemes.BLUE).style(Role.BRAND, ColorTier.TRUECOLOR)
        val purple = ElectricThemes.palette(ElectricThemes.PURPLE).style(Role.BRAND, ColorTier.TRUECOLOR)

        assertNotEquals(blue, purple)
        assertTrue(blue.contains("0;122;255"))
    }

    @Test
    fun `selection stays legible by pairing the accent with a contrasting ink`() {
        ElectricThemes.ACCENTS.forEach { accent ->
            val selection = ElectricThemes.palette(accent)
                .style(Role.ACCENT_SELECTION, ColorTier.TRUECOLOR)

            assertTrue(selection.contains("48;2;"), "${accent.id} selection needs a background")
            assertTrue(
                selection.contains("38;2;255;255;255") || selection.contains("38;2;9;9;11"),
                "${accent.id} selection must pair with white or near-black, not a mid tone"
            )
        }
    }

    @Test
    fun `every accent survives a terminal with only the basic sixteen colours`() {
        ElectricThemes.palettes().forEach { palette ->
            assertTrue(
                palette.style(Role.BRAND, ColorTier.BASIC).isNotEmpty(),
                "${palette.id} disappears on a 16-colour terminal"
            )
        }
    }

    @Test
    fun `no colour is emitted when the terminal supports none`() {
        ElectricThemes.palettes().forEach { palette ->
            assertEquals("", palette.style(Role.BRAND, ColorTier.NONE))
        }
    }
}
