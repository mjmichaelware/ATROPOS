package atropos.core.supremacy

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater

data class PatchComplexityMetric(
    val patchIndex: Int,
    val rawBytes: Int,
    val compressedBytes: Int,
    val structuralLinesAdded: Int,
    val structuralLinesRemoved: Int,
    val descriptionComplexityScore: Double
)

class MdlPatchSelector {
    fun calculateDeflatedLength(data: String): Int {
        val input = data.toByteArray(StandardCharsets.UTF_8)
        if (input.isEmpty()) return 0
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(input)
        deflater.finish()
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(512)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            baos.write(buffer, 0, count)
        }
        deflater.end()
        return baos.size()
    }

    fun scoreCandidate(index: Int, patchDiff: String): PatchComplexityMetric {
        val compressed = calculateDeflatedLength(patchDiff)
        var added = 0
        var removed = 0
        for (line in patchDiff.lineSequence()) {
            if (line.startsWith("+") && !line.startsWith("+++")) added++
            if (line.startsWith("-") && !line.startsWith("---")) removed++
        }
        val score = compressed.toDouble() + (added * 2.0) - (removed * 0.5)
        return PatchComplexityMetric(
            patchIndex = index,
            rawBytes = patchDiff.length,
            compressedBytes = compressed,
            structuralLinesAdded = added,
            structuralLinesRemoved = removed,
            descriptionComplexityScore = maxOf(0.0, score)
        )
    }

    fun selectMinimalPatch(candidates: List<String>): Int {
        require(candidates.isNotEmpty()) { "Cannot select from empty candidate patches" }
        if (candidates.size == 1) return 0
        val metrics = candidates.mapIndexed { idx, diff -> scoreCandidate(idx, diff) }
        return metrics.minByOrNull { it.descriptionComplexityScore }!!.patchIndex
    }
}
