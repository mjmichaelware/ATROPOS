/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.TerminalTheme
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Four accents were added with no way to select one but an environment
 * variable — which a phone operator cannot set before tapping a launcher. A
 * palette nobody can select is decoration.
 */
class ThemePreferenceTest {

    @Test
    fun `terminal theme rejects raw escape input before painting`() {
        val theme = TerminalTheme(ConfigurationManager(), tierOverride = ColorTier.NONE)
        assertFailsWith<IllegalArgumentException> {
            theme.strong("unsafe\u001B[31mtext")
        }
    }

    private fun home(): String = Files.createTempDirectory("atropos-theme").toString()

    @Test
    fun `a stored choice survives and resolves`() {
        val home = home()

        assertTrue(ThemePreference.write("atropos-purple", home))

        assertEquals("atropos-purple", ThemePreference.read(home))
        assertEquals("atropos-purple", ThemePreference.resolve({ null }, home))
    }

    @Test
    fun `an unknown theme is refused rather than stored`() {
        val home = home()

        assertFalse(ThemePreference.write("chartreuse", home))
        assertNull(ThemePreference.read(home))
    }

    @Test
    fun `the environment overrides the stored choice`() {
        val home = home()
        ThemePreference.write("atropos-purple", home)

        val resolved = ThemePreference.resolve({ name ->
            if (name == "ATROPOS_THEME") "atropos-blue" else null
        }, home)

        assertEquals("atropos-blue", resolved, "an explicit variable asked for that theme")
    }

    @Test
    fun `no stored choice resolves to the default`() {
        assertEquals(ThemeCatalog.DEFAULT_ID, ThemePreference.resolve({ null }, home()))
    }

    @Test
    fun `reset returns to the default`() {
        val home = home()
        ThemePreference.write("atropos-orange", home)

        ThemePreference.clear(home)

        assertNull(ThemePreference.read(home))
        assertEquals(ThemeCatalog.DEFAULT_ID, ThemePreference.resolve({ null }, home))
    }

    @Test
    fun `every catalog theme can actually be selected`() {
        val home = home()

        ThemeCatalog.all.forEach { theme ->
            assertTrue(ThemePreference.write(theme.id, home), "${theme.id} must be selectable")
            assertEquals(theme.id, ThemePreference.resolve({ null }, home))
        }
    }
}
