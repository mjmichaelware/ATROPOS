package atropos.cli.ui

import atropos.core.assets.LocalAssetGenerator

class StatusAssetsRenderer(
    private val generator: LocalAssetGenerator = LocalAssetGenerator()
) {
    fun render(): String {
        val status = generator.status()
        return buildString {
            appendLine("assets:")
            appendLine("  local text: ${if (status.localTextReady) "ready" else "unavailable"}")
            appendLine("  local ansi: ${if (status.localAnsiReady) "ready" else "unavailable"}")
            appendLine("  local svg: ${if (status.localSvgReady) "ready" else "unavailable"}")
            appendLine("  artifacts: ${status.totalArtifacts}")
            appendLine("  root: ${status.root.path}")
            appendLine("  huggingface: ${if (status.huggingFaceConfigured) "configured optional" else "optional/off"}")
            appendLine("  fal: ${if (status.falConfigured) "configured optional" else "optional/off"}")
            appendLine("  replicate: ${if (status.replicateConfigured) "configured optional" else "optional/off"}")
            appendLine("  paid vision: locked until explicit /paid unlock")
            appendLine("  policy: terminal UI never requires image generation")
        }
    }
}
