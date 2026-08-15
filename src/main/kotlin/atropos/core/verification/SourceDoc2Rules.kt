/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object Rule127Snapshot {
    fun formatSnapshot(rawBytes: ByteArray): Map<Int, String> {
        val ansiStripped = String(rawBytes).replace(Regex("\\u001B\\[[;\\d]*m"), "")
        return mapOf(
            40 to limitColumns(ansiStripped, 40),
            80 to limitColumns(ansiStripped, 80),
            120 to limitColumns(ansiStripped, 120)
        )
    }

    private fun limitColumns(text: String, width: Int): String {
        return text.lines().joinToString("\n") { line ->
            if (line.length > width) line.substring(0, width) else line
        }
    }
}

object Rule129Compile {
    fun compileAndPromote(
        sourceFile: File,
        targetFile: File,
        compileFn: (File) -> Boolean,
        gateFn: () -> Boolean
    ): Boolean {
        val tempDir = Files.createTempDirectory("atropos-compile-temp-")
        val tempFile = tempDir.resolve(sourceFile.name).toFile()
        sourceFile.copyTo(tempFile, overwrite = true)

        val compileOk = compileFn(tempFile)
        if (!compileOk) return false

        val gatesOk = gateFn()
        if (!gatesOk) return false

        // Move into place only after every gate passes
        Files.createDirectories(targetFile.parentFile.toPath())
        Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return true
    }
}

object Rule137Success {
    fun verifySuccess(
        kotlincExit: Int,
        smokeExit: Int,
        grepTruth: Boolean,
        gitDiffCheckExit: Int,
        jarSwapSuccess: Boolean
    ): Boolean {
        return kotlincExit == 0 &&
               smokeExit == 0 &&
               grepTruth &&
               gitDiffCheckExit == 0 &&
               jarSwapSuccess
    }
}

object Rule142Export {
    fun generateExportCommand(changedFiles: List<String>): String {
        return "cat ${changedFiles.joinToString(" ")} > /sdcard/Download/atropos-context-export.txt"
    }

    fun triggerMediaScan(): String {
        return "termux-media-scan /sdcard/Download/atropos-context-export.txt"
    }
}
