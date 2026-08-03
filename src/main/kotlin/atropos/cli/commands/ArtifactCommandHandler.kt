/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.artifact.ArtifactPipeline
import atropos.core.artifact.ArtifactVerificationService
import atropos.core.artifact.JarSwapEvidence
import atropos.core.artifact.SafeJarSwapGate
import java.nio.file.Path

/**
 * `/artifact` — Phase 19 plan, build, verify, install, commit, and gate.
 *
 * The promotion path is the reason this handler stays thin. `promote-jar`
 * resolves the named verification ids against the pipeline's own record and
 * refuses when any one of them is unknown, so an operator cannot promote by
 * naming evidence that was never produced. That check belongs to
 * [SafeJarSwapGate] and the pipeline; this file's job is to pass real ids
 * through and to render the refusal, never to soften it.
 */
class ArtifactCommandHandler(
    private val pipeline: ArtifactPipeline = ArtifactPipeline(),
    private val verification: ArtifactVerificationService = ArtifactVerificationService(),
    private val jarSwapGate: SafeJarSwapGate = SafeJarSwapGate()
) {
    fun handle(args: List<String>): String = when (args.firstOrNull()) {
        "plan" -> plan(args)
        "build" -> build(args)
        "verify" -> verify(args)
        "install" -> install(args)
        "commit" -> commit(args)
        "gate" -> gate(args)
        "promote-jar" -> promoteJar(args)
        else -> pipeline.report().summary
    }

    private fun plan(args: List<String>): String {
        val prompt = args.drop(1).joinToString(" ")
        if (prompt.isBlank()) return "usage: /artifact plan <prompt>"
        val plan = pipeline.plan(prompt)
        return "Artifact plan: ${plan.id} intent=${plan.intent} steps=${plan.steps.size}"
    }

    private fun build(args: List<String>): String {
        val prompt = args.drop(1).joinToString(" ")
        if (prompt.isBlank()) return "usage: /artifact build <prompt>"
        val report = pipeline.createDeliverable(prompt)
        val artifact = report.artifacts.singleOrNull() ?: return report.summary
        return "Artifact deliverable: ${artifact.filePath} id=${artifact.id} " +
            "sha256=${artifact.sha256} (${report.summary})"
    }

    private fun verify(args: List<String>): String {
        if (args.size < 2) return "usage: /artifact verify <artifact-id>"
        return verification.verifyFull(args[1]).joinToString("\n") {
            "  ${it.kind.name}: ${if (it.passed) "PASS" else "FAIL"} - ${it.evidence.take(EVIDENCE_PREVIEW)}"
        }
    }

    private fun install(args: List<String>): String {
        if (args.size < 3) return "usage: /artifact install <artifact-id> <target-dir>"
        val proof = verification.checkInstall(args[1], args[2])
        return "Install: ${if (proof.verified) "OK" else "FAIL"} -> ${proof.targetPath} (${proof.durationMs}ms)"
    }

    private fun commit(args: List<String>): String {
        if (args.size < 3) return "usage: /artifact commit <message> <artifact-id> [proof-id...]"
        val candidate = verification.finalizeCommit(
            args[1],
            listOf(args[2]),
            args.drop(3),
            territoryCheck = true,
            secretCheck = true
        )
        return "Commit candidate: ${candidate.id} ready=${candidate.readyForCommit} files=${candidate.files.size}"
    }

    private fun gate(args: List<String>): String {
        if (args.size < 2) return "usage: /artifact gate <artifact-id>"
        val result = verification.runAcceptanceGate(args[1])
        return "Acceptance gate: ${if (result.passed) "PASS" else "FAIL"} - ${result.message}"
    }

    /**
     * Swaps a candidate JAR in only against verifications that actually exist.
     *
     * The recorded verifications are read once and the named ids matched against
     * them. Any id with no matching record aborts the promotion and is named in
     * the refusal — a promotion justified by evidence nobody can produce is the
     * exact shape of a fake VERIFIED.
     */
    private fun promoteJar(args: List<String>): String {
        if (args.size < 4) {
            return "usage: /artifact promote-jar <candidate-jar> <target-jar> " +
                "<verification-id> [verification-id...]"
        }

        val requestedIds = args.drop(3).toSet()
        val recorded = pipeline.report().verifications
        val evidence = recorded
            .filter { it.id in requestedIds }
            .map { JarSwapEvidence(it.passed, it.kind.name, "${it.id}: ${it.evidence}") }

        if (evidence.size != requestedIds.size) {
            val found = recorded.map { it.id }.toSet()
            val missing = requestedIds.filterNot { it in found }
            return "JAR promote refused: missing verification evidence ${missing.joinToString(",")}"
        }

        val result = jarSwapGate.promote(Path.of(args[1]), Path.of(args[2]), evidence)
        return "JAR promote: ${if (result.promoted) "PROMOTED" else "REFUSED"} - ${result.message}"
    }

    private companion object {
        const val EVIDENCE_PREVIEW = 80
    }
}
