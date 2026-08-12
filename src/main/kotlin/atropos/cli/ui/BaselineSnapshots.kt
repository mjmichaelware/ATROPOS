/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Names and resolves the tracked terminal parity baselines.
 *
 * The files are text captures, not fabricated screenshots. A missing file is
 * reported as absent so callers cannot turn an unavailable baseline into a
 * passing visual claim.
 */
class BaselineSnapshots(
    private val root: Path = Paths.get("docs", "ui-parity", "baseline")
) {
    data class Snapshot(val width: Int, val surface: String, val path: Path, val available: Boolean)

    fun widths(): List<Int> = REQUIRED_WIDTHS

    fun snapshot(surface: String, width: Int): Snapshot {
        require(width in REQUIRED_WIDTHS) { "unsupported baseline width: $width" }
        val safeSurface = surface.trim().lowercase().ifBlank { "landing" }
        val path = root.resolve("${safeSurface}-${width}col.txt").normalize()
        require(path.parent == root.normalize()) { "baseline surface escapes baseline root" }
        return Snapshot(width, safeSurface, path, Files.isRegularFile(path))
    }

    fun all(surface: String): List<Snapshot> = widths().map { snapshot(surface, it) }

    companion object {
        val REQUIRED_WIDTHS: List<Int> = listOf(40, 80, 120, 160)
    }
}
