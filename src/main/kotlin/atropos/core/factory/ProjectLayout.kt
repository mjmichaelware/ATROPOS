/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

/**
 * One answer to "where does this project's code live", for everyone who asks.
 *
 * The question was asked in three places and answered differently in each:
 * [RepoScaffold] laid files out, [AppGeneratedBehaviorGuard] looked for them,
 * and [FactoryCompletionVerifier] audited them. Each recomputed the layout, so
 * teaching one of them about a new language left the other two failing the run
 * for a language nobody asked for.
 *
 * It also answers a question none of them used to ask: when the document
 * declares its own tree, that tree is the layout. A specification naming two
 * hundred files was previously answered with eleven of the scaffold's own.
 */
data class ProjectLayout(
    val detection: ProjectLanguage.Detection,
    val language: ProjectLanguage,
    val scaffold: LanguageScaffold,
    /** Every path the source document declared, empty when it declared none. */
    val declared: List<DeclaredProjectTree.Entry>,
    val sourcePath: String,
    val testPath: String,
    /** How the test file refers to the source file, in this language. */
    val importReference: String
) {
    val declaresItsOwnTree: Boolean get() = declared.isNotEmpty()

    companion object {

        /** Files a document names that mark the program's entry point. */
        private val ENTRY_NAMES = listOf("main", "__main__", "index", "app", "cli", "program")

        /** Directories a document names that hold tests. */
        private val TEST_DIRECTORIES = setOf("tests", "test", "spec", "specs")

        private val EXTENSIONS = mapOf(
            ProjectLanguage.PYTHON to "py",
            ProjectLanguage.TYPESCRIPT to "ts",
            ProjectLanguage.GO to "go",
            ProjectLanguage.RUST to "rs",
            ProjectLanguage.KOTLIN to "kt",
            ProjectLanguage.JAVA to "java",
            ProjectLanguage.RUBY to "rb",
            ProjectLanguage.CSHARP to "cs",
            ProjectLanguage.PHP to "php",
            ProjectLanguage.SWIFT to "swift",
            ProjectLanguage.CPP to "cpp"
        )

        fun resolve(spec: AppProjectSpec): ProjectLayout {
            val packageName = AppProjectGenerator.safeName(spec.intent.name)
            val detection = ProjectLanguage.detect(spec.prompt)
            val language = ProjectLanguage.layoutFor(detection)
            val scaffold = when (detection) {
                is ProjectLanguage.Detection.Unsupported -> LanguageScaffold.generic(detection.displayName)
                else -> LanguageScaffold.forLanguage(language, packageName)
            }
            val declared =
                if (detection is ProjectLanguage.Detection.Unsupported) emptyList()
                else DeclaredProjectTree.read(spec.prompt)

            val extension = EXTENSIONS[language]
            val files = declared.filterNot { it.isDirectory }.map { it.path }
            val declaredSource = extension?.let { chooseEntry(files, it) }
            val declaredTest = extension?.let { chooseTest(files, it) }

            // Both or neither. Putting the program in the document's tree and
            // its tests in the scaffold's would leave a test that cannot see
            // what it is testing.
            //
            // And only where the language can be told, from the paths alone,
            // how the test reaches the source. Rust resolves modules through
            // `mod` declarations, Java and C# through a package that must
            // mirror the directory, Swift through targets in Package.swift --
            // a path the document chose is not enough to compute any of those,
            // and a seed program that does not compile is worse than one in a
            // directory the document did not name. Those languages still get
            // the declared tree as files; only the seed program stays put.
            val programCanFollowTheTree = when (language) {
                ProjectLanguage.PYTHON,
                ProjectLanguage.TYPESCRIPT,
                ProjectLanguage.RUBY,
                ProjectLanguage.PHP,
                ProjectLanguage.CPP -> true
                // Go compiles a directory as one package, so the test has to
                // sit beside the source it calls.
                ProjectLanguage.GO ->
                    declaredSource?.substringBeforeLast('/', "") ==
                        declaredTest?.substringBeforeLast('/', "")
                else -> false
            }
            val useDeclared = declaredSource != null && declaredTest != null && programCanFollowTheTree
            val sourcePath = if (useDeclared) declaredSource!! else scaffold.sourcePath
            val testPath = if (useDeclared) declaredTest!! else scaffold.testPath

            return ProjectLayout(
                detection = detection,
                language = language,
                scaffold = scaffold.withPaths(sourcePath, testPath),
                declared = declared,
                sourcePath = sourcePath,
                testPath = testPath,
                importReference = importReference(language, sourcePath, testPath, packageName)
            )
        }

        /**
         * The declared file most likely to be where the program starts.
         *
         * Ranked rather than first-matched: a document lists its entry point
         * beside forty other modules, and `app/main.py` is the one to put a
         * program in even though `app/config.py` appears first.
         */
        private fun chooseEntry(files: List<String>, extension: String): String? =
            files.filter { it.endsWith(".$extension") && !isUnderTests(it) }
                .minByOrNull { path ->
                    val base = path.substringAfterLast('/').removeSuffix(".$extension")
                    val rank = ENTRY_NAMES.indexOf(base).let { if (it < 0) ENTRY_NAMES.size else it }
                    rank * 1_000 + path.count { character -> character == '/' } * 10 + path.length
                }

        private fun chooseTest(files: List<String>, extension: String): String? =
            files.filter { it.endsWith(".$extension") && isUnderTests(it) }
                .minByOrNull { path -> path.count { character -> character == '/' } * 10 + path.length }

        private fun isUnderTests(path: String): Boolean {
            val segments = path.split('/')
            if (segments.dropLast(1).any { it.lowercase() in TEST_DIRECTORIES }) return true
            val base = segments.last().lowercase()
            return base.startsWith("test_") || base.contains("_test.") || base.contains(".test.")
        }

        /**
         * How the test file names the source file.
         *
         * A generated test that imports the wrong module is a repository that
         * fails its own verification for a reason unrelated to its code -- the
         * exact failure this whole layout exists to avoid.
         */
        private fun importReference(
            language: ProjectLanguage,
            sourcePath: String,
            testPath: String,
            packageName: String
        ): String = when (language) {
            ProjectLanguage.PYTHON ->
                sourcePath.removeSuffix(".py").replace('/', '.').removeSuffix(".__init__")
            ProjectLanguage.TYPESCRIPT -> relativeReference(testPath, sourcePath)
            ProjectLanguage.RUBY -> relativeReference(testPath, sourcePath).removeSuffix(".rb")
            ProjectLanguage.PHP -> relativeReference(testPath, sourcePath)
            ProjectLanguage.CPP -> relativeReference(testPath, sourcePath.replace(".cpp", ".hpp"))
            ProjectLanguage.JAVA, ProjectLanguage.KOTLIN -> packageName
            else -> packageName
        }

        /** A path to [target] as written from the directory holding [from]. */
        private fun relativeReference(from: String, target: String): String {
            val fromParts = from.split('/').dropLast(1)
            val targetParts = target.split('/')
            var shared = 0
            while (shared < fromParts.size && shared < targetParts.size - 1 &&
                fromParts[shared] == targetParts[shared]
            ) {
                shared++
            }
            val up = List(fromParts.size - shared) { ".." }
            val down = targetParts.drop(shared)
            val joined = (up + down).joinToString("/")
            return if (joined.startsWith("..") || joined.startsWith("/")) joined else "./$joined"
        }
    }
}
