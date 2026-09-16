/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.security.RedactionFilter
import atropos.core.factory.FactoryRunOrchestrator
import atropos.core.factory.FactoryPlan
import atropos.core.factory.FactoryStep
import atropos.core.factory.GeneratedAppProject
import atropos.core.project.ProjectRegistry
import atropos.core.project.ProjectStatus

/**
 * Projects the factory preview onto the wire.
 *
 * `ADD-W-018` requires live preview + diagnostics strip: preview + hot reload +
 * diagnostics + rollback if factory exposes them.
 *
 * This projection reads the factory's live preview state and projects it.
 */
class FactoryPreviewProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val projectRegistry: ProjectRegistry? = null
) {

    /**
     * Renders the factory preview state.
     */
    fun render(plan: FactoryPlan?): String {
        if (plan == null) {
            return JsonWriter.obj(
                "ok" to JsonWriter.bool(false),
                "error" to JsonWriter.str("No factory plan available")
            )
        }

        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "planId" to JsonWriter.str(redactionFilter.redact(plan.id)),
            "intent" to JsonWriter.str(plan.intent),
            "prompt" to JsonWriter.str(redactionFilter.redact(plan.prompt)),
            "paidAllowed" to JsonWriter.bool(plan.paidAllowed),
            "status" to JsonWriter.str(plan.terminationReason ?: "in-progress"),
            "steps" to JsonWriter.arr(plan.steps.map { step ->
                JsonWriter.obj(
                    "kind" to JsonWriter.str(step.kind.name),
                    "route" to JsonWriter.str(step.route),
                    "localFirst" to JsonWriter.bool(step.localFirst),
                    "description" to JsonWriter.str(step.description)
                )
            }),
            "plannedAtomIds" to JsonWriter.arr(plan.plannedAtomIds.map { JsonWriter.str(it) }),
            "softFailures" to JsonWriter.arr(plan.softFailures.map { JsonWriter.str(it) }),
            "promptFingerprint" to JsonWriter.str(plan.promptFingerprint ?: ""),
            "promptSha256" to JsonWriter.str(plan.promptSha256 ?: ""),
            "confidenceScore" to JsonWriter.num(plan.confidenceScore ?: 0),
            "researchState" to JsonWriter.str(plan.researchState ?: "unknown"),
            "specGraphStatus" to JsonWriter.str(plan.specGraphStatus ?: "unknown"),
            "terminationReason" to JsonWriter.str(plan.terminationReason ?: "none"),
            "dag" to JsonWriter.obj(
                "planningDagId" to JsonWriter.str(plan.planningDagId ?: "none"),
                "plannedAtomCount" to JsonWriter.num(plan.plannedAtomIds.size)
            ),
            "evidence" to JsonWriter.obj(
                "hasPromptArtifact" to JsonWriter.bool(plan.promptFingerprint != null),
                "hasRequirementsArtifact" to JsonWriter.bool(plan.researchSha256 != null),
                "hasAtomArtifacts" to JsonWriter.bool(plan.plannedAtomIds.isNotEmpty())
            ),
            "generatedProject" to JsonWriter.obj(
                "hasGeneratedProject" to JsonWriter.bool(plan.generatedProject != null),
                "projectId" to JsonWriter.str(plan.projectRecordId ?: "none")
            )
        )
    }

    /**
     * Renders the generated project preview.
     */
    fun renderGenerated(project: GeneratedAppProject?): String {
        if (project == null) {
            return JsonWriter.obj(
                "ok" to JsonWriter.bool(false),
                "error" to JsonWriter.str("No generated project available")
            )
        }

        val projectRecord = projectRegistry?.records.firstOrNull { it.value.binding.repoRoot == project.path }
        val status = projectRecord?.value?.status ?: ProjectStatus.WORKING

        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "path" to JsonWriter.str(redactionFilter.redact(project.path)),
            "spec" to JsonWriter.obj(
                "name" to JsonWriter.str(project.spec.intent.name),
                "kind" to JsonWriter.str(project.spec.intent.kind)
            ),
            "files" to JsonWriter.arr(project.files.map { JsonWriter.str(it) }),
            "evidencePath" to JsonWriter.str(project.evidencePath),
            "commitId" to JsonWriter.str(project.commitId),
            "branch" to JsonWriter.str(project.branch),
            "treeSha256" to JsonWriter.str(project.treeSha256),
            "exportPath" to JsonWriter.str(project.exportPath),
            "planningDagId" to JsonWriter.str(project.planningDagId ?: "none"),
            "plannedAtomIds" to JsonWriter.arr(project.plannedAtomIds.map { JsonWriter.str(it) }),
            "proposalSha256" to JsonWriter.str(project.proposalSha256),
            "status" to JsonWriter.str(status.name)
        )
    }

    /**
     * Renders the live preview diagnostics.
     *
     * Note: FactoryRunOrchestrator does not expose public state fields.
     * This method renders what is available from the plan and registry.
     */
    fun renderDiagnostics(orchestrator: FactoryRunOrchestrator?, plan: FactoryPlan? = null): String {
        if (orchestrator == null && plan == null) {
            return JsonWriter.obj(
                "ok" to JsonWriter.bool(false),
                "error" to JsonWriter.str("No factory orchestrator or plan available")
            )
        }

        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "plan" to (plan?.let {
                JsonWriter.obj(
                    "planId" to JsonWriter.str(it.id),
                    "intent" to JsonWriter.str(it.intent),
                    "terminationReason" to JsonWriter.str(it.terminationReason ?: "in-progress"),
                    "steps" to JsonWriter.num(it.steps.size),
                    "plannedAtomIds" to JsonWriter.num(it.plannedAtomIds.size),
                    "softFailures" to JsonWriter.num(it.softFailures.size),
                    "researchState" to JsonWriter.str(it.researchState ?: "unknown"),
                    "specGraphStatus" to JsonWriter.str(it.specGraphStatus ?: "unknown"),
                    "confidenceScore" to JsonWriter.num(it.confidenceScore ?: 0),
                    "acceptanceFreezeSha256" to JsonWriter.str(it.acceptanceFreezeSha256 ?: "none"),
                    "terminationReason" to JsonWriter.str(it.terminationReason ?: "none")
                )
            } ?: JsonWriter.obj("status" to JsonWriter.str("no-plan"))),
            "orchestrator" to JsonWriter.obj(
                "note" to JsonWriter.str("FactoryRunOrchestrator state is internal; see plan for progress")
            )
        )
    }
}