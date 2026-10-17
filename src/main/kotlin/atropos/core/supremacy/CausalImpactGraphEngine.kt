package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path

data class SymbolRef(val qualifiedName: String, val filePath: String)

data class CausalImpactReport(
    val directlyTouchedFiles: Set<String>,
    val directDependentSymbols: Set<SymbolRef>,
    val transitiveDependentSymbols: Set<SymbolRef>,
    val affectedFiles: Set<String>,
    val riskScore: Double
)

class CausalImpactGraphEngine {
    private val importPattern = Regex("""import\s+([a-zA-Z0-9_.]+)""")
    private val packagePattern = Regex("""package\s+([a-zA-Z0-9_.]+)""")
    private val classPattern = Regex("""(?:class|interface|object)\s+([a-zA-Z0-9_]+)""")

    fun computeTransitiveClosure(repoRoot: Path, modifiedFiles: List<String>): CausalImpactReport {
        val allKotlinFiles = Files.walk(repoRoot)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .toList()

        val symbolToFile = mutableMapOf<String, String>()
        val fileToSymbols = mutableMapOf<String, MutableSet<String>>()
        val fileDependencies = mutableMapOf<String, MutableSet<String>>()

        for (file in allKotlinFiles) {
            val relPath = repoRoot.relativize(file).toString().replace('\\', '/')
            val lines = Files.readAllLines(file)
            var currentPackage = ""
            val imported = mutableSetOf<String>()

            for (line in lines) {
                packagePattern.find(line)?.let { currentPackage = it.groupValues[1] }
                importPattern.find(line)?.let { imported.add(it.groupValues[1]) }
                classPattern.find(line)?.let {
                    val className = it.groupValues[1]
                    val fqName = if (currentPackage.isNotBlank()) "$currentPackage.$className" else className
                    symbolToFile[fqName] = relPath
                    fileToSymbols.computeIfAbsent(relPath) { mutableSetOf() }.add(fqName)
                }
            }

            for (imp in imported) {
                symbolToFile[imp]?.let { targetFile ->
                    if (targetFile != relPath) {
                        fileDependencies.computeIfAbsent(targetFile) { mutableSetOf() }.add(relPath)
                    }
                }
            }
        }

        val directDependents = mutableSetOf<SymbolRef>()
        val transitiveDependents = mutableSetOf<SymbolRef>()
        val affectedFiles = modifiedFiles.toMutableSet()
        val queue = ArrayDeque<String>(modifiedFiles)

        while (queue.isNotEmpty()) {
            val currentFile = queue.removeFirst()
            val dependents = fileDependencies[currentFile] ?: emptySet()
            for (depFile in dependents) {
                if (affectedFiles.add(depFile)) {
                    queue.add(depFile)
                    val symbols = fileToSymbols[depFile] ?: emptySet()
                    for (sym in symbols) {
                        transitiveDependents.add(SymbolRef(sym, depFile))
                        if (currentFile in modifiedFiles) {
                            directDependents.add(SymbolRef(sym, depFile))
                        }
                    }
                }
            }
        }

        val risk = minOf(1.0, (transitiveDependents.size.toDouble() / 50.0) + (0.1 * affectedFiles.size))
        return CausalImpactReport(
            directlyTouchedFiles = modifiedFiles.toSet(),
            directDependentSymbols = directDependents,
            transitiveDependentSymbols = transitiveDependents,
            affectedFiles = affectedFiles,
            riskScore = risk
        )
    }
}
