/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.toolchain.LanguageToolchainDriver
import atropos.core.parser.TreeSitterGrammarBridge
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random

data class MutationReport(
    val totalMutants: Int,
    val killedMutants: Int,
    val killRate: Double,
    val passed: Boolean,
    val survivedMutants: List<String> = emptyList()
) {
    val killRatio: Double get() = killRate
}

class MutationTestingOracle(
    private val minKillRate: Double = 0.850,
    private val astBridge: TreeSitterGrammarBridge = TreeSitterGrammarBridge(),
    private val random: Random = Random()
) {
    fun evaluate(worktree: Path, driver: LanguageToolchainDriver): MutationReport {
        val sourceFiles = mutableListOf<String>()
        if (Files.exists(worktree)) {
            Files.walk(worktree).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { p ->
                        val name = p.fileName.toString()
                        (name.endsWith(".kt") || name.endsWith(".rs") || name.endsWith(".ts") ||
                            name.endsWith(".go") || name.endsWith(".py")) && !name.contains("Test")
                    }
                    .forEach { sourceFiles.add(worktree.relativize(it).toString()) }
            }
        }

        if (sourceFiles.isEmpty()) {
            return MutationReport(
                totalMutants = 0,
                killedMutants = 0,
                killRate = 1.0,
                passed = true
            )
        }

        return evaluateMutationScore(worktree, driver, sourceFiles)
    }

    fun evaluateMutationScore(
        worktreeRoot: Path,
        driver: LanguageToolchainDriver,
        sourceFiles: List<String>
    ): MutationReport {
        val allMutations = mutableListOf<String>()
        val originalSources = mutableMapOf<String, String>()

        for (filePath in sourceFiles) {
            val path = worktreeRoot.resolve(filePath)
            if (!Files.isRegularFile(path)) continue
            val source = Files.readString(path)
            originalSources[filePath] = source

            val mutations = generateMutations(source)
            allMutations.addAll(mutations.map { filePath + ":" + it })
        }

        val baselineTestResult = driver.test(worktreeRoot)
        if (baselineTestResult.exitCode != 0) {
            return MutationReport(
                totalMutants = allMutations.size,
                killedMutants = 0,
                killRate = 0.0,
                passed = false,
                survivedMutants = allMutations
            )
        }

        var killed = 0
        val survived = mutableListOf<String>()

        for (mutation in allMutations) {
            val parts = mutation.split(":", limit = 2)
            val filePath = parts[0]
            val mutatedCode = parts[1]
            val originalCode = originalSources[filePath] ?: continue

            try {
                Files.writeString(worktreeRoot.resolve(filePath), mutatedCode)
                val testResult = driver.test(worktreeRoot)
                Files.writeString(worktreeRoot.resolve(filePath), originalCode)

                if (testResult.exitCode != 0) {
                    killed++
                } else {
                    survived.add(mutation)
                }
            } catch (e: Exception) {
                try {
                    Files.writeString(worktreeRoot.resolve(filePath), originalCode)
                } catch (e2: Exception) {}
                survived.add(mutation)
            }
        }

        val total = allMutations.size
        val ratio = if (total > 0) killed.toDouble() / total else 1.0
        val passed = ratio >= minKillRate

        return MutationReport(
            totalMutants = total,
            killedMutants = killed,
            killRate = ratio,
            passed = passed,
            survivedMutants = survived
        )
    }

    private fun generateMutations(source: String): List<String> {
        val mutations = mutableListOf<String>()
        val lines = source.lineSequence().toList()

        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.contains(">") && !trimmed.contains(">=") && !trimmed.contains("->")) {
                mutations.add(line.replace(">", "<="))
            }
            if (trimmed.contains("<") && !trimmed.contains("<=") && !trimmed.contains("<-")) {
                mutations.add(line.replace("<", ">="))
            }
            if (trimmed.contains("&&")) {
                mutations.add(line.replace("&&", "||"))
            }
            if (trimmed.contains("||")) {
                mutations.add(line.replace("||", "&&"))
            }
            if (trimmed.contains("==")) {
                mutations.add(line.replace("==", "!="))
            }
            if (trimmed.contains("!=")) {
                mutations.add(line.replace("!=", "=="))
            }
            if (trimmed.matches(Regex(".*\\breturn\\s+(true|false|0|1|null)\\b.*"))) {
                mutations.add(line.replace("true", "false").replace("false", "true").replace("0", "1").replace("1", "0").replace("null", "0"))
            }
            if (trimmed.contains(".") && trimmed.contains("(") && !trimmed.trim().startsWith("//")) {
                mutations.add(line.replaceFirst(Regex("\\w+\\("), ""))
            }
        }

        return if (mutations.size > 10) mutations.shuffled(random).take(10) else mutations
    }
}