/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.security.RedactionFilter
import java.util.Locale

/**
 * A generated program that runs, in the language the project is written in.
 *
 * [AppSourceTemplate] renders a real CLI -- state, commands, usage, exit codes
 * and executable assertions -- and [AppGeneratedBehaviorGuard] exists to reject
 * anything less. Both were Kotlin-only. When the factory learned to lay a
 * repository out in eleven languages, every non-Kotlin project got a
 * `describe()` stub instead: exactly the scaffold-shaped output the guard was
 * written to prevent, now waved through because the guard could not read the
 * language.
 *
 * So the seed program is rendered per language here, to one contract:
 *
 * - `run_app(args, state)` returns an exit code, stdout text and stderr text
 * - `--help` succeeds and prints usage; no arguments fails and prints usage
 * - `add <value>` appends and reports; a missing value fails
 * - `list` numbers the items, or says there are none
 * - every declared feature is its own command
 * - anything else is `unknown command: X` and a non-zero exit
 * - the tests exercise each of those, and fail the build when one breaks
 *
 * The seed is not the application. Providers replace it through the DAG; what
 * it has to do is compile, run, and prove it ran before any of that starts.
 */
class LanguageBehaviorTemplate(
    private val kotlinTemplate: AppSourceTemplate = AppSourceTemplate(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    /** The source file, the test file, and whatever else the language needs beside them. */
    data class Program(
        val source: String,
        val test: String,
        val extraSources: Map<String, String> = emptyMap()
    )

    fun render(language: ProjectLanguage, spec: AppProjectSpec, packageName: String): Program {
        // Kotlin keeps AppSourceTemplate, which already renders web, service,
        // desktop and expression variants this does not attempt to translate.
        if (language == ProjectLanguage.KOTLIN) {
            return Program(
                source = kotlinTemplate.mainSource(spec, packageName),
                test = kotlinTemplate.testSource(spec, packageName)
            )
        }
        val app = appName(spec)
        val features = features(spec)
        return when (language) {
            ProjectLanguage.PYTHON -> python(packageName, app, features)
            ProjectLanguage.TYPESCRIPT -> typescript(app, features)
            ProjectLanguage.GO -> go(app, features)
            ProjectLanguage.RUST -> rust(app, features)
            ProjectLanguage.JAVA -> java(packageName, app, features)
            ProjectLanguage.RUBY -> ruby(packageName, app, features)
            ProjectLanguage.CSHARP -> csharp(app, features)
            ProjectLanguage.PHP -> php(packageName, app, features)
            ProjectLanguage.SWIFT -> swift(packageName, app, features)
            ProjectLanguage.CPP -> cpp(app, features)
            ProjectLanguage.KOTLIN -> error("handled above")
        }
    }

    /**
     * The command words a feature may become.
     *
     * A feature named `say "hi"` would have to be escaped ten different ways to
     * survive into ten languages, and one missed escape is a program that does
     * not compile. Dropping what does not fit is the only option that cannot
     * produce a broken file.
     */
    private fun features(spec: AppProjectSpec): List<String> =
        spec.intent.features
            .map { it.lowercase(Locale.US) }
            .filter { it !in RESERVED_COMMANDS }
            .filter { SAFE_TOKEN.matches(it) }
            .distinct()

    private fun appName(spec: AppProjectSpec): String =
        redactionFilter.redact(spec.intent.name)
            .lowercase(Locale.US)
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "app" }

    private fun usage(app: String, features: List<String>): String =
        "usage: $app [add <value>|list|feature <value>" +
            features.joinToString("") { "|$it <value>" } +
            "|--help]"

    private fun commandList(features: List<String>): List<String> = listOf("feature") + features

    private fun quoted(values: List<String>, quote: String = "\"") =
        values.joinToString(", ") { "$quote$it$quote" }

    // ---------------------------------------------------------------- Python

    private fun python(packageName: String, app: String, features: List<String>): Program {
        val featureAssertions = features.joinToString("\n\n") { feature ->
            "def test_${feature.replace('-', '_')}_is_its_own_command():\n" +
                "    assert run_app([\"$feature\", \"sample\"], []) == (0, \"$feature: sample\", \"\")"
        }
        val source = buildString {
            append("import sys\n\n")
            append("USAGE = \"${usage(app, features)}\"\n")
            append("FEATURES = [${quoted(commandList(features))}]\n\n\n")
            append("def run_app(args, state=None):\n")
            append("    \"\"\"Run one command. Returns (exit_code, output, error).\"\"\"\n")
            append("    if state is None:\n        state = []\n")
            append("    if list(args) == [\"--help\"]:\n        return (0, USAGE, \"\")\n")
            append("    if not args:\n        return (2, \"\", USAGE)\n")
            append("    command = args[0]\n")
            append("    value = \" \".join(args[1:]).strip()\n")
            append("    if command == \"add\":\n")
            append("        if not value:\n            return (2, \"\", \"usage: $app add <value>\")\n")
            append("        state.append(value)\n        return (0, \"added: \" + value, \"\")\n")
            append("    if command == \"list\":\n")
            append("        if not state:\n            return (0, \"no items\", \"\")\n")
            append("        return (0, \"\\n\".join(\"%d. %s\" % (i + 1, item) for i, item in enumerate(state)), \"\")\n")
            append("    if command in FEATURES:\n")
            append("        if not value:\n            return (2, \"\", \"usage: $app %s <value>\" % command)\n")
            append("        state.append(\"%s: %s\" % (command, value))\n")
            append("        return (0, \"%s: %s\" % (command, value), \"\")\n")
            append("    return (2, \"\", \"unknown command: %s\" % command)\n\n\n")
            append("def main(argv=None):\n")
            append("    exit_code, output, error = run_app(list(sys.argv[1:] if argv is None else argv))\n")
            append("    if output:\n        print(output)\n")
            append("    if error:\n        print(error, file=sys.stderr)\n")
            append("    if exit_code:\n        sys.exit(exit_code)\n\n\n")
            append("if __name__ == \"__main__\":\n    main()\n")
        }
        val test = buildString {
            append("from $packageName import run_app, USAGE\n\n\n")
            append("def test_help_prints_usage():\n")
            append("    assert run_app([\"--help\"]) == (0, USAGE, \"\")\n\n\n")
            append("def test_no_arguments_exits_nonzero():\n")
            append("    assert run_app([])[0] == 2\n\n\n")
            append("def test_add_then_list():\n")
            append("    state = []\n")
            append("    assert run_app([\"add\", \"first\", \"item\"], state) == (0, \"added: first item\", \"\")\n")
            append("    assert run_app([\"list\"], state) == (0, \"1. first item\", \"\")\n\n\n")
            append("def test_add_without_a_value_exits_nonzero():\n")
            append("    assert run_app([\"add\"], [])[0] == 2\n\n\n")
            if (featureAssertions.isNotEmpty()) append("$featureAssertions\n\n\n")
            append("def test_unknown_command_exits_nonzero():\n")
            append("    assert run_app([\"unknown\"], [])[0] == 2\n\n\n")
            // Runnable without pytest as well: a phone may not have it, and a
            // verification that cannot run is the failure this is fixing.
            append("if __name__ == \"__main__\":\n")
            append("    for name, case in sorted(list(globals().items())):\n")
            append("        if name.startswith(\"test_\"):\n            case()\n")
            append("    print(\"checks passed\")\n")
        }
        return Program(
            source = source,
            test = test,
            extraSources = mapOf("$packageName/__main__.py" to "from . import main\n\nmain()\n")
        )
    }

    // ------------------------------------------------------------ TypeScript

    private fun typescript(app: String, features: List<String>): Program {
        val featureTests = features.joinToString("\n\n") { feature ->
            "test(\"$feature is its own command\", () => {\n" +
                "  assert.equal(runApp([\"$feature\", \"sample\"], []).output, \"$feature: sample\");\n});"
        }
        val source = buildString {
            append("export interface CliResult {\n  exitCode: number;\n  output: string;\n  error: string;\n}\n\n")
            append("export const USAGE = \"${usage(app, features)}\";\n\n")
            append("const FEATURES = [${quoted(commandList(features))}];\n\n")
            append("export function runApp(args: string[], state: string[] = []): CliResult {\n")
            append("  if (args.length === 1 && args[0] === \"--help\") {\n")
            append("    return { exitCode: 0, output: USAGE, error: \"\" };\n  }\n")
            append("  if (args.length === 0) {\n    return { exitCode: 2, output: \"\", error: USAGE };\n  }\n")
            append("  const command = args[0];\n")
            append("  const value = args.slice(1).join(\" \").trim();\n")
            append("  if (command === \"add\") {\n")
            append("    if (value === \"\") {\n")
            append("      return { exitCode: 2, output: \"\", error: \"usage: $app add <value>\" };\n    }\n")
            append("    state.push(value);\n")
            append("    return { exitCode: 0, output: \"added: \" + value, error: \"\" };\n  }\n")
            append("  if (command === \"list\") {\n")
            append("    const output = state.length === 0\n      ? \"no items\"\n")
            append("      : state.map((item, index) => `\${index + 1}. \${item}`).join(\"\\n\");\n")
            append("    return { exitCode: 0, output, error: \"\" };\n  }\n")
            append("  if (FEATURES.includes(command)) {\n")
            append("    if (value === \"\") {\n")
            append("      return { exitCode: 2, output: \"\", error: `usage: $app \${command} <value>` };\n    }\n")
            append("    state.push(`\${command}: \${value}`);\n")
            append("    return { exitCode: 0, output: `\${command}: \${value}`, error: \"\" };\n  }\n")
            append("  return { exitCode: 2, output: \"\", error: `unknown command: \${command}` };\n}\n\n")
            append("export function main(argv: string[]): void {\n")
            append("  const result = runApp(argv);\n")
            append("  if (result.output !== \"\") console.log(result.output);\n")
            append("  if (result.error !== \"\") console.error(result.error);\n")
            append("  if (result.exitCode !== 0) process.exit(result.exitCode);\n}\n\n")
            append("if (process.argv[1]?.endsWith(\"index.ts\")) {\n  main(process.argv.slice(2));\n}\n")
        }
        val test = buildString {
            append("import { test } from \"node:test\";\n")
            append("import assert from \"node:assert/strict\";\n")
            append("import { runApp, USAGE } from \"./index.ts\";\n\n")
            append("test(\"help prints usage\", () => {\n")
            append("  assert.equal(runApp([\"--help\"]).exitCode, 0);\n")
            append("  assert.equal(runApp([\"--help\"]).output, USAGE);\n});\n\n")
            append("test(\"no arguments exits nonzero\", () => {\n")
            append("  assert.equal(runApp([]).exitCode, 2);\n});\n\n")
            append("test(\"add then list\", () => {\n  const state: string[] = [];\n")
            append("  assert.equal(runApp([\"add\", \"first\", \"item\"], state).output, \"added: first item\");\n")
            append("  assert.equal(runApp([\"list\"], state).output, \"1. first item\");\n});\n\n")
            append("test(\"add without a value exits nonzero\", () => {\n")
            append("  assert.equal(runApp([\"add\"], []).exitCode, 2);\n});\n\n")
            if (featureTests.isNotEmpty()) append("$featureTests\n\n")
            append("test(\"unknown command exits nonzero\", () => {\n")
            append("  assert.equal(runApp([\"unknown\"], []).exitCode, 2);\n});\n")
        }
        return Program(source = source, test = test)
    }

    // -------------------------------------------------------------------- Go

    private fun go(app: String, features: List<String>): Program {
        val featureTests = features.joinToString("\n\n") { feature ->
            val name = feature.split('-', '_').joinToString("") { part ->
                part.replaceFirstChar { it.titlecase(Locale.US) }
            }
            "func Test${name}IsItsOwnCommand(t *testing.T) {\n\tstate := []string{}\n" +
                "\tif got := RunApp([]string{\"$feature\", \"sample\"}, &state); got.Output != \"$feature: sample\" {\n" +
                "\t\tt.Fatalf(\"$feature: %+v\", got)\n\t}\n}"
        }
        val source = buildString {
            append("package main\n\n")
            append("import (\n\t\"fmt\"\n\t\"os\"\n\t\"strings\"\n)\n\n")
            append("// Usage is printed for --help and when no arguments are given.\n")
            append("const Usage = \"${usage(app, features)}\"\n\n")
            append("var features = []string{${quoted(commandList(features))}}\n\n")
            append("// CliResult is the outcome of one command: what to print and what to exit with.\n")
            append("type CliResult struct {\n\tExitCode int\n\tOutput   string\n\tError    string\n}\n\n")
            append("// RunApp runs one command against state and reports what should happen.\n")
            append("func RunApp(args []string, state *[]string) CliResult {\n")
            append("\tif len(args) == 1 && args[0] == \"--help\" {\n\t\treturn CliResult{0, Usage, \"\"}\n\t}\n")
            append("\tif len(args) == 0 {\n\t\treturn CliResult{2, \"\", Usage}\n\t}\n")
            append("\tcommand := args[0]\n")
            append("\tvalue := strings.TrimSpace(strings.Join(args[1:], \" \"))\n")
            append("\tif command == \"add\" {\n\t\tif value == \"\" {\n")
            append("\t\t\treturn CliResult{2, \"\", \"usage: $app add <value>\"}\n\t\t}\n")
            append("\t\t*state = append(*state, value)\n\t\treturn CliResult{0, \"added: \" + value, \"\"}\n\t}\n")
            append("\tif command == \"list\" {\n\t\tif len(*state) == 0 {\n")
            append("\t\t\treturn CliResult{0, \"no items\", \"\"}\n\t\t}\n")
            append("\t\tlines := make([]string, 0, len(*state))\n")
            append("\t\tfor index, item := range *state {\n")
            append("\t\t\tlines = append(lines, fmt.Sprintf(\"%d. %s\", index+1, item))\n\t\t}\n")
            append("\t\treturn CliResult{0, strings.Join(lines, \"\\n\"), \"\"}\n\t}\n")
            append("\tfor _, feature := range features {\n\t\tif command == feature {\n")
            append("\t\t\tif value == \"\" {\n")
            append("\t\t\t\treturn CliResult{2, \"\", \"usage: $app \" + command + \" <value>\"}\n\t\t\t}\n")
            append("\t\t\t*state = append(*state, command+\": \"+value)\n")
            append("\t\t\treturn CliResult{0, command + \": \" + value, \"\"}\n\t\t}\n\t}\n")
            append("\treturn CliResult{2, \"\", \"unknown command: \" + command}\n}\n\n")
            append("func main() {\n\tstate := []string{}\n")
            append("\tresult := RunApp(os.Args[1:], &state)\n")
            append("\tif result.Output != \"\" {\n\t\tfmt.Println(result.Output)\n\t}\n")
            append("\tif result.Error != \"\" {\n\t\tfmt.Fprintln(os.Stderr, result.Error)\n\t}\n")
            append("\tif result.ExitCode != 0 {\n\t\tos.Exit(result.ExitCode)\n\t}\n}\n")
        }
        val test = buildString {
            append("package main\n\nimport \"testing\"\n\n")
            append("func TestHelpPrintsUsage(t *testing.T) {\n\tstate := []string{}\n")
            append("\tif got := RunApp([]string{\"--help\"}, &state); got.ExitCode != 0 || got.Output != Usage {\n")
            append("\t\tt.Fatalf(\"help: %+v\", got)\n\t}\n}\n\n")
            append("func TestNoArgumentsExitsNonzero(t *testing.T) {\n\tstate := []string{}\n")
            append("\tif got := RunApp([]string{}, &state); got.ExitCode != 2 {\n")
            append("\t\tt.Fatalf(\"empty: %+v\", got)\n\t}\n}\n\n")
            append("func TestAddThenList(t *testing.T) {\n\tstate := []string{}\n")
            append("\tif got := RunApp([]string{\"add\", \"first\", \"item\"}, &state); got.Output != \"added: first item\" {\n")
            append("\t\tt.Fatalf(\"add: %+v\", got)\n\t}\n")
            append("\tif got := RunApp([]string{\"list\"}, &state); got.Output != \"1. first item\" {\n")
            append("\t\tt.Fatalf(\"list: %+v\", got)\n\t}\n}\n\n")
            append("func TestAddWithoutValueExitsNonzero(t *testing.T) {\n\tstate := []string{}\n")
            append("\tif got := RunApp([]string{\"add\"}, &state); got.ExitCode != 2 {\n")
            append("\t\tt.Fatalf(\"add: %+v\", got)\n\t}\n}\n\n")
            if (featureTests.isNotEmpty()) append("$featureTests\n\n")
            append("func TestUnknownCommandExitsNonzero(t *testing.T) {\n\tstate := []string{}\n")
            append("\tif got := RunApp([]string{\"unknown\"}, &state); got.ExitCode != 2 {\n")
            append("\t\tt.Fatalf(\"unknown: %+v\", got)\n\t}\n}\n")
        }
        return Program(source = source, test = test)
    }

    // ------------------------------------------------------------------ Rust

    private fun rust(app: String, features: List<String>): Program {
        val featureTests = features.joinToString("\n\n") { feature ->
            "    #[test]\n    fn ${feature.replace('-', '_')}_is_its_own_command() {\n" +
                "        let mut state = Vec::new();\n" +
                "        assert_eq!(run_app(&args(&[\"$feature\", \"sample\"]), &mut state).output, \"$feature: sample\");\n    }"
        }
        val source = buildString {
            append("use std::process;\n\n")
            append("/// Printed for `--help` and when no arguments are given.\n")
            append("pub const USAGE: &str = \"${usage(app, features)}\";\n\n")
            append("const FEATURES: &[&str] = &[${quoted(commandList(features))}];\n\n")
            append("/// The outcome of one command: what to print and what to exit with.\n")
            append("#[derive(Debug, PartialEq, Eq)]\npub struct CliResult {\n")
            append("    pub exit_code: i32,\n    pub output: String,\n    pub error: String,\n}\n\n")
            append("impl CliResult {\n    fn new(exit_code: i32, output: &str, error: &str) -> Self {\n")
            append("        CliResult { exit_code, output: output.to_string(), error: error.to_string() }\n    }\n}\n\n")
            append("/// Run one command against `state` and report what should happen.\n")
            append("pub fn run_app(args: &[String], state: &mut Vec<String>) -> CliResult {\n")
            append("    if args.len() == 1 && args[0] == \"--help\" {\n")
            append("        return CliResult::new(0, USAGE, \"\");\n    }\n")
            append("    if args.is_empty() {\n        return CliResult::new(2, \"\", USAGE);\n    }\n")
            append("    let command = args[0].as_str();\n")
            append("    let value = args[1..].join(\" \").trim().to_string();\n")
            append("    if command == \"add\" {\n        if value.is_empty() {\n")
            append("            return CliResult::new(2, \"\", \"usage: $app add <value>\");\n        }\n")
            append("        state.push(value.clone());\n")
            append("        return CliResult::new(0, &format!(\"added: {}\", value), \"\");\n    }\n")
            append("    if command == \"list\" {\n        if state.is_empty() {\n")
            append("            return CliResult::new(0, \"no items\", \"\");\n        }\n")
            append("        let lines: Vec<String> = state\n            .iter()\n            .enumerate()\n")
            append("            .map(|(index, item)| format!(\"{}. {}\", index + 1, item))\n            .collect();\n")
            append("        return CliResult::new(0, &lines.join(\"\\n\"), \"\");\n    }\n")
            append("    if FEATURES.contains(&command) {\n        if value.is_empty() {\n")
            append("            return CliResult::new(2, \"\", &format!(\"usage: $app {} <value>\", command));\n        }\n")
            append("        state.push(format!(\"{}: {}\", command, value));\n")
            append("        return CliResult::new(0, &format!(\"{}: {}\", command, value), \"\");\n    }\n")
            append("    CliResult::new(2, \"\", &format!(\"unknown command: {}\", command))\n}\n\n")
            append("fn main() {\n    let args: Vec<String> = std::env::args().skip(1).collect();\n")
            append("    let mut state: Vec<String> = Vec::new();\n")
            append("    let result = run_app(&args, &mut state);\n")
            append("    if !result.output.is_empty() {\n        println!(\"{}\", result.output);\n    }\n")
            append("    if !result.error.is_empty() {\n        eprintln!(\"{}\", result.error);\n    }\n")
            append("    if result.exit_code != 0 {\n        process::exit(result.exit_code);\n    }\n}\n\n")
            append("#[cfg(test)]\nmod tests;\n")
        }
        val test = buildString {
            append("use crate::*;\n\n")
            append("fn args(values: &[&str]) -> Vec<String> {\n")
            append("    values.iter().map(|value| value.to_string()).collect()\n}\n\n")
            append("#[test]\nfn help_prints_usage() {\n    let mut state = Vec::new();\n")
            append("    assert_eq!(run_app(&args(&[\"--help\"]), &mut state).output, USAGE);\n}\n\n")
            append("#[test]\nfn no_arguments_exits_nonzero() {\n    let mut state = Vec::new();\n")
            append("    assert_eq!(run_app(&args(&[]), &mut state).exit_code, 2);\n}\n\n")
            append("#[test]\nfn add_then_list() {\n    let mut state = Vec::new();\n")
            append("    assert_eq!(run_app(&args(&[\"add\", \"first\", \"item\"]), &mut state).output, \"added: first item\");\n")
            append("    assert_eq!(run_app(&args(&[\"list\"]), &mut state).output, \"1. first item\");\n}\n\n")
            append("#[test]\nfn add_without_a_value_exits_nonzero() {\n    let mut state = Vec::new();\n")
            append("    assert_eq!(run_app(&args(&[\"add\"]), &mut state).exit_code, 2);\n}\n\n")
            if (featureTests.isNotEmpty()) {
                append(featureTests.lines().joinToString("\n") { it.removePrefix("    ") })
                append("\n\n")
            }
            append("#[test]\nfn unknown_command_exits_nonzero() {\n    let mut state = Vec::new();\n")
            append("    assert_eq!(run_app(&args(&[\"unknown\"]), &mut state).exit_code, 2);\n}\n")
        }
        return Program(source = source, test = test)
    }

    // ------------------------------------------------------------------ Java

    private fun java(packageName: String, app: String, features: List<String>): Program {
        val featureChecks = features.joinToString("\n") { feature ->
            "        check(Main.runApp(List.of(\"$feature\", \"sample\"), new ArrayList<>()).output()" +
                ".equals(\"$feature: sample\"), \"$feature is its own command\");"
        }
        val source = buildString {
            append("package $packageName;\n\n")
            append("import java.util.ArrayList;\nimport java.util.Arrays;\nimport java.util.List;\n\n")
            append("/** The generated command-line application. */\npublic final class Main {\n\n")
            append("    /** Printed for --help and when no arguments are given. */\n")
            append("    public static final String USAGE = \"${usage(app, features)}\";\n\n")
            append("    private static final List<String> FEATURES = List.of(${quoted(commandList(features))});\n\n")
            append("    private Main() {\n    }\n\n")
            append("    /** The outcome of one command: what to print and what to exit with. */\n")
            append("    public record CliResult(int exitCode, String output, String error) {\n    }\n\n")
            append("    /** Run one command against state and report what should happen. */\n")
            append("    public static CliResult runApp(List<String> args, List<String> state) {\n")
            append("        if (args.size() == 1 && args.get(0).equals(\"--help\")) {\n")
            append("            return new CliResult(0, USAGE, \"\");\n        }\n")
            append("        if (args.isEmpty()) {\n            return new CliResult(2, \"\", USAGE);\n        }\n")
            append("        String command = args.get(0);\n")
            append("        String value = String.join(\" \", args.subList(1, args.size())).trim();\n")
            append("        if (command.equals(\"add\")) {\n            if (value.isEmpty()) {\n")
            append("                return new CliResult(2, \"\", \"usage: $app add <value>\");\n            }\n")
            append("            state.add(value);\n")
            append("            return new CliResult(0, \"added: \" + value, \"\");\n        }\n")
            append("        if (command.equals(\"list\")) {\n            if (state.isEmpty()) {\n")
            append("                return new CliResult(0, \"no items\", \"\");\n            }\n")
            append("            StringBuilder lines = new StringBuilder();\n")
            append("            for (int index = 0; index < state.size(); index++) {\n")
            append("                if (index > 0) {\n                    lines.append(\"\\n\");\n                }\n")
            append("                lines.append(index + 1).append(\". \").append(state.get(index));\n            }\n")
            append("            return new CliResult(0, lines.toString(), \"\");\n        }\n")
            append("        if (FEATURES.contains(command)) {\n            if (value.isEmpty()) {\n")
            append("                return new CliResult(2, \"\", \"usage: $app \" + command + \" <value>\");\n            }\n")
            append("            state.add(command + \": \" + value);\n")
            append("            return new CliResult(0, command + \": \" + value, \"\");\n        }\n")
            append("        return new CliResult(2, \"\", \"unknown command: \" + command);\n    }\n\n")
            append("    public static void main(String[] args) {\n")
            append("        CliResult result = runApp(Arrays.asList(args), new ArrayList<>());\n")
            append("        if (!result.output().isEmpty()) {\n            System.out.println(result.output());\n        }\n")
            append("        if (!result.error().isEmpty()) {\n            System.err.println(result.error());\n        }\n")
            append("        if (result.exitCode() != 0) {\n            System.exit(result.exitCode());\n        }\n    }\n}\n")
        }
        val test = buildString {
            append("package $packageName;\n\n")
            append("import java.util.ArrayList;\nimport java.util.List;\n\n")
            append("/** Executable assertions for {@link Main}, runnable without a test framework. */\n")
            append("public final class MainTest {\n\n")
            append("    private MainTest() {\n    }\n\n")
            append("    private static void check(boolean condition, String what) {\n")
            append("        if (!condition) {\n            throw new AssertionError(what);\n        }\n    }\n\n")
            append("    public static void main(String[] args) {\n")
            append("        List<String> state = new ArrayList<>();\n")
            append("        check(Main.runApp(List.of(\"--help\"), state).exitCode() == 0, \"help exits zero\");\n")
            append("        check(Main.runApp(List.of(), state).exitCode() == 2, \"no arguments exits nonzero\");\n")
            append("        check(Main.runApp(List.of(\"add\", \"first\", \"item\"), state).output()")
            append(".equals(\"added: first item\"), \"add reports the value\");\n")
            append("        check(Main.runApp(List.of(\"list\"), state).output()")
            append(".equals(\"1. first item\"), \"list numbers the items\");\n")
            append("        check(Main.runApp(List.of(\"add\"), new ArrayList<>()).exitCode() == 2,")
            append(" \"add without a value exits nonzero\");\n")
            if (featureChecks.isNotEmpty()) append("$featureChecks\n")
            append("        check(Main.runApp(List.of(\"unknown\"), new ArrayList<>()).exitCode() == 2,")
            append(" \"unknown command exits nonzero\");\n")
            append("        System.out.println(\"checks passed\");\n    }\n}\n")
        }
        return Program(source = source, test = test)
    }

    // ------------------------------------------------------------------ Ruby

    private fun ruby(packageName: String, app: String, features: List<String>): Program {
        val moduleName = packageName.split('-', '_')
            .joinToString("") { part -> part.replaceFirstChar { it.titlecase(Locale.US) } }
            .ifBlank { "App" }
        val featureTests = features.joinToString("\n\n") { feature ->
            "  def test_${feature.replace('-', '_')}_is_its_own_command\n" +
                "    assert_equal '$feature: sample', $moduleName.run_app(['$feature', 'sample'], []).output\n  end"
        }
        val source = buildString {
            append("# frozen_string_literal: true\n\n")
            append("# The generated command-line application.\n")
            append("module $moduleName\n")
            append("  USAGE = '${usage(app, features)}'\n")
            append("  FEATURES = [${quoted(commandList(features), "'")}].freeze\n\n")
            append("  CliResult = Struct.new(:exit_code, :output, :error)\n\n")
            append("  # Run one command against +state+ and report what should happen.\n")
            append("  def self.run_app(args, state = [])\n")
            append("    return CliResult.new(0, USAGE, '') if args == ['--help']\n")
            append("    return CliResult.new(2, '', USAGE) if args.empty?\n\n")
            append("    command = args.first\n")
            append("    value = args.drop(1).join(' ').strip\n")
            append("    case command\n")
            append("    when 'add'\n")
            append("      return CliResult.new(2, '', 'usage: $app add <value>') if value.empty?\n\n")
            append("      state << value\n")
            append("      CliResult.new(0, \"added: #{value}\", '')\n")
            append("    when 'list'\n")
            append("      return CliResult.new(0, 'no items', '') if state.empty?\n\n")
            append("      CliResult.new(0, state.each_with_index.map { |item, index| \"#{index + 1}. #{item}\" }.join(\"\\n\"), '')\n")
            append("    when *FEATURES\n")
            append("      return CliResult.new(2, '', \"usage: $app #{command} <value>\") if value.empty?\n\n")
            append("      state << \"#{command}: #{value}\"\n")
            append("      CliResult.new(0, \"#{command}: #{value}\", '')\n")
            append("    else\n")
            append("      CliResult.new(2, '', \"unknown command: #{command}\")\n")
            append("    end\n  end\n\n")
            append("  def self.main(argv)\n")
            append("    result = run_app(argv)\n")
            append("    puts result.output unless result.output.empty?\n")
            append("    warn result.error unless result.error.empty?\n")
            append("    exit(result.exit_code) unless result.exit_code.zero?\n")
            append("  end\nend\n\n")
            append("$moduleName.main(ARGV) if \$PROGRAM_NAME == __FILE__\n")
        }
        val test = buildString {
            append("# frozen_string_literal: true\n\n")
            append("require 'minitest/autorun'\n")
            append("require_relative '../lib/$packageName'\n\n")
            append("class Test$moduleName < Minitest::Test\n")
            append("  def test_help_prints_usage\n")
            append("    assert_equal 0, $moduleName.run_app(['--help']).exit_code\n  end\n\n")
            append("  def test_no_arguments_exits_nonzero\n")
            append("    assert_equal 2, $moduleName.run_app([]).exit_code\n  end\n\n")
            append("  def test_add_then_list\n    state = []\n")
            append("    assert_equal 'added: first item', $moduleName.run_app(['add', 'first', 'item'], state).output\n")
            append("    assert_equal '1. first item', $moduleName.run_app(['list'], state).output\n  end\n\n")
            append("  def test_add_without_a_value_exits_nonzero\n")
            append("    assert_equal 2, $moduleName.run_app(['add'], []).exit_code\n  end\n\n")
            if (featureTests.isNotEmpty()) append("$featureTests\n\n")
            append("  def test_unknown_command_exits_nonzero\n")
            append("    assert_equal 2, $moduleName.run_app(['unknown'], []).exit_code\n  end\nend\n")
        }
        return Program(source = source, test = test)
    }

    // ---------------------------------------------------------------- C sharp

    private fun csharp(app: String, features: List<String>): Program {
        val featureChecks = features.joinToString("\n") { feature ->
            "        Check(Program.RunApp(new List<string> { \"$feature\", \"sample\" }, new List<string>())" +
                ".Output == \"$feature: sample\", \"$feature is its own command\");"
        }
        val source = buildString {
            append("using System;\nusing System.Collections.Generic;\nusing System.Linq;\n\n")
            append("/// <summary>The generated command-line application.</summary>\n")
            append("public static class Program\n{\n")
            append("    /// <summary>Printed for --help and when no arguments are given.</summary>\n")
            append("    public const string Usage = \"${usage(app, features)}\";\n\n")
            append("    private static readonly string[] Features = { ${quoted(commandList(features))} };\n\n")
            append("    /// <summary>The outcome of one command: what to print and what to exit with.</summary>\n")
            append("    public readonly record struct CliResult(int ExitCode, string Output, string Error);\n\n")
            append("    /// <summary>Run one command against state and report what should happen.</summary>\n")
            append("    public static CliResult RunApp(List<string> args, List<string> state)\n    {\n")
            append("        if (args.Count == 1 && args[0] == \"--help\")\n        {\n")
            append("            return new CliResult(0, Usage, \"\");\n        }\n\n")
            append("        if (args.Count == 0)\n        {\n")
            append("            return new CliResult(2, \"\", Usage);\n        }\n\n")
            append("        var command = args[0];\n")
            append("        var value = string.Join(\" \", args.Skip(1)).Trim();\n")
            append("        if (command == \"add\")\n        {\n")
            append("            if (value.Length == 0)\n            {\n")
            append("                return new CliResult(2, \"\", \"usage: $app add <value>\");\n            }\n\n")
            append("            state.Add(value);\n")
            append("            return new CliResult(0, \"added: \" + value, \"\");\n        }\n\n")
            append("        if (command == \"list\")\n        {\n")
            append("            if (state.Count == 0)\n            {\n")
            append("                return new CliResult(0, \"no items\", \"\");\n            }\n\n")
            append("            var lines = state.Select((item, index) => \$\"{index + 1}. {item}\");\n")
            append("            return new CliResult(0, string.Join(\"\\n\", lines), \"\");\n        }\n\n")
            append("        if (Array.IndexOf(Features, command) >= 0)\n        {\n")
            append("            if (value.Length == 0)\n            {\n")
            append("                return new CliResult(2, \"\", \$\"usage: $app {command} <value>\");\n            }\n\n")
            append("            state.Add(\$\"{command}: {value}\");\n")
            append("            return new CliResult(0, \$\"{command}: {value}\", \"\");\n        }\n\n")
            append("        return new CliResult(2, \"\", \$\"unknown command: {command}\");\n    }\n\n")
            append("    public static int Main(string[] args)\n    {\n")
            append("        var result = RunApp(new List<string>(args), new List<string>());\n")
            append("        if (result.Output.Length > 0)\n        {\n")
            append("            Console.WriteLine(result.Output);\n        }\n\n")
            append("        if (result.Error.Length > 0)\n        {\n")
            append("            Console.Error.WriteLine(result.Error);\n        }\n\n")
            append("        return result.ExitCode;\n    }\n}\n")
        }
        val test = buildString {
            append("using System;\nusing System.Collections.Generic;\n\n")
            append("/// <summary>Executable assertions for Program, runnable without a test framework.</summary>\n")
            append("public static class ProgramTests\n{\n")
            append("    private static void Check(bool condition, string what)\n    {\n")
            append("        if (!condition)\n        {\n")
            append("            throw new Exception(what);\n        }\n    }\n\n")
            append("    public static int Main()\n    {\n")
            append("        var state = new List<string>();\n")
            append("        Check(Program.RunApp(new List<string> { \"--help\" }, state).ExitCode == 0, \"help exits zero\");\n")
            append("        Check(Program.RunApp(new List<string>(), state).ExitCode == 2, \"no arguments exits nonzero\");\n")
            append("        Check(Program.RunApp(new List<string> { \"add\", \"first\", \"item\" }, state).Output")
            append(" == \"added: first item\", \"add reports the value\");\n")
            append("        Check(Program.RunApp(new List<string> { \"list\" }, state).Output")
            append(" == \"1. first item\", \"list numbers the items\");\n")
            append("        Check(Program.RunApp(new List<string> { \"add\" }, new List<string>()).ExitCode == 2,")
            append(" \"add without a value exits nonzero\");\n")
            if (featureChecks.isNotEmpty()) append("$featureChecks\n")
            append("        Check(Program.RunApp(new List<string> { \"unknown\" }, new List<string>()).ExitCode == 2,")
            append(" \"unknown command exits nonzero\");\n")
            append("        Console.WriteLine(\"checks passed\");\n        return 0;\n    }\n}\n")
        }
        return Program(source = source, test = test)
    }

    // ------------------------------------------------------------------- PHP

    private fun php(packageName: String, app: String, features: List<String>): Program {
        val featureChecks = features.joinToString("\n") { feature ->
            "check(run_app(['$feature', 'sample'], \$scratch)->output === '$feature: sample'," +
                " '$feature is its own command');"
        }
        val source = buildString {
            append("<?php\n\n")
            append("declare(strict_types=1);\n\n")
            append("const USAGE = '${usage(app, features)}';\n")
            append("const FEATURES = [${quoted(commandList(features), "'")}];\n\n")
            append("/** The outcome of one command: what to print and what to exit with. */\n")
            append("final class CliResult\n{\n")
            append("    public function __construct(\n")
            append("        public readonly int \$exitCode,\n")
            append("        public readonly string \$output = '',\n")
            append("        public readonly string \$error = ''\n")
            append("    ) {\n    }\n}\n\n")
            append("/** Run one command against \$state and report what should happen. */\n")
            append("function run_app(array \$args, array &\$state): CliResult\n{\n")
            append("    if (\$args === ['--help']) {\n        return new CliResult(0, USAGE);\n    }\n")
            append("    if (\$args === []) {\n        return new CliResult(2, '', USAGE);\n    }\n")
            append("    \$command = \$args[0];\n")
            append("    \$value = trim(implode(' ', array_slice(\$args, 1)));\n")
            append("    if (\$command === 'add') {\n        if (\$value === '') {\n")
            append("            return new CliResult(2, '', 'usage: $app add <value>');\n        }\n")
            append("        \$state[] = \$value;\n")
            append("        return new CliResult(0, 'added: ' . \$value);\n    }\n")
            append("    if (\$command === 'list') {\n        if (\$state === []) {\n")
            append("            return new CliResult(0, 'no items');\n        }\n")
            append("        \$lines = [];\n")
            append("        foreach (\$state as \$index => \$item) {\n")
            append("            \$lines[] = (\$index + 1) . '. ' . \$item;\n        }\n")
            append("        return new CliResult(0, implode(\"\\n\", \$lines));\n    }\n")
            append("    if (in_array(\$command, FEATURES, true)) {\n        if (\$value === '') {\n")
            append("            return new CliResult(2, '', 'usage: $app ' . \$command . ' <value>');\n        }\n")
            append("        \$state[] = \$command . ': ' . \$value;\n")
            append("        return new CliResult(0, \$command . ': ' . \$value);\n    }\n")
            append("    return new CliResult(2, '', 'unknown command: ' . \$command);\n}\n\n")
            append("function main(array \$argv): void\n{\n")
            append("    \$state = [];\n")
            append("    \$result = run_app(array_slice(\$argv, 1), \$state);\n")
            append("    if (\$result->output !== '') {\n        fwrite(STDOUT, \$result->output . \"\\n\");\n    }\n")
            append("    if (\$result->error !== '') {\n        fwrite(STDERR, \$result->error . \"\\n\");\n    }\n")
            append("    if (\$result->exitCode !== 0) {\n        exit(\$result->exitCode);\n    }\n}\n\n")
            append("if (PHP_SAPI === 'cli' && isset(\$argv) && realpath(\$argv[0]) === __FILE__) {\n")
            append("    main(\$argv);\n}\n")
        }
        val test = buildString {
            append("<?php\n\n")
            append("declare(strict_types=1);\n\n")
            append("require_once __DIR__ . '/../src/$packageName.php';\n\n")
            append("\$failures = 0;\n\n")
            append("function check(bool \$condition, string \$what): void\n{\n")
            append("    global \$failures;\n")
            append("    if (!\$condition) {\n        fwrite(STDERR, \"FAIL: \$what\\n\");\n        \$failures++;\n    }\n}\n\n")
            append("\$state = [];\n\$scratch = [];\n")
            append("check(run_app(['--help'], \$state)->exitCode === 0, 'help exits zero');\n")
            append("check(run_app([], \$state)->exitCode === 2, 'no arguments exits nonzero');\n")
            append("check(run_app(['add', 'first', 'item'], \$state)->output === 'added: first item', 'add reports the value');\n")
            append("check(run_app(['list'], \$state)->output === '1. first item', 'list numbers the items');\n")
            append("check(run_app(['add'], \$scratch)->exitCode === 2, 'add without a value exits nonzero');\n")
            if (featureChecks.isNotEmpty()) append("$featureChecks\n")
            append("check(run_app(['unknown'], \$scratch)->exitCode === 2, 'unknown command exits nonzero');\n\n")
            append("if (\$failures > 0) {\n    exit(1);\n}\n\n")
            append("fwrite(STDOUT, \"checks passed\\n\");\n")
        }
        return Program(source = source, test = test)
    }

    // ----------------------------------------------------------------- Swift

    private fun swift(packageName: String, app: String, features: List<String>): Program {
        val featureTests = features.joinToString("\n\n") { feature ->
            "    func test${feature.split('-', '_').joinToString("") { part ->
                part.replaceFirstChar { it.titlecase(Locale.US) }
            }}IsItsOwnCommand() {\n" +
                "        var state: [String] = []\n" +
                "        XCTAssertEqual(runApp([\"$feature\", \"sample\"], &state).output, \"$feature: sample\")\n    }"
        }
        val source = buildString {
            append("import Foundation\n\n")
            append("/// Printed for `--help` and when no arguments are given.\n")
            append("public let usage = \"${usage(app, features)}\"\n\n")
            append("let features = [${quoted(commandList(features))}]\n\n")
            append("/// The outcome of one command: what to print and what to exit with.\n")
            append("public struct CliResult: Equatable {\n")
            append("    public let exitCode: Int32\n    public let output: String\n    public let error: String\n\n")
            append("    public init(_ exitCode: Int32, _ output: String = \"\", _ error: String = \"\") {\n")
            append("        self.exitCode = exitCode\n        self.output = output\n        self.error = error\n    }\n}\n\n")
            append("/// Run one command against `state` and report what should happen.\n")
            append("public func runApp(_ args: [String], _ state: inout [String]) -> CliResult {\n")
            append("    if args == [\"--help\"] {\n        return CliResult(0, usage, \"\")\n    }\n")
            append("    if args.isEmpty {\n        return CliResult(2, \"\", usage)\n    }\n")
            append("    let command = args[0]\n")
            append("    let value = args.dropFirst().joined(separator: \" \").trimmingCharacters(in: .whitespaces)\n")
            append("    if command == \"add\" {\n        if value.isEmpty {\n")
            append("            return CliResult(2, \"\", \"usage: $app add <value>\")\n        }\n")
            append("        state.append(value)\n        return CliResult(0, \"added: \\(value)\", \"\")\n    }\n")
            append("    if command == \"list\" {\n        if state.isEmpty {\n")
            append("            return CliResult(0, \"no items\", \"\")\n        }\n")
            append("        let lines = state.enumerated().map { \"\\(\$0.offset + 1). \\(\$0.element)\" }\n")
            append("        return CliResult(0, lines.joined(separator: \"\\n\"), \"\")\n    }\n")
            append("    if features.contains(command) {\n        if value.isEmpty {\n")
            append("            return CliResult(2, \"\", \"usage: $app \\(command) <value>\")\n        }\n")
            append("        state.append(\"\\(command): \\(value)\")\n")
            append("        return CliResult(0, \"\\(command): \\(value)\", \"\")\n    }\n")
            append("    return CliResult(2, \"\", \"unknown command: \\(command)\")\n}\n\n")
            append("public func main(_ argv: [String]) {\n")
            append("    var state: [String] = []\n")
            append("    let result = runApp(argv, &state)\n")
            append("    if !result.output.isEmpty {\n        print(result.output)\n    }\n")
            append("    if !result.error.isEmpty {\n        FileHandle.standardError.write((result.error + \"\\n\").data(using: .utf8)!)\n    }\n")
            append("    if result.exitCode != 0 {\n        exit(result.exitCode)\n    }\n}\n")
        }
        val test = buildString {
            append("import XCTest\n@testable import $packageName\n\n")
            append("final class ${packageName}Tests: XCTestCase {\n")
            append("    func testHelpPrintsUsage() {\n        var state: [String] = []\n")
            append("        XCTAssertEqual(runApp([\"--help\"], &state).output, usage)\n    }\n\n")
            append("    func testNoArgumentsExitsNonzero() {\n        var state: [String] = []\n")
            append("        XCTAssertEqual(runApp([], &state).exitCode, 2)\n    }\n\n")
            append("    func testAddThenList() {\n        var state: [String] = []\n")
            append("        XCTAssertEqual(runApp([\"add\", \"first\", \"item\"], &state).output, \"added: first item\")\n")
            append("        XCTAssertEqual(runApp([\"list\"], &state).output, \"1. first item\")\n    }\n\n")
            append("    func testAddWithoutAValueExitsNonzero() {\n        var state: [String] = []\n")
            append("        XCTAssertEqual(runApp([\"add\"], &state).exitCode, 2)\n    }\n\n")
            if (featureTests.isNotEmpty()) append("$featureTests\n\n")
            append("    func testUnknownCommandExitsNonzero() {\n        var state: [String] = []\n")
            append("        XCTAssertEqual(runApp([\"unknown\"], &state).exitCode, 2)\n    }\n}\n")
        }
        return Program(
            source = source,
            test = test,
            extraSources = mapOf(
                "Sources/${packageName}Cli/main.swift" to
                    "import $packageName\n\nmain(Array(CommandLine.arguments.dropFirst()))\n"
            )
        )
    }

    // ------------------------------------------------------------------- C++

    private fun cpp(app: String, features: List<String>): Program {
        val featureChecks = features.joinToString("\n") { feature ->
            "    check(run_app({\"$feature\", \"sample\"}, scratch).output == \"$feature: sample\"," +
                " \"$feature is its own command\");"
        }
        val source = buildString {
            append("#include \"app.hpp\"\n\n")
            append("#include <iostream>\n#include <sstream>\n\n")
            append("const char* const USAGE = \"${usage(app, features)}\";\n\n")
            append("namespace {\n\n")
            append("const std::vector<std::string> FEATURES = {${quoted(commandList(features))}};\n\n")
            append("std::string trim(const std::string& value) {\n")
            append("    const auto begin = value.find_first_not_of(\" \\t\\n\");\n")
            append("    if (begin == std::string::npos) {\n        return \"\";\n    }\n")
            append("    const auto end = value.find_last_not_of(\" \\t\\n\");\n")
            append("    return value.substr(begin, end - begin + 1);\n}\n\n")
            append("}  // namespace\n\n")
            append("CliResult run_app(const std::vector<std::string>& args, std::vector<std::string>& state) {\n")
            append("    if (args.size() == 1 && args[0] == \"--help\") {\n")
            append("        return CliResult{0, USAGE, \"\"};\n    }\n")
            append("    if (args.empty()) {\n        return CliResult{2, \"\", USAGE};\n    }\n")
            append("    const std::string command = args[0];\n")
            append("    std::ostringstream joined;\n")
            append("    for (std::size_t index = 1; index < args.size(); ++index) {\n")
            append("        if (index > 1) {\n            joined << \" \";\n        }\n")
            append("        joined << args[index];\n    }\n")
            append("    const std::string value = trim(joined.str());\n")
            append("    if (command == \"add\") {\n        if (value.empty()) {\n")
            append("            return CliResult{2, \"\", \"usage: $app add <value>\"};\n        }\n")
            append("        state.push_back(value);\n")
            append("        return CliResult{0, \"added: \" + value, \"\"};\n    }\n")
            append("    if (command == \"list\") {\n        if (state.empty()) {\n")
            append("            return CliResult{0, \"no items\", \"\"};\n        }\n")
            append("        std::ostringstream lines;\n")
            append("        for (std::size_t index = 0; index < state.size(); ++index) {\n")
            append("            if (index > 0) {\n                lines << \"\\n\";\n            }\n")
            append("            lines << (index + 1) << \". \" << state[index];\n        }\n")
            append("        return CliResult{0, lines.str(), \"\"};\n    }\n")
            append("    for (const auto& feature : FEATURES) {\n        if (command == feature) {\n")
            append("            if (value.empty()) {\n")
            append("                return CliResult{2, \"\", \"usage: $app \" + command + \" <value>\"};\n            }\n")
            append("            state.push_back(command + \": \" + value);\n")
            append("            return CliResult{0, command + \": \" + value, \"\"};\n        }\n    }\n")
            append("    return CliResult{2, \"\", \"unknown command: \" + command};\n}\n\n")
            append("#ifndef APP_FACTORY_NO_MAIN\n")
            append("int main(int argc, char** argv) {\n")
            append("    const std::vector<std::string> args(argv + 1, argv + argc);\n")
            append("    std::vector<std::string> state;\n")
            append("    const CliResult result = run_app(args, state);\n")
            append("    if (!result.output.empty()) {\n        std::cout << result.output << std::endl;\n    }\n")
            append("    if (!result.error.empty()) {\n        std::cerr << result.error << std::endl;\n    }\n")
            append("    return result.exit_code;\n}\n#endif\n")
        }
        val test = buildString {
            append("#include \"../src/app.hpp\"\n\n")
            append("#include <iostream>\n#include <string>\n#include <vector>\n\n")
            append("namespace {\n\nint failures = 0;\n\n")
            append("void check(bool condition, const std::string& what) {\n")
            append("    if (!condition) {\n        std::cerr << \"FAIL: \" << what << std::endl;\n        ++failures;\n    }\n}\n\n")
            append("}  // namespace\n\n")
            append("int main() {\n")
            append("    std::vector<std::string> state;\n    std::vector<std::string> scratch;\n")
            append("    check(run_app({\"--help\"}, state).exit_code == 0, \"help exits zero\");\n")
            append("    check(run_app({}, state).exit_code == 2, \"no arguments exits nonzero\");\n")
            append("    check(run_app({\"add\", \"first\", \"item\"}, state).output == \"added: first item\", \"add reports the value\");\n")
            append("    check(run_app({\"list\"}, state).output == \"1. first item\", \"list numbers the items\");\n")
            append("    check(run_app({\"add\"}, scratch).exit_code == 2, \"add without a value exits nonzero\");\n")
            if (featureChecks.isNotEmpty()) append("$featureChecks\n")
            append("    check(run_app({\"unknown\"}, scratch).exit_code == 2, \"unknown command exits nonzero\");\n")
            append("    if (failures > 0) {\n        return 1;\n    }\n")
            append("    std::cout << \"checks passed\" << std::endl;\n    return 0;\n}\n")
        }
        return Program(
            source = source,
            test = test,
            extraSources = mapOf(
                "src/app.hpp" to buildString {
                    append("#ifndef APP_FACTORY_APP_HPP\n#define APP_FACTORY_APP_HPP\n\n")
                    append("#include <string>\n#include <vector>\n\n")
                    append("/** The outcome of one command: what to print and what to exit with. */\n")
                    append("struct CliResult {\n    int exit_code;\n    std::string output;\n    std::string error;\n};\n\n")
                    append("/** Run one command against state and report what should happen. */\n")
                    append("CliResult run_app(const std::vector<std::string>& args, std::vector<std::string>& state);\n\n")
                    append("#endif\n")
                }
            )
        )
    }

    private companion object {
        val RESERVED_COMMANDS = setOf("add", "list", "feature", "help")
        val SAFE_TOKEN = Regex("[a-z][a-z0-9_-]{0,31}")
    }
}
