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
 */
data class LanguageScaffold(
    val language: ProjectLanguage,
    val sourcePath: String,
    val testPath: String,
    val source: String,
    val test: String,
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
    val commentPrefix: String
) {
    companion object {

        fun forLanguage(language: ProjectLanguage, packageName: String, title: String): LanguageScaffold =
            when (language) {
                ProjectLanguage.PYTHON -> python(packageName, title)
                ProjectLanguage.TYPESCRIPT -> typescript(packageName, title)
                ProjectLanguage.GO -> go(packageName, title)
                ProjectLanguage.RUST -> rust(packageName, title)
                ProjectLanguage.KOTLIN -> kotlin(packageName, title)
                ProjectLanguage.JAVA -> java(packageName, title)
                ProjectLanguage.RUBY -> ruby(packageName, title)
                ProjectLanguage.CSHARP -> csharp(packageName, title)
                ProjectLanguage.PHP -> php(packageName, title)
                ProjectLanguage.SWIFT -> swift(packageName, title)
                ProjectLanguage.CPP -> cpp(packageName, title)
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
            source = "",
            test = "",
            manifests = emptyMap(),
            verify = "#!/usr/bin/env sh\nset -eu\n" +
                "printf '%s\\n' 'ATROPOS has no scaffold for $displayName; " +
                "add this project\\'s own build and test commands here.' >&2\nexit 1\n",
            ignore = "*.log\n",
            commentPrefix = "#"
        )

        private fun python(packageName: String, title: String) = LanguageScaffold(
            language = ProjectLanguage.PYTHON,
            sourcePath = "$packageName/__init__.py",
            testPath = "tests/test_$packageName.py",
            source = "\"\"\"$title.\"\"\"\n\n\ndef describe() -> str:\n    return \"$title\"\n",
            test = "from $packageName import describe\n\n\ndef test_describe():\n    assert describe() == \"$title\"\n",
            manifests = mapOf(
                "pyproject.toml" to
                    "[project]\nname = \"$packageName\"\nversion = \"0.1.0\"\nrequires-python = \">=3.11\"\n\n" +
                    "[build-system]\nrequires = [\"setuptools>=68\"]\nbuild-backend = \"setuptools.build_meta\"\n",
                "requirements.txt" to "",
                "requirements-dev.txt" to "pytest\n"
            ),
            // `python -m pytest`, not bare `pytest`: the module form uses the
            // interpreter that is actually on PATH, which is the one the rest
            // of the project was installed into.
            verify = "#!/usr/bin/env sh\nset -eu\ncommand -v python3 >/dev/null 2>&1 || " +
                "{ printf '%s\\n' 'python3 is required' >&2; exit 1; }\n" +
                "python3 -m pytest tests -q\nprintf '%s\\n' APP_FACTORY_VERIFY_OK\n",
            ignore = "__pycache__/\n*.pyc\n.venv/\n.pytest_cache/\ndist/\n",
            commentPrefix = "#"
        )

        private fun typescript(packageName: String, title: String) = LanguageScaffold(
            language = ProjectLanguage.TYPESCRIPT,
            sourcePath = "src/index.ts",
            testPath = "src/index.test.ts",
            source = "export function describe(): string {\n  return \"$title\";\n}\n",
            test = "import { describe as describeApp } from \"./index\";\n\n" +
                "test(\"describes itself\", () => {\n  expect(describeApp()).toBe(\"$title\");\n});\n",
            manifests = mapOf(
                "package.json" to
                    "{\n  \"name\": \"$packageName\",\n  \"version\": \"0.1.0\",\n  \"type\": \"module\",\n" +
                    "  \"scripts\": { \"test\": \"node --test\" }\n}\n",
                "tsconfig.json" to
                    "{\n  \"compilerOptions\": {\n    \"target\": \"ES2022\",\n    \"module\": \"ES2022\",\n" +
                    "    \"strict\": true,\n    \"outDir\": \"dist\"\n  },\n  \"include\": [\"src\"]\n}\n"
            ),
            verify = "#!/usr/bin/env sh\nset -eu\ncommand -v node >/dev/null 2>&1 || " +
                "{ printf '%s\\n' 'node is required' >&2; exit 1; }\nnpm test\n" +
                "printf '%s\\n' APP_FACTORY_VERIFY_OK\n",
            ignore = "node_modules/\ndist/\n*.log\n",
            commentPrefix = "//"
        )

        private fun go(packageName: String, title: String) = LanguageScaffold(
            language = ProjectLanguage.GO,
            sourcePath = "main.go",
            testPath = "main_test.go",
            source = "package main\n\nimport \"fmt\"\n\nfunc Describe() string {\n\treturn \"$title\"\n}\n\n" +
                "func main() {\n\tfmt.Println(Describe())\n}\n",
            test = "package main\n\nimport \"testing\"\n\nfunc TestDescribe(t *testing.T) {\n" +
                "\tif Describe() != \"$title\" {\n\t\tt.Fatal(\"unexpected description\")\n\t}\n}\n",
            manifests = mapOf("go.mod" to "module $packageName\n\ngo 1.22\n"),
            verify = "#!/usr/bin/env sh\nset -eu\ncommand -v go >/dev/null 2>&1 || " +
                "{ printf '%s\\n' 'go is required' >&2; exit 1; }\ngo test ./...\n" +
                "printf '%s\\n' APP_FACTORY_VERIFY_OK\n",
            ignore = "bin/\n*.exe\n",
            commentPrefix = "//"
        )

        private fun rust(packageName: String, title: String) = LanguageScaffold(
            language = ProjectLanguage.RUST,
            sourcePath = "src/lib.rs",
            testPath = "tests/describe.rs",
            source = "pub fn describe() -> &'static str {\n    \"$title\"\n}\n",
            test = "use $packageName::describe;\n\n#[test]\nfn describes_itself() {\n" +
                "    assert_eq!(describe(), \"$title\");\n}\n",
            manifests = mapOf(
                "Cargo.toml" to "[package]\nname = \"$packageName\"\nversion = \"0.1.0\"\nedition = \"2021\"\n"
            ),
            verify = "#!/usr/bin/env sh\nset -eu\ncommand -v cargo >/dev/null 2>&1 || " +
                "{ printf '%s\\n' 'cargo is required' >&2; exit 1; }\ncargo test\n" +
                "printf '%s\\n' APP_FACTORY_VERIFY_OK\n",
            ignore = "target/\n",
            commentPrefix = "//"
        )

        private fun kotlin(packageName: String, title: String) = LanguageScaffold(
            language = ProjectLanguage.KOTLIN,
            sourcePath = "src/main/kotlin/$packageName/Main.kt",
            testPath = "src/test/kotlin/$packageName/MainTest.kt",
            source = "",
            test = "",
            manifests = emptyMap(),
            verify = "#!/usr/bin/env sh\nset -eu\nmain=src/main/kotlin/$packageName/Main.kt\n" +
                "test=src/test/kotlin/$packageName/MainTest.kt\n" +
                "command -v kotlinc >/dev/null 2>&1 || " +
                "{ printf '%s\\n' 'kotlinc is required for generated-test verification' >&2; exit 1; }\n" +
                "mkdir -p build\nkotlinc \"\$main\" \"\$test\" -include-runtime -d build/app-tests.jar\n" +
                "java -cp build/app-tests.jar ${packageName}.MainTestKt\n" +
                "printf '%s\\n' APP_FACTORY_VERIFY_OK\n",
            ignore = "build/\n.gradle/\n.idea/\n",
            commentPrefix = "//"
        )

        private fun java(packageName: String, title: String) = LanguageScaffold(
            language = ProjectLanguage.JAVA,
            sourcePath = "src/main/java/$packageName/Main.java",
            testPath = "src/test/java/$packageName/MainTest.java",
            source = "package $packageName;\n\npublic final class Main {\n" +
                "    public static String describe() {\n        return \"$title\";\n    }\n\n" +
                "    public static void main(String[] args) {\n        System.out.println(describe());\n    }\n}\n",
            test = "package $packageName;\n\npublic final class MainTest {\n" +
                "    public static void main(String[] args) {\n" +
                "        if (!Main.describe().equals(\"$title\")) throw new AssertionError(\"unexpected\");\n" +
                "        System.out.println(\"ok\");\n    }\n}\n",
            manifests = mapOf(
                "pom.xml" to "<project>\n  <modelVersion>4.0.0</modelVersion>\n" +
                    "  <groupId>$packageName</groupId>\n  <artifactId>$packageName</artifactId>\n" +
                    "  <version>0.1.0</version>\n</project>\n"
            ),
            verify = "#!/usr/bin/env sh\nset -eu\ncommand -v javac >/dev/null 2>&1 || " +
                "{ printf '%s\\n' 'javac is required' >&2; exit 1; }\nmkdir -p build\n" +
                "javac -d build src/main/java/$packageName/Main.java src/test/java/$packageName/MainTest.java\n" +
                "java -cp build ${packageName}.MainTest\nprintf '%s\\n' APP_FACTORY_VERIFY_OK\n",
            ignore = "build/\ntarget/\n*.class\n",
            commentPrefix = "//"
        )

        private fun ruby(packageName: String, title: String) = LanguageScaffold(
            language = ProjectLanguage.RUBY,
            sourcePath = "lib/$packageName.rb",
            testPath = "spec/${packageName}_spec.rb",
            source = "# frozen_string_literal: true\n\nmodule $packageName\n" +
                "  def self.describe\n    '$title'\n  end\nend\n",
            test = "require_relative '../lib/$packageName'\n\n" +
                "RSpec.describe $packageName do\n  it 'describes itself' do\n" +
                "    expect($packageName.describe).to eq('$title')\n  end\nend\n",
            manifests = mapOf("Gemfile" to "source 'https://rubygems.org'\n\ngem 'rspec'\n"),
            verify = "#!/usr/bin/env sh\nset -eu\ncommand -v rspec >/dev/null 2>&1 || " +
                "{ printf '%s\\n' 'rspec is required' >&2; exit 1; }\nrspec\n" +
                "printf '%s\\n' APP_FACTORY_VERIFY_OK\n",
            ignore = ".bundle/\nvendor/bundle/\n",
            commentPrefix = "#"
        )

        private fun csharp(packageName: String, title: String) = LanguageScaffold(
            language = ProjectLanguage.CSHARP,
            sourcePath = "src/Program.cs",
            testPath = "tests/ProgramTests.cs",
            source = "public static class Program\n{\n" +
                "    public static string Describe() => \"$title\";\n\n" +
                "    public static void Main() => System.Console.WriteLine(Describe());\n}\n",
            test = "public static class ProgramTests\n{\n    public static void Main()\n    {\n" +
                "        if (Program.Describe() != \"$title\") throw new System.Exception(\"unexpected\");\n" +
                "        System.Console.WriteLine(\"ok\");\n    }\n}\n",
            manifests = mapOf(
                "$packageName.csproj" to "<Project Sdk=\"Microsoft.NET.Sdk\">\n  <PropertyGroup>\n" +
                    "    <OutputType>Exe</OutputType>\n    <TargetFramework>net8.0</TargetFramework>\n" +
                    "  </PropertyGroup>\n</Project>\n"
            ),
            verify = "#!/usr/bin/env sh\nset -eu\ncommand -v dotnet >/dev/null 2>&1 || " +
                "{ printf '%s\\n' 'dotnet is required' >&2; exit 1; }\ndotnet build\n" +
                "printf '%s\\n' APP_FACTORY_VERIFY_OK\n",
            ignore = "bin/\nobj/\n",
            commentPrefix = "//"
        )

        private fun php(packageName: String, title: String) = LanguageScaffold(
            language = ProjectLanguage.PHP,
            sourcePath = "src/$packageName.php",
            testPath = "tests/${packageName}Test.php",
            source = "<?php\n\nfunction describe(): string\n{\n    return '$title';\n}\n",
            test = "<?php\n\nrequire __DIR__ . '/../src/$packageName.php';\n\n" +
                "if (describe() !== '$title') {\n    fwrite(STDERR, \"unexpected\\n\");\n    exit(1);\n}\n" +
                "echo \"ok\\n\";\n",
            manifests = mapOf(
                "composer.json" to "{\n  \"name\": \"atropos/$packageName\",\n" +
                    "  \"require\": { \"php\": \">=8.2\" }\n}\n"
            ),
            verify = "#!/usr/bin/env sh\nset -eu\ncommand -v php >/dev/null 2>&1 || " +
                "{ printf '%s\\n' 'php is required' >&2; exit 1; }\nphp tests/${packageName}Test.php\n" +
                "printf '%s\\n' APP_FACTORY_VERIFY_OK\n",
            ignore = "vendor/\n",
            commentPrefix = "//"
        )

        private fun swift(packageName: String, title: String) = LanguageScaffold(
            language = ProjectLanguage.SWIFT,
            sourcePath = "Sources/$packageName/$packageName.swift",
            testPath = "Tests/${packageName}Tests/${packageName}Tests.swift",
            source = "public func describe() -> String {\n    return \"$title\"\n}\n",
            test = "import XCTest\n@testable import $packageName\n\n" +
                "final class ${packageName}Tests: XCTestCase {\n" +
                "    func testDescribe() { XCTAssertEqual(describe(), \"$title\") }\n}\n",
            manifests = mapOf(
                "Package.swift" to "// swift-tools-version:5.9\nimport PackageDescription\n\n" +
                    "let package = Package(\n    name: \"$packageName\",\n    targets: [\n" +
                    "        .target(name: \"$packageName\"),\n" +
                    "        .testTarget(name: \"${packageName}Tests\", dependencies: [\"$packageName\"])\n    ]\n)\n"
            ),
            verify = "#!/usr/bin/env sh\nset -eu\ncommand -v swift >/dev/null 2>&1 || " +
                "{ printf '%s\\n' 'swift is required' >&2; exit 1; }\nswift test\n" +
                "printf '%s\\n' APP_FACTORY_VERIFY_OK\n",
            ignore = ".build/\n*.xcodeproj\n",
            commentPrefix = "//"
        )

        private fun cpp(packageName: String, title: String) = LanguageScaffold(
            language = ProjectLanguage.CPP,
            sourcePath = "src/main.cpp",
            testPath = "tests/main_test.cpp",
            source = "#include <string>\n\nstd::string describe() {\n    return \"$title\";\n}\n",
            test = "#include <cassert>\n#include <string>\n\nstd::string describe();\n\n" +
                "int main() {\n    assert(describe() == \"$title\");\n    return 0;\n}\n",
            manifests = mapOf(
                "CMakeLists.txt" to "cmake_minimum_required(VERSION 3.20)\nproject($packageName CXX)\n" +
                    "set(CMAKE_CXX_STANDARD 20)\nadd_executable($packageName src/main.cpp)\n"
            ),
            verify = "#!/usr/bin/env sh\nset -eu\ncommand -v cmake >/dev/null 2>&1 || " +
                "{ printf '%s\\n' 'cmake is required' >&2; exit 1; }\ncmake -S . -B build && cmake --build build\n" +
                "printf '%s\\n' APP_FACTORY_VERIFY_OK\n",
            ignore = "build/\n",
            commentPrefix = "//"
        )

    }
}