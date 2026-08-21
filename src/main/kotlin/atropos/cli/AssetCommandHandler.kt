/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.StatusAssetsRenderer
import atropos.core.assets.AssetKind
import atropos.core.assets.AssetRequest
import atropos.core.assets.LocalAssetGenerator

class AssetCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val generator: LocalAssetGenerator = LocalAssetGenerator()
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            null, "status" -> uiEngine.renderBlock(StatusAssetsRenderer(generator).render(uiEngine.viewportWidth))
            "text", "ansi", "svg" -> writeAsset(tokens)
            else -> uiEngine.renderError("usage: /assets [status|text|ansi|svg] <name> <prompt>")
        }
        return RouterOutcome.CONTINUE
    }

    private fun writeAsset(tokens: List<String>) {
        val kind = when (tokens[1].lowercase()) {
            "ansi" -> AssetKind.ANSI
            "svg" -> AssetKind.SVG
            else -> AssetKind.TEXT
        }
        val name = tokens.getOrNull(2) ?: kind.name.lowercase()
        val prompt = tokens.drop(3).joinToString(" ").ifBlank { name }
        val artifact = generator.generate(AssetRequest(kind, name, prompt, listOf("cli")))
        uiEngine.renderNotice("asset written: ${artifact.file.path} bytes=${artifact.bytes}")
    }
}
