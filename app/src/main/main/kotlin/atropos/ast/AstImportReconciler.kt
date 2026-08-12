package atropos.ast

import atropos.core.parser.KotlinLexicalMasker
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal class AstImportReconciler(private val repoRoot: Path) {
    fun reconcileImports(path: String, symbols: List<AstSymbol>): AstImportReconciliationResult {
        val target = resolveScopedPath(path)
        val fileSymbols = symbols.filter { it.file.normalize() == target }
        require(fileSymbols.isNotEmpty()) { "unknown Kotlin source: $path" }
        val fileSymbol = fileSymbols.first { it.kind == AstSymbolKind.FILE }
        val importableSymbols = symbols.filter { it.kind != AstSymbolKind.FILE }
        val symbolIndex = importableSymbols.groupBy { it.qualifiedName }
        val localPackageRoots = symbols
            .mapNotNull { it.packageName.substringBefore('.').takeIf(String::isNotBlank) }
            .toSet()
        val imports = fileSymbol.imports
        val resolutions = imports.distinct().sorted().map { rawImport ->
            val importPath = normalizeImportPath(rawImport)
            when {
                isExternalImport(importPath, localPackageRoots) -> AstImportResolution(
                    importPath = rawImport,
                    status = AstImportStatus.EXTERNAL,
                    matches = emptyList(),
                    expectedPathSuffixes = emptyList()
                )
                importPath.endsWith(".*") -> {
                    val packagePrefix = importPath.removeSuffix(".*") + "."
                    val matches = importableSymbols
                        .filter { it.qualifiedName.startsWith(packagePrefix) }
                        .sortedBy { it.qualifiedName }
                    AstImportResolution(
                        importPath = rawImport,
                        status = AstImportStatus.WILDCARD,
                        matches = matches.map { it.qualifiedName },
                        expectedPathSuffixes = matches.map { it.expectedPathSuffix }.distinct().sorted()
                    )
                }
                symbolIndex.containsKey(importPath) -> {
                    val matches = symbolIndex.getValue(importPath)
                    AstImportResolution(
                        importPath = rawImport,
                        status = if (matches.size == 1) AstImportStatus.LOCAL_EXACT else AstImportStatus.AMBIGUOUS,
                        matches = matches.map { it.qualifiedName }.sorted(),
                        expectedPathSuffixes = matches.map { it.expectedPathSuffix }.distinct().sorted()
                    )
                }
                else -> {
                    AstImportResolution(
                        importPath = rawImport,
                        status = AstImportStatus.UNRESOLVED,
                        matches = emptyList(),
                        expectedPathSuffixes = emptyList()
                    )
                }
            }
        }
        val violations = deterministicImportViolations(
            imports = imports,
            resolutions = resolutions,
            source = runCatching { Files.readString(target, StandardCharsets.UTF_8) }.getOrElse { "" }
        )
        return AstImportReconciliationResult(
            file = target,
            packageName = fileSymbol.packageName,
            packagePathInvariantHolds = fileSymbol.packagePathInvariantHolds,
            resolutions = resolutions,
            violations = violations
        )
    }

    private fun deterministicImportViolations(
        imports: List<String>,
        resolutions: List<AstImportResolution>,
        source: String
    ): List<AstImportViolation> {
        val violations = mutableListOf<AstImportViolation>()
        val aliases = imports.mapNotNull { rawImport ->
            rawImport.substringAfter(" as ", "").trim().takeIf { it.isNotBlank() }
                ?.let { alias -> alias to rawImport }
        }
        aliases.groupBy { it.first }.filterValues { it.size > 1 }.toSortedMap().forEach { (alias, entries) ->
            violations += AstImportViolation(
                rule = "duplicate_alias",
                imports = entries.map { it.second }.sorted(),
                evidence = "alias '$alias' is assigned to multiple imports",
                remediation = "use one alias or distinct aliases"
            )
        }

        imports.filter { normalizeImportPath(it).endsWith(".*") }.distinct().sorted().forEach { wildcard ->
            violations += AstImportViolation(
                rule = "wildcard_import",
                imports = listOf(wildcard),
                evidence = "wildcard import does not identify a deterministic symbol",
                remediation = "replace wildcard import with an exact import"
            )
        }

        val visibleNames = imports
            .filterNot { normalizeImportPath(it).endsWith(".*") }
            .groupBy { visibleImportName(it) }
            .filterValues { entries -> entries.map(::normalizeImportPath).distinct().size > 1 }
        visibleNames.toSortedMap().forEach { (name, entries) ->
            violations += AstImportViolation(
                rule = "simple_name_ambiguity",
                imports = entries.sorted(),
                evidence = "simple name '$name' resolves to multiple import paths",
                remediation = "use an alias or remove the competing import"
            )
        }

        resolutions.filter { it.status == AstImportStatus.AMBIGUOUS }.forEach { resolution ->
            violations += AstImportViolation(
                rule = "ambiguous_import",
                imports = listOf(resolution.importPath),
                evidence = "local import resolves to multiple symbols: ${resolution.matches.joinToString(",")}",
                remediation = "remove the competing symbol or import an unambiguous path"
            )
        }
        resolutions.filter { it.status == AstImportStatus.UNRESOLVED }.forEach { resolution ->
            violations += AstImportViolation(
                rule = "unresolved_import",
                imports = listOf(resolution.importPath),
                evidence = "local import has no symbol-graph match",
                remediation = "add the local declaration or correct the import path"
            )
        }

        val codeWithoutImportLines = KotlinLexicalMasker.maskNonCode(source)
            .lineSequence()
            .filterNot { it.trimStart().startsWith("import ") }
            .joinToString("\n")
        resolutions.filter { it.status == AstImportStatus.LOCAL_EXACT || it.status == AstImportStatus.EXTERNAL }
            .forEach { resolution ->
                val visibleName = visibleImportName(resolution.importPath)
                if (!Regex("\\b${Regex.escape(visibleName)}\\b").containsMatchIn(codeWithoutImportLines)) {
                    violations += AstImportViolation(
                        rule = "unused_import",
                        imports = listOf(resolution.importPath),
                        evidence = "imported name '$visibleName' is not referenced outside its import declaration",
                        remediation = "remove the unused import"
                    )
                }
            }

        return violations.distinctBy { it.rule to it.imports }
    }

    private fun resolveScopedPath(path: String): Path {
        val root = repoRoot.toAbsolutePath().normalize()
        val target = root.resolve(path).normalize()
        require(target.startsWith(root)) {
            "AST query path escapes repository root"
        }
        var current: Path? = target
        while (current != null && current.startsWith(root)) {
            require(!Files.isSymbolicLink(current)) {
                "AST query path contains a symbolic link"
            }
            current = current.parent
        }
        return target
    }

    private fun isExternalImport(importPath: String, localPackageRoots: Set<String>): Boolean =
        importPath.startsWith("java.") ||
            importPath.startsWith("javax.") ||
            importPath.startsWith("kotlin.") ||
            importPath.startsWith("android.") ||
            importPath.startsWith("androidx.") ||
            importPath.substringBefore('.') !in localPackageRoots

    private fun normalizeImportPath(importPath: String): String =
        importPath.substringBefore(" as ").trim()

    private fun visibleImportName(importPath: String): String =
        importPath.substringAfter(" as ", "").trim().ifBlank {
            normalizeImportPath(importPath).substringAfterLast('.')
        }
}
