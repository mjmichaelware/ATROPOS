/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.util.CodeBlockExtractor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Normalizes code serialization before writing generated source files to disk in staging environments.
 * Enforces string normalization by replacing literal \" with " and \' with '.
 */
object StagingFileWriter {
    fun normalize(code: String): String =
        CodeBlockExtractor.normalize(code)

    fun writeSource(path: Path, content: String): Path {
        path.parent?.let { Files.createDirectories(it) }
        val normalized = normalize(content)
        return Files.writeString(
            path,
            normalized,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }
}
