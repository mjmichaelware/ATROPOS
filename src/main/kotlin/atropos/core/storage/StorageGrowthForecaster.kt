/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class StorageGrowthForecast(val currentBytes: Long, val averageBytesPerSample: Long, val samplesUntilCeiling: Long?)

class StorageGrowthForecaster {
    fun forecast(samples: List<Long>, ceilingBytes: Long): StorageGrowthForecast {
        require(samples.all { it >= 0 } && ceilingBytes >= 0)
        val current = samples.lastOrNull() ?: 0L
        val average = if (samples.size < 2) 0L else samples.zipWithNext().map { (a, b) -> (b - a).coerceAtLeast(0) }.average().toLong()
        val remaining = (ceilingBytes - current).coerceAtLeast(0)
        val until = if (average == 0L) null else remaining / average
        return StorageGrowthForecast(current, average, until)
    }
}
