/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.territory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerritoryEnforcerTest {

    @Test
    fun allows_paths_strictly_under_allowed_territory_prefixes() {
        val enforcer = TerritoryEnforcer(listOf("src/main/kotlin", "docs"))

        assertTrue(enforcer.isAllowed("src/main/kotlin/atropos/Main.kt"))
        assertTrue(enforcer.isAllowed("docs/AGENTS.md"))

        // Exact matches
        assertTrue(enforcer.isAllowed("docs"))
        assertTrue(enforcer.isAllowed("src/main/kotlin"))

        // Out of territory
        assertFalse(enforcer.isAllowed("src/test/kotlin/atropos/MainTest.kt"))
        assertFalse(enforcer.isAllowed("build.gradle.kts"))
        assertFalse(enforcer.isAllowed("README.md"))
    }

    @Test
    fun returns_false_for_empty_or_malformed_traversal_paths() {
        val enforcer = TerritoryEnforcer(listOf("src"))

        assertFalse(enforcer.isAllowed(""))
        assertFalse(enforcer.isAllowed("src/../build.gradle.kts"))
        assertFalse(enforcer.isAllowed("../src"))
    }

    @Test
    fun returns_first_outside_correctly() {
        val enforcer = TerritoryEnforcer(listOf("src", "docs"))

        assertNull(enforcer.firstOutside(listOf("src/A.kt", "docs/B.md")))
        assertEquals("build.gradle.kts", enforcer.firstOutside(listOf("src/A.kt", "build.gradle.kts", "docs/B.md")))
    }
}
