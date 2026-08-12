package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class RecoveryTectonicRibbonTest {
    @Test
    fun keeps_continuity_free_space_and_authorization_visible() {
        val line = RecoveryTectonicRibbon().render(
            RecoveryTectonicRibbon.State("restored", "72%", "ready"),
            120
        )
        assertTrue(line.contains("restored"))
        assertTrue(line.contains("free 72%"))
        assertTrue(line.contains("auth ready"))
    }
}
