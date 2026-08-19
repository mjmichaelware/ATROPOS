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
            }

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
    }
}
