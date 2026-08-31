/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path

/**
 * Projects visual comparison results onto the wire.
 *
 * `ADD-W-019` requires visual compare + a11y bind: screenshots/visual compare
 * bound to requirement evidence. Attach compare results as EvidenceRef.
 *
 * This projection compares two screenshots and produces a visual diff result
 * that can be attached as evidence.
 */
class VisualComparisonProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    /**
     * Renders the visual comparison between two screenshots.
     */
    fun compare(baselinePath: String, currentPath: String): String {
        val baseline = Path.of(baselinePath).normalize()
        val current = Path.of(currentPath).normalize()

        if (!Files.isRegularFile(baseline)) {
            return JsonWriter.obj(
                "ok" to JsonWriter.bool(false),
                "error" to JsonWriter.str("Baseline image not found: $baselinePath")
            )
        }

        if (!Files.isRegularFile(current)) {
            return JsonWriter.obj(
                "ok" to JsonWriter.bool(false),
                "error" to JsonWriter.str("Current image not found: $currentPath")
            )
        }

        // Read both images as bytes
        val baselineBytes = Files.readAllBytes(baseline)
        val currentBytes = Files.readAllBytes(current)

        // Simple pixel-by-pixel comparison (placeholder for real diff)
        val baselineHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(baselineBytes)
            .joinToString("") { "%02x".format(it) }
        val currentHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(currentBytes)
            .joinToString("") { "%02x".format(it) }

        val identical = baselineHash == currentHash
        val diffPercentage = if (identical) 0.0 else calculateDiffPercentage(baselineBytes, currentBytes)

        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "identical" to JsonWriter.bool(identical),
            "diffPercentage" to JsonWriter.num(diffPercentage),
            "baselineHash" to JsonWriter.str(baselineHash),
            "currentHash" to JsonWriter.str(currentHash),
            "evidenceRef" to JsonWriter.obj(
                "casHash" to JsonWriter.str(currentHash),
                "claimId" to JsonWriter.str("visual-compare-${System.currentTimeMillis()}"),
                "gateIds" to JsonWriter.arr("visual-compare")
            )
        )
    }

    private fun calculateDiffPercentage(baseline: ByteArray, current: ByteArray): Double {
        val minLen = minOf(baseline.size, current.size)
        if (minLen == 0) return 100.0
        var diff = 0
        for (i in 0 until minLen) {
            if (baseline[i] != current[i]) diff++
        }
        return (diff.toDouble() / minLen) * 100.0
    }
}