package atropos.ast

import atropos.core.parser.KotlinDeclarationKind
import atropos.core.parser.KotlinLexicalMasker
import atropos.core.parser.TreeSitterGrammarBridge
import atropos.core.AtroposRepoRootLocator
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

class AstSymbolGraph(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val parser: TreeSitterGrammarBridge = TreeSitterGrammarBridge()
) {
    private val importReconciler = AstImportReconciler(repoRoot)
    val index = AstSymbolIndex()
    private val nodes = mutableMapOf<String, AstSymbolNode>()
    private val childrenMap = mutableMapOf<String, MutableList<AstSymbolNode>>()

    fun addNode(node: AstSymbolNode) {
        nodes[node.nodeId] = node
        index.add(node)
        if (node.parentId != null) {
            childrenMap.getOrPut(node.parentId) { mutableListOf() }.add(node)
        }
    }

    fun getNode(nodeId: String): AstSymbolNode? = nodes[nodeId]
    fun getByAddress(address: String): AstSymbolNode? = index.lookup(address).firstOrNull { it.address == address }
    fun getByFile(filePath: String): List<AstSymbolNode> = nodes.values.filter { it.filePath == filePath }
    fun getChildren(nodeId: String): List<AstSymbolNode> = childrenMap[nodeId] ?: emptyList()


    fun build(): List<AstSymbol> {
        val sourceRoots = listOf(
            repoRoot.resolve("src/main/kotlin"),
            repoRoot.resolve("src/test/kotlin")
        ).filter { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(it) }
        if (sourceRoots.isEmpty()) return emptyList()
        return sourceRoots.flatMap { sourceRoot ->
            Files.walk(sourceRoot).use { stream ->
                stream.filter {
                    Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) &&
                        it.fileName.toString().substringAfterLast('.', "") == "kt"
                }
                    .sorted()
                    .flatMap { parseFile(it).stream() }
                    .toList()
            }
        }
    }

    fun lookup(query: String): AstLookupResult {
        val normalized = query.trim()
        val symbols = build().filter {
            it.qualifiedName == normalized ||
                it.name == normalized ||
                it.qualifiedName.contains(normalized, ignoreCase = true)
        }
        return AstLookupResult(normalized, symbols)
    }

    fun impactedByPaths(paths: List<String>): List<AstSymbol> {
        val normalized = paths.map(::resolveScopedPath).toSet()
        return build().filter { it.file.normalize() in normalized }
    }

    fun impactOfPaths(paths: List<String>): List<AstSymbol> {
        val symbols = build()
        val changedFiles = paths.map(::resolveScopedPath).toSet()
        val changedNames = symbols
            .filter { it.file.normalize() in changedFiles && it.kind != AstSymbolKind.FILE }
            .map { it.qualifiedName }
            .toSet()
        if (changedNames.isEmpty()) return symbols.filter { it.file.normalize() in changedFiles }
        val changedPackageNames = symbols
            .filter { it.file.normalize() in changedFiles && it.kind == AstSymbolKind.FILE }
            .map { it.packageName }
            .filter(String::isNotBlank)
            .toSet()
        return symbols.filter { symbol ->
            symbol.file.normalize() in changedFiles ||
                symbol.kind == AstSymbolKind.FILE && (
                    symbol.packageName in changedPackageNames ||
                        symbol.imports.any { imported ->
                            val importPath = normalizeImportPath(imported)
                            importPath in changedNames ||
                                importPath.endsWith(".*") && changedPackageNames.any { packageName ->
                                    importPath.removeSuffix(".*") == packageName
                                }
                        }
                    )
        }
    }

    fun findCallers(symbolName: String): List<AstSymbol> {
        val normalized = symbolName.trim()
        if (normalized.isBlank()) return emptyList()
        val allSymbols = build()
        val simpleName = normalized.substringAfterLast('.')
        val candidates = allSymbols.filter {
            it.kind != AstSymbolKind.FILE &&
                (it.qualifiedName == normalized || it.name == normalized || it.name == simpleName)
        }
        val exactCandidates = candidates.filter { it.qualifiedName == normalized }
        val knownSymbol = when {
            exactCandidates.size == 1 -> exactCandidates.single()
            exactCandidates.size > 1 -> null
            candidates.size == 1 -> candidates.single()
            else -> null
        }
        if (knownSymbol == null) return emptyList()
        val searchedName = knownSymbol.name
        val fileSymbols = allSymbols.filter { it.kind == AstSymbolKind.FILE }
        val matches = mutableListOf<AstSymbol>()
        val qualifiedPattern = Regex(Regex.escape(knownSymbol.qualifiedName))

        fileSymbols.forEach { fileSymbol ->
            val content = runCatching { Files.readString(fileSymbol.file) }.getOrNull() ?: ""
            val executableCode = maskReferenceSource(content)
            val referenceNames = referenceNamesInScope(fileSymbol, knownSymbol)
            val visibleReference = referenceNames.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(executableCode) }
            if (visibleReference || qualifiedPattern.containsMatchIn(executableCode)) {
                val definesSymbol = allSymbols.any {
                    it.file == fileSymbol.file &&
                        it.kind != AstSymbolKind.FILE &&
                        (if (normalized.contains('.')) it.qualifiedName == normalized else it.name == searchedName)
                }
                if (!definesSymbol) {
                    matches.add(fileSymbol)
                }
            }
        }
        return matches
    }

    fun reconcileImports(path: String): AstImportReconciliationResult =
        importReconciler.reconcileImports(path, build())

    private fun referenceNamesInScope(file: AstSymbol, target: AstSymbol): Set<String> {
        val names = linkedSetOf<String>()
        if (file.packageName == target.packageName) names += target.name
        file.imports.forEach { rawImport ->
            val imported = rawImport.substringBefore(" as ").trim()
            val alias = rawImport.substringAfter(" as ", "").trim()
            val visibleName = alias.ifBlank { target.name }
            if (imported == target.qualifiedName ||
                imported.endsWith(".*") && target.qualifiedName.startsWith(imported.removeSuffix(".*") + ".")
            ) {
                names += visibleName
            }
        }
        return names
    }

    private fun maskReferenceSource(content: String): String =
        KotlinLexicalMasker.maskNonCode(content).replace(PACKAGE_OR_IMPORT_LINE) { match ->
            match.value.map { character ->
                if (character == '\n' || character == '\r') character else ' '
            }.joinToString("")
        }

    private fun parseFile(path: Path): List<AstSymbol> {
        val source = Files.readString(path, StandardCharsets.UTF_8)
        val tree = parser.parseTree(source)
        val relative = portablePath(repoRoot.relativize(path))
        val expectedPathSuffix = expectedPathSuffix(tree.packageName, path)
        val packagePathInvariantHolds = relative.endsWith(expectedPathSuffix)
        val symbols = mutableListOf<AstSymbol>()

        symbols += AstSymbol(
            kind = AstSymbolKind.FILE,
            name = path.fileName.toString(),
            qualifiedName = "${tree.packageName}.${path.fileName}",
            file = path,
            packageName = tree.packageName,
            imports = tree.imports,
            dependencyRefs = tree.imports,
            expectedPathSuffix = expectedPathSuffix,
            packagePathInvariantHolds = packagePathInvariantHolds,
            line = 1,
            column = 1,
            offset = 0
        )

        tree.declarations.forEach { declaration ->
            val kind = when (declaration.kind) {
                KotlinDeclarationKind.CLASS -> AstSymbolKind.CLASS
                KotlinDeclarationKind.ENUM -> AstSymbolKind.ENUM
                KotlinDeclarationKind.ANNOTATION -> AstSymbolKind.ANNOTATION
                KotlinDeclarationKind.OBJECT -> AstSymbolKind.OBJECT
                KotlinDeclarationKind.INTERFACE -> AstSymbolKind.INTERFACE
                KotlinDeclarationKind.FUNCTION -> AstSymbolKind.FUNCTION
                KotlinDeclarationKind.PROPERTY -> AstSymbolKind.PROPERTY
                KotlinDeclarationKind.TYPEALIAS -> AstSymbolKind.TYPEALIAS
            }
            val qualified = if (tree.packageName.isBlank()) {
                (declaration.scope + declaration.name).joinToString(".")
            } else {
                "${tree.packageName}.${(declaration.scope + declaration.name).joinToString(".")}"
            }
            symbols += AstSymbol(
                kind = kind,
                name = declaration.name,
                qualifiedName = qualified,
                file = path,
                packageName = tree.packageName,
                imports = tree.imports,
                dependencyRefs = tree.imports,
                expectedPathSuffix = expectedPathSuffix,
                packagePathInvariantHolds = packagePathInvariantHolds,
                line = declaration.line,
                column = declaration.column,
                offset = declaration.offset
            )
        }
        return symbols
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

    private fun expectedPathSuffix(packageName: String, path: Path): String =
        if (packageName.isBlank()) {
            path.fileName.toString()
        } else {
            packageName.replace('.', '/') + "/" + path.fileName
        }

    private fun normalizeImportPath(importPath: String): String =
        importPath.substringBefore(" as ").trim()

    private fun portablePath(path: Path): String = path.toString().replace('\\', '/')

    private companion object {
        val PACKAGE_OR_IMPORT_LINE = Regex("(?m)^[ \\t]*(?:package|import)\\b[^\\r\\n]*")
    }
}

data class AstSymbolNode(
    val nodeId: String,
    val address: String, // document#section@Lstart-end
    val symbolType: String,
    val filePath: String,
    val byteOffsetStart: Int,
    val byteOffsetEnd: Int,
    val parentId: String?
)

class AstSymbolIndex {
    private val index = mutableMapOf<String, AstSymbolNode>()
    fun add(node: AstSymbolNode) { index[node.address] = node }
    fun lookup(prefix: String): List<AstSymbolNode> {
        return index.values.filter { it.address.startsWith(prefix) }
    }
}

object AstNamespaceReconciler {
    fun reconcile(imports: List<String>, graph: AstSymbolGraph): List<AstSymbolNode> {
        return imports.flatMap { imp ->
            graph.index.lookup(imp)
        }
    }
}
