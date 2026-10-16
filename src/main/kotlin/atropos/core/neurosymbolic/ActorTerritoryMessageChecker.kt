/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class ActorTerritoryMessageChecker {

    fun verifyNoSharedMemory(worktree: Path): Boolean {
        if (!Files.exists(worktree)) return true

        val ktFiles = Files.walk(worktree)
            .filter { Files.isRegularFile(it) && it.extension == "kt" }
            .toList()

        for (file in ktFiles) {
            val lines = Files.readAllLines(file)
            var insideCompanion = false

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) continue

                if (trimmed.contains("companion object")) {
                    insideCompanion = true
                }
                if (insideCompanion && trimmed.startsWith("}")) {
                    insideCompanion = false
                }

                // Disallow mutable companion properties or global vars
                if (insideCompanion && (trimmed.startsWith("var ") || trimmed.contains(" var "))) {
                    if (!trimmed.contains("ThreadLocal") && !trimmed.contains("Atomic")) {
                        return false
                    }
                }

                if (trimmed.startsWith("var ") && !trimmed.contains("private ")) {
                    // Top-level public mutable variable
                    return false
                }
            }
        }
        return true
    }
}
