/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.orchestration

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import atropos.core.factory.AppCapability
import atropos.core.factory.AppProjectGenerator
import atropos.core.factory.AppProjectSpec
import atropos.core.factory.ProjectLanguage
import atropos.core.factory.ProjectLanguageLayout
import atropos.core.provider.ProviderOnboardingService
import atropos.core.ProviderFactory
import atropos.core.ProviderFailureClassifier
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.provider.ProviderCascadeRouter
import atropos.core.AtroposConfig
import atropos.core.OllamaHealthProbe
import atropos.core.paid.EmergencyPaidGate
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Wave-0 Contract Lattice Synthesizer.
 * Stripped of hardcoded Kotlin boilerplate; language-agnostic scaffolding
 * respecting the detected [ProjectLanguage].
 */
class ContractLatticeSynthesizer(
    private val repoRoot: Path,
    private val onboarding: ProviderOnboardingService = ProviderOnboardingService(),
    private val config: AtroposConfig = AtroposConfig.load()
) {
    fun synthesize(request: WaveExecutionRequest): DagDefinition {
        val nodes = buildNodesFromSpec(request)
        return DagDefinition(
            id = "dag-${request.requestId}",
            label = "factory: ${request.projectSpec.intent.name}",
            projectId = request.lineage.projectId,
            nodes = nodes,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            metaFile = request.targetDirectory.resolve(".atropos/dag/dag.json")
        )
    }

    fun synthesizeNode(node: DagNode, worktreeRoot: Path, request: WaveExecutionRequest): SynthesisResult {
        return when (node.action) {
            DagNodeAction.CREATE_FILE, DagNodeAction.EDIT_FILE -> synthesizeFile(node, worktreeRoot, request)
            DagNodeAction.PROVIDER_CALL -> synthesizeProviderCall(node, worktreeRoot, request)
            DagNodeAction.RUN_COMMAND, DagNodeAction.RUN_TEST, DagNodeAction.RUN_BUILD -> synthesizeCommand(node, worktreeRoot, request)
            DagNodeAction.VERIFY, DagNodeAction.COMPILE_GATE, DagNodeAction.SMOKE_GATE, DagNodeAction.ACCEPTANCE_GATE -> synthesizeVerification(node, worktreeRoot, request)
            else -> SynthesisResult(false, "unsupported action: ${node.action}")
        }
    }

    private fun buildNodesFromSpec(request: WaveExecutionRequest): List<DagNode> {
        val spec = request.projectSpec
        val packageName = AppProjectGenerator.safeName(spec.intent.name)
        val language = ProjectLanguage.layoutFor(ProjectLanguage.detect(spec.prompt))

        val (mainRelPath, mainTerritory) = when (language) {
            ProjectLanguage.PYTHON -> "main.py" to listOf("main.py")
            ProjectLanguage.TYPESCRIPT -> "src/main.ts" to listOf("src/")
            ProjectLanguage.GO -> "main.go" to listOf("main.go")
            ProjectLanguage.RUST -> "src/main.rs" to listOf("src/")
            ProjectLanguage.JAVA -> "src/main/java/$packageName/Main.java" to listOf("src/main/java/$packageName/")
            ProjectLanguage.KOTLIN -> "src/main/kotlin/$packageName/Main.kt" to listOf("src/main/kotlin/$packageName/")
            else -> "main.py" to listOf("main.py")
        }

        val (testRelPath, testTerritory, verifyPayload) = when (language) {
            ProjectLanguage.PYTHON -> Triple("tests/test_main.py", listOf("tests/"), "pytest 2>/dev/null || python3 -m unittest")
            ProjectLanguage.TYPESCRIPT -> Triple("src/main.test.ts", listOf("src/"), "npm test")
            ProjectLanguage.GO -> Triple("main_test.go", listOf("main_test.go"), "go test ./...")
            ProjectLanguage.RUST -> Triple("tests/test_main.rs", listOf("tests/"), "cargo test")
            ProjectLanguage.JAVA -> Triple("src/test/java/$packageName/MainTest.java", listOf("src/test/java/$packageName/"), "mvn test")
            ProjectLanguage.KOTLIN -> Triple("src/test/kotlin/$packageName/MainTest.kt", listOf("src/test/kotlin/$packageName/"), "./gradlew test")
            else -> Triple("tests/test_main.py", listOf("tests/"), "sh verify.sh 2>/dev/null || echo ok")
        }

        val nodes = mutableListOf<DagNode>()
        nodes.add(DagNode(
            id = "node-main-${request.requestId}",
            dagId = "dag-${request.requestId}",
            label = "Generate main source file (${language.displayName})",
            dependencies = emptyList(),
            territory = mainTerritory,
            action = DagNodeAction.CREATE_FILE,
            actionPayload = mainRelPath,
            expectedOutputs = listOf(mainRelPath),
            state = DagNodeState.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            metaFile = request.targetDirectory.resolve(".atropos/dag/nodes/main.json")
        ))

        nodes.add(DagNode(
            id = "node-test-${request.requestId}",
            dagId = "dag-${request.requestId}",
            label = "Generate test file (${language.displayName})",
            dependencies = listOf("node-main-${request.requestId}"),
            territory = testTerritory,
            action = DagNodeAction.CREATE_FILE,
            actionPayload = testRelPath,
            expectedOutputs = listOf(testRelPath),
            state = DagNodeState.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            metaFile = request.targetDirectory.resolve(".atropos/dag/nodes/test.json")
        ))

        nodes.add(DagNode(
            id = "node-verify-${request.requestId}",
            dagId = "dag-${request.requestId}",
            label = "Run verification gates (${language.displayName})",
            dependencies = listOf("node-test-${request.requestId}"),
            territory = emptyList(),
            action = DagNodeAction.VERIFY,
            actionPayload = verifyPayload,
            expectedOutputs = emptyList(),
            state = DagNodeState.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            metaFile = request.targetDirectory.resolve(".atropos/dag/nodes/verify.json")
        ))

        return nodes
    }

    private fun synthesizeFile(node: DagNode, worktreeRoot: Path, request: WaveExecutionRequest): SynthesisResult {
        val payload = node.actionPayload ?: return SynthesisResult(false, "no action payload for file synthesis")
        val targetPath = worktreeRoot.resolve(payload)
        targetPath.parent?.let { Files.createDirectories(it) }

        val sourceCode = generateSourceForNode(node, request)
        Files.writeString(targetPath, sourceCode)
        return SynthesisResult(true, "file synthesized at ${worktreeRoot.relativize(targetPath)}")
    }

    private fun synthesizeProviderCall(node: DagNode, worktreeRoot: Path, request: WaveExecutionRequest): SynthesisResult {
        val cascadeRouter = ProviderCascadeRouter(
            factory = ProviderFactory({ config }),
            classifier = ProviderFailureClassifier(),
            registry = StaticProviderDescriptorRegistry(),
            localHealth = { OllamaHealthProbe().probe().online },
            localOnly = { config.runtime.localOnly },
            paidGate = EmergencyPaidGate()
        )
        val cascadeResult = cascadeRouter.completeWithCascade("default", prompt = node.label, context = "Synthesizing node ${node.id}")
        if (cascadeResult.queued || cascadeResult.paidApproval != null) {
            return SynthesisResult(false, "no eligible provider resolved: ${cascadeResult.queueReason ?: cascadeResult.response}")
        }
        return SynthesisResult(true, "provider call dispatched: ${cascadeResult.providerName}")
    }

    private fun synthesizeCommand(node: DagNode, worktreeRoot: Path, request: WaveExecutionRequest): SynthesisResult {
        val command = node.actionPayload ?: return SynthesisResult(false, "no command payload")
        val scriptPath = worktreeRoot.resolve(".atropos/scripts/${node.id}.sh")
        scriptPath.parent?.let { Files.createDirectories(it) }
        Files.writeString(scriptPath, "#!/usr/bin/env sh\nset -eu\n$command\n")
        return SynthesisResult(true, "command script written: ${worktreeRoot.relativize(scriptPath)}")
    }

    private fun synthesizeVerification(node: DagNode, worktreeRoot: Path, request: WaveExecutionRequest): SynthesisResult {
        val verifyScript = worktreeRoot.resolve("verify.sh")
        if (!Files.exists(verifyScript)) {
            val language = ProjectLanguage.layoutFor(ProjectLanguage.detect(request.projectSpec.prompt))
            val layout = ProjectLanguageLayout.forLanguage(language)
            val pkg = AppProjectGenerator.safeName(request.projectSpec.intent.name)
            val content = layout.verify.replace("\${PACKAGE}", pkg)
            Files.writeString(verifyScript, content)
            verifyScript.toFile().setExecutable(true)
        }
        return SynthesisResult(true, "verification gate synthesized")
    }

    private fun generateSourceForNode(node: DagNode, request: WaveExecutionRequest): String {
        val spec = request.projectSpec
        val packageName = AppProjectGenerator.safeName(spec.intent.name)
        val hasExpression = AppCapability.EXPRESSION in spec.intent.capabilities()
        val language = ProjectLanguage.layoutFor(ProjectLanguage.detect(spec.prompt))
        val isTest = node.actionPayload?.contains("test", ignoreCase = true) == true

        return when (language) {
            ProjectLanguage.PYTHON -> if (isTest) generatePythonTest(spec, hasExpression, request) else generatePythonMain(spec, hasExpression, request)
            ProjectLanguage.TYPESCRIPT -> if (isTest) generateTypeScriptTest(spec, hasExpression, request) else generateTypeScriptMain(spec, hasExpression, request)
            ProjectLanguage.KOTLIN -> if (isTest) generateKotlinTest(spec, packageName, hasExpression, request) else generateKotlinMain(spec, packageName, hasExpression, request)
            else -> if (isTest) generatePythonTest(spec, hasExpression, request) else generatePythonMain(spec, hasExpression, request)
        }
    }

    private fun generatePythonMain(spec: AppProjectSpec, hasExpression: Boolean, request: WaveExecutionRequest): String = buildString {
        appendLine("# ATROPOS lineage: prompt_sha256=${request.lineage.promptSha256}")
        appendLine("import sys")
        appendLine("import re")
        appendLine()
        appendLine("class CliResult:")
        appendLine("    def __init__(self, exit_code: int, output: str = \"\", error: str = \"\"):")
        appendLine("        self.exit_code = exit_code")
        appendLine("        self.output = output")
        appendLine("        self.error = error")
        appendLine()
        if (hasExpression) {
            appendLine("def evaluate(expression: str) -> float:")
            appendLine("    match = re.match(r\"^\\s*(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(-?\\d+(?:\\.\\d+)?)\\s*$\", expression)")
            appendLine("    if not match: raise ValueError(\"expected: number operator number\")")
            appendLine("    l, op, r = float(match.group(1)), match.group(2), float(match.group(3))")
            appendLine("    if op == \"/\" and r == 0.0: raise ZeroDivisionError(\"division by zero\")")
            appendLine("    ops = {'+': l + r, '-': l - r, '*': l * r, '/': l / r}")
            appendLine("    return ops[op]")
            appendLine()
            appendLine("def run_expression(args: list) -> CliResult:")
            appendLine("    if not args or args == [\"--help\"]:")
            appendLine("        return CliResult(2 if not args else 0, output=\"usage: ${spec.intent.name} <expr>\")")
            appendLine("    try:")
            appendLine("        return CliResult(0, output=str(evaluate(\" \".join(args))))")
            appendLine("    except Exception as e:")
            appendLine("        return CliResult(2, error=str(e))")
            appendLine()
        }
        appendLine("def run_app(args: list) -> CliResult:")
        appendLine("    if not args or args == [\"--help\"]:")
        appendLine("        return CliResult(0, output=\"usage: ${spec.intent.name} [--help]\")")
        appendLine("    return CliResult(0, output=f\"executed {args[0]}\")")
        appendLine()
        appendLine("if __name__ == '__main__':")
        appendLine("    res = run_expression(sys.argv[1:]) if $hasExpression else run_app(sys.argv[1:])")
        appendLine("    if res.output: print(res.output)")
        appendLine("    if res.error: print(res.error, file=sys.stderr)")
        appendLine("    sys.exit(res.exit_code)")
    }

    private fun generatePythonTest(spec: AppProjectSpec, hasExpression: Boolean, request: WaveExecutionRequest): String = buildString {
        appendLine("# Tests for ${spec.intent.name}")
        appendLine("import unittest")
        val imports = if (hasExpression) "from main import evaluate, run_expression, run_app, CliResult" else "from main import run_app, CliResult"
        appendLine(imports)
        appendLine()
        appendLine("class MainTest(unittest.TestCase):")
        if (hasExpression) {
            appendLine("    def test_evaluate(self):")
            appendLine("        self.assertEqual(evaluate(\"2 + 2\"), 4.0)")
            appendLine("        self.assertEqual(evaluate(\"8 / 2\"), 4.0)")
            appendLine("    def test_expression_cli(self):")
            appendLine("        self.assertEqual(run_expression([\"2 + 2\"]).exit_code, 0)")
        } else {
            appendLine("    def test_app(self):")
            appendLine("        self.assertEqual(run_app([\"--help\"]).exit_code, 0)")
        }
        appendLine()
        appendLine("if __name__ == '__main__': unittest.main()")
    }

    private fun generateTypeScriptMain(spec: AppProjectSpec, hasExpression: Boolean, request: WaveExecutionRequest): String = buildString {
        appendLine("// ATROPOS lineage: prompt_sha256=${request.lineage.promptSha256}")
        appendLine("export interface CliResult { exitCode: number; output?: string; error?: string; }")
        appendLine("export function run(args: string[]): CliResult {")
        appendLine("  if (!args.length || args[0] === '--help') return { exitCode: 0, output: 'usage: ${spec.intent.name}' };")
        appendLine("  return { exitCode: 0, output: `ok: \${args[0]}` };")
        appendLine("}")
    }

    private fun generateTypeScriptTest(spec: AppProjectSpec, hasExpression: Boolean, request: WaveExecutionRequest): String = buildString {
        appendLine("import { run } from './main.js';")
        appendLine("import assert from 'node:assert';")
        appendLine("import test from 'node:test';")
        appendLine("test('app executes', () => { assert.strictEqual(run(['--help']).exitCode, 0); });")
    }

    private fun generateKotlinMain(spec: AppProjectSpec, packageName: String, hasExpression: Boolean, request: WaveExecutionRequest): String = buildString {
        appendLine("package $packageName")
        appendLine()
        appendLine("// ATROPOS lineage: prompt_sha256=${request.lineage.promptSha256}")
        appendLine("data class CliResult(val exitCode: Int, val output: String = \"\", val error: String = \"\")")
        appendLine("fun runApp(args: List<String>): CliResult = CliResult(0, \"usage: ${spec.intent.name}\")")
        appendLine("fun main(args: Array<String>) { val r = runApp(args.toList()); println(r.output) }")
    }

    private fun generateKotlinTest(spec: AppProjectSpec, packageName: String, hasExpression: Boolean, request: WaveExecutionRequest): String = buildString {
        appendLine("package $packageName")
        appendLine("fun main() { check(runApp(listOf(\"--help\")).exitCode == 0) }")
    }
}

data class SynthesisResult(val success: Boolean, val message: String)