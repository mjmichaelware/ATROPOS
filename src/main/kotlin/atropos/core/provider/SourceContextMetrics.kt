package atropos.core.provider

data class SourceContextMetrics(
    val sourceByteCount: Int,
    val packedByteCount: Int,
    val treeEditDistance: Int?,
) {
    val savingRatio: Double
        get() = if (sourceByteCount <= 0) 0.0 else
            ((sourceByteCount - packedByteCount).toDouble() / sourceByteCount.toDouble()).coerceIn(0.0, 1.0)

    val savingPercent: Double
        get() = savingRatio * 100.0
}
