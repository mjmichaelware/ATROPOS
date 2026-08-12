package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BaselineSnapshotsTest {
    @Test
    fun exposes_required_terminal_widths_and_tracked_files() {
        val snapshots = BaselineSnapshots()
        assertEquals(listOf(40, 80, 120, 160), snapshots.widths())
        assertTrue(snapshots.all("landing").all { it.available })
    }

    @Test
    fun responsive_grammar_consumes_the_baseline_width_contract() {
        assertEquals(BaselineSnapshots.REQUIRED_WIDTHS, ResponsiveNativeGrammar().baselineWidths())
    }
}
