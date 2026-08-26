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
                ProjectLanguage.ELIXIR -> elixir(packageName, title)
                ProjectLanguage.SCALA -> scala(packageName, title)
                ProjectLanguage.CLOJURE -> clojure(packageName, title)
                ProjectLanguage.HASKELL -> haskell(packageName, title)
                ProjectLanguage.DART -> dart(packageName, title)
                ProjectLanguage.C -> c(packageName, title)
                ProjectLanguage.ZIG -> zig(packageName, title)
                ProjectLanguage.LUA -> lua(packageName, title)
                ProjectLanguage.R -> r(packageName, title)
                ProjectLanguage.JULIA -> julia(packageName, title)
                ProjectLanguage.FSHARP -> fsharp(packageName, title)
                ProjectLanguage.ERLANG -> erlang(packageName, title)
                ProjectLanguage.PERL -> perl(packageName, title)
                ProjectLanguage.BASH -> bash(packageName, title)
                ProjectLanguage.POWERSHELL -> powershell(packageName, title)
                ProjectLanguage.OBJECTIVE_C -> objectiveC(packageName, title)
                ProjectLanguage.FORTRAN -> fortran(packageName, title)
                ProjectLanguage.GROOVY -> groovy(packageName, title)
                ProjectLanguage.NIM -> nim(packageName, title)
                ProjectLanguage.SOLIDITY -> solidity(packageName, title)
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

        private fun elixir(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.ELIXIR, "lib/$packageName.ex", "test/${packageName}_test.exs",
            "defmodule ${packageName.replaceFirstChar { it.uppercase() }} do\n  def describe, do: \"$title\"\nend\n",
            "ExUnit.start()\n\ndefmodule ${packageName.replaceFirstChar { it.uppercase() }}Test do\n  use ExUnit.Case\n  test \"describes itself\" do\n    assert ${packageName.replaceFirstChar { it.uppercase() }}.describe() == \"$title\"\n  end\nend\n",
            mapOf("mix.exs" to "defmodule ${packageName.replaceFirstChar { it.uppercase() }}.MixProject do\n  use Mix.Project\n  def project, do: [app: :$packageName, version: \"0.1.0\", elixir: \"~> 1.14\"]\nend\n"),
            verifyScript("mix", "mix test"), "_build/\ndeps/\n", "#"
        )

        private fun scala(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.SCALA, "src/main/scala/$packageName/Main.scala", "src/test/scala/$packageName/MainTest.scala",
            "package $packageName\n\nobject Main { def describe: String = \"$title\" }\n",
            "package $packageName\n\nobject MainTest extends App { assert(Main.describe == \"$title\") }\n",
            mapOf("build.sbt" to "ThisBuild / scalaVersion := \"2.13.14\"\nlibraryDependencies += \"org.scala-lang\" %% \"scala-library\" % \"2.13.14\"\n"),
            verifyScript("sbt", "sbt \"Test / runMain $packageName.MainTest\""), "target/\n", "//"
        )

        private fun clojure(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.CLOJURE, "src/$packageName/core.clj", "test/$packageName/core_test.clj",
            "(ns $packageName.core)\n\n(defn describe [] \"$title\")\n",
            "(ns $packageName.core-test\n  (:require [clojure.test :refer :all]\n            [$packageName.core :as core]))\n\n(deftest describes-itself\n  (is (= \"$title\" (core/describe))))\n",
            mapOf("deps.edn" to "{:paths [\"src\"] :aliases {:test {:extra-paths [\"test\"]}}}\n"),
            verifyScript("clojure", "clojure -M:test -e \"(require '$packageName.core-test)(clojure.test/run-tests '$packageName.core-test)\""), ".cpcache/\n", ";"
        )

        private fun haskell(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.HASKELL, "src/Lib.hs", "test/Spec.hs",
            "module Lib (describe) where\n\ndescribe :: String\ndescribe = \"$title\"\n",
            "module Main where\nimport Lib\nmain :: IO ()\nmain = if describe == \"$title\" then pure () else fail \"unexpected\"\n",
            mapOf("$packageName.cabal" to "cabal-version: 2.4\nname: $packageName\nversion: 0.1.0.0\nlibrary\n  hs-source-dirs: src\n  exposed-modules: Lib\n  build-depends: base\n  default-language: Haskell2010\nexecutable $packageName-test\n  hs-source-dirs: test\n  main-is: Spec.hs\n  build-depends: base, $packageName\n  default-language: Haskell2010\n"),
            verifyScript("cabal", "cabal test"), "dist-newstyle/\n", "--"
        )

        private fun dart(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.DART, "lib/$packageName.dart", "test/${packageName}_test.dart",
            "String describe() => '$title';\n",
            "import 'package:test/test.dart';\nimport 'package:$packageName/$packageName.dart';\nvoid main() { test('describes itself', () { expect(describe(), '$title'); }); }\n",
            mapOf("pubspec.yaml" to "name: $packageName\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'\ndev_dependencies:\n  test: any\n"),
            verifyScript("dart", "dart pub get && dart test"), ".dart_tool/\nbuild/\n", "//"
        )

        private fun c(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.C, "src/main.c", "tests/main_test.c",
            "#include <stdio.h>\nconst char *describe(void) { return \"$title\"; }\nint main(void) { puts(describe()); return 0; }\n",
            "#include <assert.h>\n#include <string.h>\nconst char *describe(void);\nint main(void) { assert(strcmp(describe(), \"$title\") == 0); return 0; }\n",
            mapOf("Makefile" to "CC ?= cc\nCFLAGS ?= -std=c11 -Wall -Wextra\nall: app\napp: src/main.c\n\t${'$'}(CC) ${'$'}(CFLAGS) -o ${'$'}@ ${'$'}<\ntest: src/main.c tests/main_test.c\n\t${'$'}(CC) ${'$'}(CFLAGS) -Dmain=app_main -o build_test src/main.c tests/main_test.c\n\t./build_test\n"),
            verifyScript("cc", "mkdir -p .atropos-build && cc -std=c11 -Wall -Wextra -Dmain=app_main -c src/main.c -o .atropos-build/main.o && cc -std=c11 -Wall -Wextra .atropos-build/main.o tests/main_test.c -o .atropos-build/test && .atropos-build/test"), ".atropos-build/\n*.o\n", "//"
        )

        private fun zig(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.ZIG, "src/main.zig", "src/main_test.zig",
            "pub fn describe() []const u8 { return \"$title\"; }\npub fn main() void { }\n",
            "const std = @import(\"std\");\nconst main = @import(\"main.zig\");\ntest \"describes itself\" { try std.testing.expectEqualStrings(\"$title\", main.describe()); }\n",
            mapOf("build.zig.zon" to ".{ .name = \"$packageName\", .version = \"0.1.0\" }\n"),
            verifyScript("zig", "zig test src/main_test.zig"), ".zig-cache/\nzig-out/\n", "//"
        )

        private fun lua(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.LUA, "$packageName.lua", "test/${packageName}_test.lua",
            "local M = {}\nfunction M.describe() return '$title' end\nreturn M\n",
            "local app = require('$packageName')\nassert(app.describe() == '$title')\nprint('ok')\n",
            mapOf("$packageName-1.0-1.rockspec" to "package = '$packageName'\nversion = '1.0-1'\nsource = { url = 'local' }\ndescription = { summary = '$title' }\nbuild = { type = 'builtin', modules = { ['$packageName'] = '$packageName.lua' } }\n"),
            verifyScript("lua", "LUA_PATH='./?.lua;./?/init.lua' lua test/${packageName}_test.lua"), ".luarocks/\n", "--"
        )

        private fun r(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.R, "R/$packageName.R", "tests/testthat/test-$packageName.R",
            "describe <- function() '$title'\n",
            "stopifnot(describe() == '$title')\n",
            mapOf("DESCRIPTION" to "Package: $packageName\nVersion: 0.1.0\nTitle: $title\nDescription: Generated by ATROPOS.\n"),
            verifyScript("Rscript", "Rscript tests/testthat/test-$packageName.R"), ".Rhistory\n.Rproj.user/\n", "#"
        )

        private fun julia(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.JULIA, "src/$packageName.jl", "test/runtests.jl",
            "module $packageName\nexport describe\ndescribe() = \"$title\"\nend\n",
            "using Test\ninclude(\"../src/$packageName.jl\")\n@test $packageName.describe() == \"$title\"\n",
            mapOf("Project.toml" to "name = \"$packageName\"\nversion = \"0.1.0\"\n"),
            verifyScript("julia", "julia --project=. test/runtests.jl"), ".julia/\n", "#"
        )

        private fun fsharp(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.FSHARP, "src/Library.fs", "tests/Tests.fs",
            "namespace $packageName\n\nmodule Library =\n    let describe () = \"$title\"\n",
            "open $packageName.Library\nif describe () <> \"$title\" then failwith \"unexpected\"\n",
            mapOf("$packageName.fsproj" to "<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup><TargetFramework>net8.0</TargetFramework></PropertyGroup><ItemGroup><Compile Include=\"src/Library.fs\" /><Compile Include=\"tests/Tests.fs\" /></ItemGroup></Project>\n"),
            verifyScript("dotnet", "dotnet test"), "bin/\nobj/\n", "//"
        )

        private fun erlang(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.ERLANG, "src/$packageName.erl", "test/${packageName}_tests.erl",
            "-module($packageName).\n-export([describe/0]).\ndescribe() -> \"$title\".\n",
            "-module(${packageName}_tests).\n-include_lib(\"eunit/include/eunit.hrl\").\ndescribe_test() -> ?assertEqual(\"$title\", $packageName:describe()).\n",
            mapOf("rebar.config" to "{erl_opts, [debug_info]}.\n{deps, []}.\n"),
            verifyScript("rebar3", "rebar3 eunit"), "_build/\n", "%"
        )

        private fun perl(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.PERL, "lib/$packageName.pm", "t/${packageName}.t",
            "package $packageName;\nuse strict; use warnings;\nsub describe { '$title' }\n1;\n",
            "use lib 'lib'; use $packageName; use Test::More tests => 1; is($packageName::describe(), '$title');\n",
            mapOf("Makefile.PL" to "use ExtUtils::MakeMaker; WriteMakefile(NAME => '$packageName');\n"),
            verifyScript("prove", "prove -l t"), "blib/\nMYMETA.*\n", "#"
        )

        private fun bash(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.BASH, "bin/$packageName", "tests/test_$packageName.sh",
            "#!/usr/bin/env bash\nset -euo pipefail\ndescribe() { printf '%s\\n' '$title'; }\nif [[ \"${'$'}{BASH_SOURCE[0]}\" == \"${'$'}0\" ]]; then describe; fi\n",
            "#!/usr/bin/env bash\nset -euo pipefail\nsource bin/$packageName\ntest \"${'$'}(describe)\" = '$title'\n",
            mapOf("Makefile" to "test:\n\tbash tests/test_$packageName.sh\n"),
            verifyScript("bash", "bash tests/test_$packageName.sh"), ".shellcheck-cache/\n", "#"
        )

        private fun powershell(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.POWERSHELL, "src/$packageName.ps1", "tests/$packageName.Tests.ps1",
            "function Get-Description { '$title' }\n",
            ". ./src/$packageName.ps1\nif ((Get-Description) -ne '$title') { throw 'unexpected' }\n",
            mapOf("README.ps1.md" to "# $title PowerShell project\n"),
            verifyScript("pwsh", "pwsh -NoProfile -File tests/$packageName.Tests.ps1"), ".pytest_cache/\n", "#"
        )

        private fun objectiveC(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.OBJECTIVE_C, "src/main.m", "tests/main_test.m",
            "#import <Foundation/Foundation.h>\nconst char *describe(void) { return \"$title\"; }\nint main(void) { @autoreleasepool { NSLog(@\"%s\", describe()); } return 0; }\n",
            "#include <assert.h>\n#include <string.h>\nconst char *describe(void);\nint main(void) { assert(strcmp(describe(), \"$title\") == 0); return 0; }\n",
            mapOf("Makefile" to "test:\n\tclang -fobjc-arc src/main.m tests/main_test.m -framework Foundation -o test && ./test\n"),
            verifyScript("clang", "mkdir -p .atropos-build && clang src/main.m tests/main_test.m -framework Foundation -o .atropos-build/test && .atropos-build/test"), ".atropos-build/\n", "//"
        )

        private fun fortran(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.FORTRAN, "src/main.f90", "tests/main.f90",
            "module app\ncontains\nfunction describe() result(value)\ncharacter(len=256) :: value\nvalue = '$title'\nend function describe\nend module app\n",
            "program test\nuse app\nif (trim(describe()) /= '$title') error stop 1\nend program test\n",
            mapOf("Makefile" to "test:\n\tgfortran tests/main.f90 -o test && ./test\n"),
            verifyScript("gfortran", "mkdir -p .atropos-build && gfortran src/main.f90 tests/main.f90 -o .atropos-build/test && .atropos-build/test"), ".atropos-build/\n", "!"
        )

        private fun groovy(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.GROOVY, "src/Main.groovy", "test/MainTest.groovy",
            "class App { static String describe() { '$title' } }\n",
            "new GroovyShell().evaluate(new File('src/Main.groovy'))\nassert App.describe() == '$title'\n",
            mapOf("build.gradle" to "plugins { id 'groovy' }\nrepositories { mavenCentral() }\n"),
            verifyScript("groovy", "groovy test/MainTest.groovy"), ".gradle/\nbuild/\n", "//"
        )

        private fun nim(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.NIM, "src/main.nim", "tests/main_test.nim",
            "proc describe(): string = \"$title\"\nwhen isMainModule: echo describe()\n",
            "import ../src/main\nassert describe() == \"$title\"\n",
            mapOf("$packageName.nimble" to "version = \"0.1.0\"\nauthor = \"ATROPOS\"\ndescription = \"$title\"\n"),
            verifyScript("nim", "mkdir -p .atropos-build && nim c -r --hints:off --nimcache:.atropos-build tests/main_test.nim"), ".atropos-build/\n", "#"
        )

        private fun solidity(packageName: String, title: String) = LanguageScaffold(
            ProjectLanguage.SOLIDITY, "src/Counter.sol", "test/Counter.t.sol",
            "// SPDX-License-Identifier: MIT\npragma solidity ^0.8.20;\ncontract Counter { string public constant DESCRIPTION = \"$title\"; }\n",
            "// SPDX-License-Identifier: MIT\npragma solidity ^0.8.20;\nimport \"forge-std/Test.sol\";\nimport \"../src/Counter.sol\";\ncontract CounterTest is Test { function testDescription() public { assertEq(new Counter().DESCRIPTION(), \"$title\"); } }\n",
            mapOf("foundry.toml" to "[profile.default]\nsrc = 'src'\ntest = 'test'\n"),
            verifyScript("forge", "forge test"), "out/\ncache/\n", "//"
        )

        private fun verifyScript(tool: String, command: String): String =
            "#!/usr/bin/env sh\nset -eu\ncommand -v $tool >/dev/null 2>&1 || { printf '%s\\n' '$tool is required' >&2; exit 1; }\n$command\nprintf '%s\\n' APP_FACTORY_VERIFY_OK\n"

    }
}
