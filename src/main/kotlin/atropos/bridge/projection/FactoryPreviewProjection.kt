/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.security.RedactionFilter
import atropos.core.factory.FactoryRunOrchestrator
import atropos.core.factory.FactoryPlan
import atropos.core.factory.GeneratedAppProject

/**
 * Projects the factory preview onto the wire.
 *
 * `ADD-W-018` requires live preview + diagnostics strip: preview + hot reload +
 * diagnostics + rollback if factory exposes them.
 *
 * This projection reads the factory's live preview state and projects it.
 */
class FactoryPreviewProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
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
            "projectId" to JsonWriter.str(redactionFilter.redact(plan.projectId)),
            "projectName" to JsonWriter.str(redactionFilter.redact(plan.projectName)),
            "status" to JsonWriter.str(plan.status.name),
            "steps" to JsonWriter.arr(plan.steps.map { step ->
                JsonWriter.obj(
                    "id" to JsonWriter.str(step.id),
                    "name" to JsonWriter.str(step.name),
                    "status" to JsonWriter.str(step.status.name),
                    "description" to JsonWriter.str(step.description)
                )
            }),
            "dag" to JsonWriter.obj(
                "nodes" to JsonWriter.num(plan.dag?.nodes?.size ?: 0),
                "edges" to JsonWriter.num(plan.dag?.edges?.size ?: 0)
            ),
            "evidence" to JsonWriter.obj(
                "hasPromptArtifact" to JsonWriter.bool(plan.evidence?.promptArtifact != null),
                "hasRequirementsArtifact" to JsonWriter.bool(plan.evidence?.requirementsArtifact != null),
                "hasAtomArtifacts" to JsonWriter.bool(plan.evidence?.atomArtifacts?.isNotEmpty() ?: false)
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

        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "projectId" to JsonWriter.str(redactionFilter.redact(project.projectId)),
            "repoRoot" to JsonWriter.str(redactionFilter.redact(project.repoRoot.toString())),
            "branch" to JsonWriter.str(project.branch),
            "commit" to JsonWriter.str(project.commit),
            "treeHash" to JsonWriter.str(project.treeHash),
            "verification" to JsonWriter.obj(
                "passed" to JsonWriter.bool(project.verificationPassed),
                "output" to JsonWriter.str(redactionFilter.redact(project.verificationOutput))
            )
        )
    }

    /**
     * Renders the live preview diagnostics.
     */
    fun renderDiagnostics(orchestrator: FactoryRunOrchestrator?): String {
        if (orchestrator == null) {
            return JsonWriter.obj(
                "ok" to JsonWriter.bool(false),
                "error" to JsonWriter.str("No factory orchestrator available")
            )
        }

        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "currentStep" to JsonWriter.str(orchestrator.currentStep?.name ?: "none"),
            "completedSteps" to JsonWriter.num(orchestrator.completedSteps?.size ?: 0),
            "totalSteps" to JsonWriter.num(orchestrator.totalSteps),
            "lastError" to JsonWriter.str(redactionFilter.redact(orchestrator.lastError ?: "none")),
            "diagnostics" to JsonWriter.arr(orchestrator.diagnostics?.map { d ->
                JsonWriter.obj(
                    "stage" to JsonWriter.str(d.stage),
                    "message" to JsonWriter.str(redactionFilter.redact(d.message)),
                    "severity" to JsonWriter.str(d.severity.name)
                )
            } ?: emptyList())
        )
    }
}