/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

/**
 * Where source, tests and verification live, per language.
 *
 * A scaffold is a claim about where code goes, and [RepoScaffold] made that
 * claim in Kotlin for every project regardless of what the operator asked for.
 * The damage is not the wasted template: it is that every file a provider
 * writes afterwards is filed into a tree for the wrong ecosystem, beside a
 * `verify.sh` that cannot run any of it. The generated repository then fails
 * its own verification for a reason that has nothing to do with the code.
 *
 * Each layout is the conventional one for its ecosystem, because a generated
 * repository that a human opens should look like every other repository in
 * that language rather than like something a tool invented.
 *
 * The program that fills the layout comes from [LanguageBehaviorTemplate]; this
 * carries only the shape around it.
 */
data class LanguageScaffold(
    val language: ProjectLanguage,
    val sourcePath: String,
    val testPath: String,
    val manifests: Map<String, String>,
    val verify: String,
    val ignore: String,
    /**
     * How a comment starts in this language.
     *
     * Lineage is prepended to generated source, and `//` at the top of a
     * Python file is a syntax error -- the provenance header would have broken
     * the very file it was documenting.
     */
    val commentPrefix: String,
    /**
     * Whether lineage goes after the file's first line instead of before it.
     *
     * PHP is the case: a comment above `<?php` is not a comment, it is output.
     * The header would have printed itself on every run of the program.
     */
    val lineageAfterFirstLine: Boolean = false
) {
    /** Put the provenance header where this language will read it as a comment. */
    fun withLineage(source: String, lineage: String): String {
        if (lineage.isEmpty() || source.isEmpty()) return source
        if (!lineageAfterFirstLine) return lineage + source
        val firstBreak = source.indexOf('\n')
        if (firstBreak < 0) return source + "\n" + lineage
        return source.substring(0, firstBreak + 1) + "\n" + lineage + source.substring(firstBreak + 1)
    }

    /**
     * The same scaffold, verifying the files the project actually has.
     *
     * `verify.sh` names the paths it compiles and runs. When the source
     * document declares its own tree, the program moves and the script has to
     * move with it -- otherwise the generated repository fails its own
     * verification for a reason that has nothing to do with its code, which is
     * the failure this whole layout exists to avoid.
     *
     * Only the languages whose seed program can follow a declared tree need a
     * rewrite; the rest verify by build tool or by glob and are unaffected.
     */
    fun withPaths(sourcePath: String, testPath: String): LanguageScaffold {
        if (sourcePath == this.sourcePath && testPath == this.testPath) return this
        val testDirectory = testPath.substringBeforeLast('/', ".")
        val rewritten = when (language) {
            ProjectLanguage.PYTHON -> shell(
                requires("python3") +
                    "if python3 -c 'import pytest' >/dev/null 2>&1; then\n" +
                    "  python3 -m pytest $testDirectory -q\nelse\n" +
                    "  PYTHONPATH=. python3 $testPath\nfi\n"
            )
            ProjectLanguage.TYPESCRIPT -> shell(requires("node") + "node --test $testPath\n")
            ProjectLanguage.RUBY -> shell(
                requires("ruby") + "ruby -I${sourcePath.substringBeforeLast('/', ".")} $testPath\n"
            )
            ProjectLanguage.PHP -> shell(requires("php") + "php $testPath\n")
            ProjectLanguage.CPP -> shell(
                requires("c++") + "mkdir -p build\n" +
                    "c++ -std=c++17 -DAPP_FACTORY_NO_MAIN -o build/app_tests $sourcePath $testPath\n" +
                    "./build/app_tests\n"
            )
            else -> verify
        }
        return copy(sourcePath = sourcePath, testPath = testPath, verify = rewritten)
    }

    companion object {

        const val OK = "printf '%s\\n' APP_FACTORY_VERIFY_OK\n"

        fun shell(body: String) = "#!/usr/bin/env sh\nset -eu\n$body$OK"

        fun requires(tool: String) =
            "command -v $tool >/dev/null 2>&1 || { printf '%s\\n' '$tool is required' >&2; exit 1; }\n"

        fun forLanguage(language: ProjectLanguage, packageName: String): LanguageScaffold =
            when (language) {
                ProjectLanguage.PYTHON -> python(packageName)
                ProjectLanguage.TYPESCRIPT -> typescript(packageName)
                ProjectLanguage.GO -> go(packageName)
                ProjectLanguage.RUST -> rust(packageName)
                ProjectLanguage.KOTLIN -> kotlin(packageName)
                ProjectLanguage.JAVA -> java(packageName)
                ProjectLanguage.RUBY -> ruby(packageName)
                ProjectLanguage.CSHARP -> csharp(packageName)
                ProjectLanguage.PHP -> php(packageName)
                ProjectLanguage.SWIFT -> swift(packageName)
                ProjectLanguage.CPP -> cpp(packageName)
            }

        /**
         * A tree for a language this cannot lay out.
         *
         * No source file at all, on purpose. Writing a `Main.kt` into an
         * Elixir project is how the old default failed; writing a `main.ex`
         * this cannot verify would fail the same way one language further on.
         * The repository gets its documentation, its licence and a verify.sh
         * that refuses honestly, and the operator supplies the layout their
         * ecosystem expects.
         */
        fun generic(displayName: String) = LanguageScaffold(
            language = ProjectLanguage.KOTLIN,
            sourcePath = "",
            testPath = "",
            manifests = emptyMap(),
            verify = "#!/usr/bin/env sh\nset -eu\n" +
                "printf '%s\\n' 'ATROPOS has no scaffold for $displayName; " +
                "add this project\\'s own build and test commands here.' >&2\nexit 1\n",
            ignore = "*.log\n",
            commentPrefix = "#"
        )

        private fun python(packageName: String) = LanguageScaffold(
            language = ProjectLanguage.PYTHON,
            sourcePath = "$packageName/__init__.py",
            testPath = "tests/test_$packageName.py",
            manifests = mapOf(
                "pyproject.toml" to
                    "[project]\nname = \"$packageName\"\nversion = \"0.1.0\"\nrequires-python = \">=3.11\"\n\n" +
                    "[build-system]\nrequires = [\"setuptools>=68\"]\nbuild-backend = \"setuptools.build_meta\"\n",
                "requirements.txt" to "",
                "requirements-dev.txt" to "pytest\n"
            ),
            // `python3 -m pytest`, not bare `pytest`: the module form uses the
            // interpreter actually on PATH. And pytest is not assumed -- a
            // phone often has no pytest, and a verification that cannot run is
            // the failure this whole scaffold exists to avoid. The test module
            // runs its own cases when executed directly.
            verify = shell(
                requires("python3") +
                    "if python3 -c 'import pytest' >/dev/null 2>&1; then\n" +
                    "  python3 -m pytest tests -q\nelse\n" +
                    "  PYTHONPATH=. python3 tests/test_$packageName.py\nfi\n"
            ),
            ignore = "__pycache__/\n*.pyc\n.venv/\n.pytest_cache/\ndist/\n",
            commentPrefix = "#"
        )

        private fun typescript(packageName: String) = LanguageScaffold(
            language = ProjectLanguage.TYPESCRIPT,
            sourcePath = "src/index.ts",
            testPath = "src/index.test.ts",
            manifests = mapOf(
                "package.json" to
                    "{\n  \"name\": \"$packageName\",\n  \"version\": \"0.1.0\",\n  \"type\": \"module\",\n" +
                    "  \"scripts\": { \"test\": \"node --test src/*.test.ts\" }\n}\n",
                "tsconfig.json" to
                    "{\n  \"compilerOptions\": {\n    \"target\": \"ES2022\",\n    \"module\": \"ES2022\",\n" +
                    "    \"strict\": true,\n    \"allowImportingTsExtensions\": true,\n" +
                    "    \"noEmit\": true\n  },\n  \"include\": [\"src\"]\n}\n"
            ),
            // node, not npm: `npm test` needs an install step this has no
            // network for, and Node runs the TypeScript directly.
            verify = shell(requires("node") + "node --test src/*.test.ts\n"),
            ignore = "node_modules/\ndist/\n*.log\n",
            commentPrefix = "//"
        )

        private fun go(packageName: String) = LanguageScaffold(
            language = ProjectLanguage.GO,
            sourcePath = "main.go",
            testPath = "main_test.go",
            manifests = mapOf("go.mod" to "module $packageName\n\ngo 1.22\n"),
            verify = shell(requires("go") + "go test ./...\n"),
            ignore = "bin/\n*.exe\n",
            commentPrefix = "//"
        )

        private fun rust(packageName: String) = LanguageScaffold(
            language = ProjectLanguage.RUST,
            // A `tests/` integration test would need a library target as well
            // as the binary; a `mod tests` beside main.rs keeps one crate and
            // still puts the assertions in their own file.
            sourcePath = "src/main.rs",
            testPath = "src/tests.rs",
            manifests = mapOf(
                "Cargo.toml" to "[package]\nname = \"$packageName\"\nversion = \"0.1.0\"\nedition = \"2021\"\n"
            ),
            verify = shell(requires("cargo") + "cargo test\n"),
            ignore = "target/\n",
            commentPrefix = "//"
        )

        private fun kotlin(packageName: String) = LanguageScaffold(
            language = ProjectLanguage.KOTLIN,
            sourcePath = "src/main/kotlin/$packageName/Main.kt",
            testPath = "src/test/kotlin/$packageName/MainTest.kt",
            manifests = emptyMap(),
            verify = "#!/usr/bin/env sh\nset -eu\nmain=src/main/kotlin/$packageName/Main.kt\n" +
                "test=src/test/kotlin/$packageName/MainTest.kt\n" +
                "command -v kotlinc >/dev/null 2>&1 || " +
                "{ printf '%s\\n' 'kotlinc is required for generated-test verification' >&2; exit 1; }\n" +
                "mkdir -p build\nkotlinc \"\$main\" \"\$test\" -include-runtime -d build/app-tests.jar\n" +
                "java -cp build/app-tests.jar ${packageName}.MainTestKt\n" + OK,
            ignore = "build/\n.gradle/\n.idea/\n",
            commentPrefix = "//"
        )

        private fun java(packageName: String) = LanguageScaffold(
            language = ProjectLanguage.JAVA,
            sourcePath = "src/main/java/$packageName/Main.java",
            testPath = "src/test/java/$packageName/MainTest.java",
            manifests = mapOf(
                "pom.xml" to "<project>\n  <modelVersion>4.0.0</modelVersion>\n" +
                    "  <groupId>$packageName</groupId>\n  <artifactId>$packageName</artifactId>\n" +
                    "  <version>0.1.0</version>\n</project>\n"
            ),
            // javac and java, not Maven: verification must not depend on a
            // dependency resolver reaching the network.
            verify = shell(
                requires("javac") + "mkdir -p build\n" +
                    "javac -d build src/main/java/$packageName/Main.java src/test/java/$packageName/MainTest.java\n" +
                    "java -cp build ${packageName}.MainTest\n"
            ),
            ignore = "build/\ntarget/\n*.class\n",
            commentPrefix = "//"
        )

        private fun ruby(packageName: String) = LanguageScaffold(
            language = ProjectLanguage.RUBY,
            sourcePath = "lib/$packageName.rb",
            // minitest, not rspec: minitest ships with Ruby, so verification
            // works without `bundle install` and without a network.
            testPath = "test/test_$packageName.rb",
            manifests = mapOf("Gemfile" to "source 'https://rubygems.org'\n\ngem 'minitest'\n"),
            verify = shell(requires("ruby") + "ruby -Ilib test/test_$packageName.rb\n"),
            ignore = ".bundle/\nvendor/bundle/\n",
            commentPrefix = "#"
        )

        private fun csharp(packageName: String) = LanguageScaffold(
            language = ProjectLanguage.CSHARP,
            sourcePath = "src/Program.cs",
            testPath = "tests/ProgramTests.cs",
            manifests = mapOf(
                // Two entry points in one project do not compile, so the tests
                // are their own executable project referencing the first.
                "$packageName.csproj" to "<Project Sdk=\"Microsoft.NET.Sdk\">\n  <PropertyGroup>\n" +
                    "    <OutputType>Exe</OutputType>\n    <TargetFramework>net8.0</TargetFramework>\n" +
                    "    <StartupObject>Program</StartupObject>\n  </PropertyGroup>\n" +
                    "  <ItemGroup>\n    <Compile Remove=\"tests/**\" />\n  </ItemGroup>\n</Project>\n",
                "tests/${packageName}.Tests.csproj" to "<Project Sdk=\"Microsoft.NET.Sdk\">\n  <PropertyGroup>\n" +
                    "    <OutputType>Exe</OutputType>\n    <TargetFramework>net8.0</TargetFramework>\n" +
                    "    <StartupObject>ProgramTests</StartupObject>\n  </PropertyGroup>\n" +
                    "  <ItemGroup>\n    <Compile Include=\"../src/Program.cs\" />\n  </ItemGroup>\n</Project>\n"
            ),
            verify = shell(requires("dotnet") + "dotnet run --project tests/${packageName}.Tests.csproj\n"),
            ignore = "bin/\nobj/\n",
            commentPrefix = "//"
        )

        private fun php(packageName: String) = LanguageScaffold(
            language = ProjectLanguage.PHP,
            sourcePath = "src/$packageName.php",
            testPath = "tests/${packageName}Test.php",
            manifests = mapOf(
                "composer.json" to "{\n  \"name\": \"atropos/$packageName\",\n" +
                    "  \"require\": { \"php\": \">=8.2\" }\n}\n"
            ),
            verify = shell(requires("php") + "php tests/${packageName}Test.php\n"),
            ignore = "vendor/\n",
            commentPrefix = "//",
            // A comment above `<?php` is output, not a comment.
            lineageAfterFirstLine = true
        )

        private fun swift(packageName: String) = LanguageScaffold(
            language = ProjectLanguage.SWIFT,
            sourcePath = "Sources/$packageName/$packageName.swift",
            testPath = "Tests/${packageName}Tests/${packageName}Tests.swift",
            manifests = mapOf(
                // A library target holds the behaviour so the tests can import
                // it; a thin executable target beside it is the command.
                "Package.swift" to "// swift-tools-version:5.9\nimport PackageDescription\n\n" +
                    "let package = Package(\n    name: \"$packageName\",\n    targets: [\n" +
                    "        .target(name: \"$packageName\"),\n" +
                    "        .executableTarget(name: \"${packageName}Cli\", dependencies: [\"$packageName\"]),\n" +
                    "        .testTarget(name: \"${packageName}Tests\", dependencies: [\"$packageName\"])\n    ]\n)\n"
            ),
            verify = shell(requires("swift") + "swift test\n"),
            ignore = ".build/\n*.xcodeproj\n",
            commentPrefix = "//"
        )

        private fun cpp(packageName: String) = LanguageScaffold(
            language = ProjectLanguage.CPP,
            sourcePath = "src/app.cpp",
            testPath = "tests/app_test.cpp",
            manifests = mapOf(
                "CMakeLists.txt" to "cmake_minimum_required(VERSION 3.20)\nproject($packageName CXX)\n" +
                    "set(CMAKE_CXX_STANDARD 17)\nset(CMAKE_CXX_STANDARD_REQUIRED ON)\n\n" +
                    "add_executable($packageName src/app.cpp)\n\n" +
                    "add_executable(${packageName}_tests src/app.cpp tests/app_test.cpp)\n" +
                    "target_compile_definitions(${packageName}_tests PRIVATE APP_FACTORY_NO_MAIN)\n\n" +
                    "enable_testing()\nadd_test(NAME cli COMMAND ${packageName}_tests)\n"
            ),
            verify = shell(
                requires("cmake") +
                    "cmake -S . -B build\ncmake --build build\n./build/${packageName}_tests\n"
            ),
            ignore = "build/\n",
            commentPrefix = "//"
        )
    }
}
