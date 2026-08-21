/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class StatusRenderersTest {

    private val theme = TerminalTheme(
        atropos.cli.config.ConfigurationManager(),
        tierOverride = atropos.cli.ui.design.ColorTier.NONE
    )

    @Test
    fun `StatusMemoryRenderer renders correctly at different widths`() {
        val renderer = StatusMemoryRenderer(theme = theme)
        val list40 = renderer.render(40)
        val list80 = renderer.render(80)

        assertTrue(list40.isNotEmpty())
        assertTrue(list80.isNotEmpty())
        list40.forEach { line ->
            assertTrue(TerminalText.cellWidth(line) <= 40)
        }
    }

    @Test
    fun `StatusSecurityRenderer renders correctly at different widths`() {
        val renderer = StatusSecurityRenderer(theme = theme)
        val list40 = renderer.render(40)
        val list80 = renderer.render(80)

        assertTrue(list40.isNotEmpty())
        assertTrue(list80.isNotEmpty())
        list40.forEach { line ->
            assertTrue(TerminalText.cellWidth(line) <= 40)
        }
    }

    @Test
    fun `StatusCiRenderer renders correctly at different widths`() {
        val renderer = StatusCiRenderer(theme = theme)
        val list40 = renderer.render(40)
        val list80 = renderer.render(80)

        assertTrue(list40.isNotEmpty())
        assertTrue(list80.isNotEmpty())
        list40.forEach { line ->
            assertTrue(TerminalText.cellWidth(line) <= 40)
        }
    }

    @Test
    fun `StatusPaidEmergencyRenderer renders correctly at different widths`() {
        val renderer = StatusPaidEmergencyRenderer(theme = theme)
        val list40 = renderer.render(40)
        val list80 = renderer.render(80)

        assertTrue(list40.isNotEmpty())
        assertTrue(list80.isNotEmpty())
        list40.forEach { line ->
            assertTrue(TerminalText.cellWidth(line) <= 40)
        }
    }

    @Test
    fun `StatusAssetsRenderer renders correctly at different widths`() {
        val renderer = StatusAssetsRenderer(theme = theme)
        val list40 = renderer.render(40)
        val list80 = renderer.render(80)

        assertTrue(list40.isNotEmpty())
        assertTrue(list80.isNotEmpty())
        list40.forEach { line ->
            assertTrue(TerminalText.cellWidth(line) <= 40)
        }
    }

    @Test
    fun `StatusStorageRenderer renders correctly at different widths`() {
        val renderer = StatusStorageRenderer(theme = theme)
        val constitution = atropos.core.storage.StorageConstitution(10, 100, 90, emptyList())
        val supervisor = atropos.core.storage.StorageSupervisor()
        val policy = atropos.core.storage.RetentionPolicy()
        val list40 = renderer.renderStatus(constitution, supervisor, 40)
        val policy40 = renderer.renderPolicy(policy, 40)

        assertTrue(list40.isNotEmpty())
        assertTrue(policy40.isNotEmpty())
        list40.forEach { line ->
            assertTrue(TerminalText.cellWidth(line) <= 40)
        }
    }

    @Test
    fun `StatusAuthRenderer renders correctly at different widths`() {
        val renderer = StatusAuthRenderer(theme = theme)
        val list40 = renderer.renderVerify(emptyList(), 40)
        val cascade40 = renderer.renderCascade(emptyList(), 40)

        assertTrue(list40.isNotEmpty())
        assertTrue(cascade40.isNotEmpty())
    }

    @Test
    fun `StatusThemeRenderer renders correctly at different widths`() {
        val renderer = StatusThemeRenderer(theme = theme)
        val list40 = renderer.renderList("default", 40)
        val preview40 = renderer.renderPreview(40)

        assertTrue(list40.isNotEmpty())
        assertTrue(preview40.isNotEmpty())
    }

    @Test
    fun `StatusRouteRenderer renders correctly at different widths`() {
        val renderer = StatusRouteRenderer(theme = theme)
        val task = atropos.core.provider.ProviderTask(atropos.core.provider.ProviderTaskKind.RESEARCH, atropos.core.provider.ApiCapability.COMPLETION)
        val decision = atropos.core.provider.RoutePolicyDecision(task, null, emptyList(), emptyList())
        val result = atropos.core.provider.adapter.AdapterRouteResult("prompt", decision, null, null, "note")
        val list40 = renderer.renderRoute(result, 40)

        assertTrue(list40.isNotEmpty())
    }
}
