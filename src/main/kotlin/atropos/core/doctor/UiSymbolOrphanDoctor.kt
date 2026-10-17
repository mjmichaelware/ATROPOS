package atropos.core.doctor

import java.nio.file.Files
import java.nio.file.Path

data class OrphanCheckReport(val totalSymbolsScanned: Int, val orphanSymbols: List<String>, val passed: Boolean)

class UiSymbolOrphanDoctor(private val repoRoot: Path) {
    private val declRegex = Regex("""(?:class|object|interface|fun)\s+([a-zA-Z0-9_]+)""")

    fun auditUiCallers(): OrphanCheckReport {
        val srcDir = repoRoot.resolve("src/main/kotlin").normalize()
        if (!Files.exists(srcDir)) return OrphanCheckReport(0, emptyList(), true)

        val allSources = Files.walk(srcDir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .toList()

        val declaredSymbols = mutableListOf<Pair<String, Path>>()
        for (file in allSources) {
            val lines = Files.readAllLines(file)
            for (line in lines) {
                declRegex.find(line)?.let {
                    declaredSymbols.add(it.groupValues[1] to file)
                }
            }
        }

        val allCodeCombined = allSources.joinToString("\n") { Files.readString(it) }
        val orphans = mutableListOf<String>()

        for ((symbol, originFile) in declaredSymbols) {
            if (symbol in setOf("main", "Companion", "equals", "hashCode", "toString")) continue
            val count = Regex("""\b$symbol\b""").findAll(allCodeCombined).count()
            if (count <= 1) {
                orphans.add("$symbol (${repoRoot.relativize(originFile)})")
            }
        }

        return OrphanCheckReport(
            totalSymbolsScanned = declaredSymbols.size,
            orphanSymbols = orphans,
            passed = orphans.isEmpty()
        )
    }
}
