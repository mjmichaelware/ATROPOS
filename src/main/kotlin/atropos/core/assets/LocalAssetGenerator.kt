package atropos.core.assets

import java.io.File
import java.security.MessageDigest
import java.util.Locale

enum class AssetKind {
    TEXT,
    ANSI,
    SVG
}

data class AssetRequest(
    val kind: AssetKind,
    val name: String,
    val prompt: String,
    val tags: List<String> = emptyList()
)

data class AssetArtifact(
    val id: String,
    val kind: AssetKind,
    val file: File,
    val bytes: Long,
    val summary: String
)

data class AssetStatus(
    val root: File,
    val totalArtifacts: Int,
    val localTextReady: Boolean,
    val localAnsiReady: Boolean,
    val localSvgReady: Boolean,
    val huggingFaceConfigured: Boolean,
    val falConfigured: Boolean,
    val replicateConfigured: Boolean,
    val paidVisionLocked: Boolean
)

class LocalAssetGenerator(
    private val root: File = File(".atropos/assets"),
    private val env: Map<String, String> = System.getenv()
) {
    fun generate(request: AssetRequest): AssetArtifact {
        root.mkdirs()
        val safeName = safeName(request.name.ifBlank { request.kind.name.lowercase(Locale.US) })
        val id = stableId("${request.kind.name}|$safeName|${request.prompt}|${request.tags.joinToString(",")}")
        val file = when (request.kind) {
            AssetKind.TEXT -> File(root, "$safeName-$id.txt")
            AssetKind.ANSI -> File(root, "$safeName-$id.ansi.txt")
            AssetKind.SVG -> File(root, "$safeName-$id.svg")
        }
        val content = when (request.kind) {
            AssetKind.TEXT -> renderText(request, id)
            AssetKind.ANSI -> renderAnsi(request, id)
            AssetKind.SVG -> renderSvg(request, id)
        }
        file.writeText(content)
        return AssetArtifact(
            id = id,
            kind = request.kind,
            file = file,
            bytes = file.length(),
            summary = "local ${request.kind.name.lowercase(Locale.US)} asset"
        )
    }

    fun status(): AssetStatus {
        root.mkdirs()
        val files = root.listFiles()?.filter { it.isFile } ?: emptyList()
        return AssetStatus(
            root = root,
            totalArtifacts = files.size,
            localTextReady = true,
            localAnsiReady = true,
            localSvgReady = true,
            huggingFaceConfigured = !env["HUGGINGFACE_API_KEY"].isNullOrBlank(),
            falConfigured = !env["FAL_AI_API_KEY"].isNullOrBlank(),
            replicateConfigured = !env["REPLICATE_API_TOKEN"].isNullOrBlank(),
            paidVisionLocked = true
        )
    }

    private fun renderText(request: AssetRequest, id: String): String {
        return buildString {
            appendLine("ATROPOS LOCAL TEXT ASSET")
            appendLine("id: $id")
            appendLine("name: ${request.name}")
            appendLine("tags: ${request.tags.joinToString(",").ifBlank { "none" }}")
            appendLine()
            appendLine(request.prompt.trim().ifBlank { "empty prompt" })
        }
    }

    private fun renderAnsi(request: AssetRequest, id: String): String {
        val title = request.name.trim().ifBlank { "ATROPOS" }.uppercase(Locale.US).take(48)
        val body = request.prompt.trim().ifBlank { "local terminal asset" }
        val width = 64
        val line = "=".repeat(width)
        return buildString {
            appendLine(line)
            appendLine(center(title, width))
            appendLine(center("asset $id", width))
            appendLine(line)
            body.lines().take(12).forEach { appendLine("  " + it.take(width - 4)) }
            appendLine(line)
        }
    }

    private fun renderSvg(request: AssetRequest, id: String): String {
        val title = escapeXml(request.name.trim().ifBlank { "ATROPOS" }.take(42))
        val body = escapeXml(request.prompt.trim().ifBlank { "local svg asset" }.take(90))
        val hue = (id.take(6).toIntOrNull(16) ?: 210) % 360
        return """
<svg xmlns="http://www.w3.org/2000/svg" width="960" height="540" viewBox="0 0 960 540" role="img" aria-label="$title">
  <rect width="960" height="540" fill="hsl($hue 42% 12%)"/>
  <rect x="48" y="48" width="864" height="444" rx="24" fill="hsl($hue 34% 18%)" stroke="hsl($hue 62% 58%)" stroke-width="3"/>
  <text x="84" y="138" fill="white" font-family="monospace" font-size="46" font-weight="700">$title</text>
  <text x="84" y="210" fill="hsl($hue 80% 82%)" font-family="monospace" font-size="24">$body</text>
  <text x="84" y="438" fill="hsl($hue 45% 72%)" font-family="monospace" font-size="18">ATROPOS local asset · $id</text>
</svg>
""".trim() + "\n"
    }

    private fun center(value: String, width: Int): String {
        if (value.length >= width) return value.take(width)
        val left = (width - value.length) / 2
        return " ".repeat(left) + value
    }

    private fun safeName(value: String): String {
        return value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifBlank { "asset" }
    }

    private fun stableId(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
