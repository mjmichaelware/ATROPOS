package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.Role
import atropos.core.assets.LocalAssetGenerator

class StatusAssetsRenderer(
    private val generator: LocalAssetGenerator = LocalAssetGenerator(),
    private val theme: TerminalTheme = TerminalTheme(ConfigurationManager())
) {
    private val surface get() = theme.surface

    fun render(): String = render(80).joinToString("\n")

    fun render(width: Int): List<String> {
        val status = generator.status()
        val body = listOf(
            surface.statusRow("local text", if (status.localTextReady) "ready" else "unavailable", if (status.localTextReady) Health.VERIFIED else Health.ERROR, width),
            surface.statusRow("local ansi", if (status.localAnsiReady) "ready" else "unavailable", if (status.localAnsiReady) Health.VERIFIED else Health.ERROR, width),
            surface.statusRow("local svg", if (status.localSvgReady) "ready" else "unavailable", if (status.localSvgReady) Health.VERIFIED else Health.ERROR, width),
            surface.row("artifacts", status.totalArtifacts.toString(), width),
            surface.row("root", status.root.name, width),
            surface.statusRow("huggingface", if (status.huggingFaceConfigured) "configured" else "off", if (status.huggingFaceConfigured) Health.VERIFIED else Health.UNKNOWN, width),
            surface.statusRow("fal", if (status.falConfigured) "configured" else "off", if (status.falConfigured) Health.VERIFIED else Health.UNKNOWN, width),
            surface.statusRow("replicate", if (status.replicateConfigured) "configured" else "off", if (status.replicateConfigured) Health.VERIFIED else Health.UNKNOWN, width),
            surface.statusRow("paid vision", "locked", Health.PENDING, width),
            surface.hint("policy: terminal UI never requires image generation", width)
        )
        return surface.block("ASSET ENGINE STATUS", body, width, Role.BRAND)
    }
}
