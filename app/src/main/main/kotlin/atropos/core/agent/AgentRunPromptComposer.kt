package atropos.core.agent

/**
 * Composes the prompts and result lines of a two-stage agent run.
 *
 * A run asks for a plan first and a diff second. Keeping the wording here means
 * the run service is about sequencing and this is about what gets said — the
 * two change for different reasons, and the prompt text is the part that gets
 * tuned most often.
 *
 * ## Why the plan is truncated into the patch task
 *
 * [patchTask] caps the plan it carries forward. The plan is model output being
 * fed back into a second model call, so its length is not bounded by anything
 * the caller controls; a runaway plan would crowd out the repository context in
 * the patch request, which is the part that actually determines whether the
 * diff applies.
 */
internal class AgentRunPromptComposer {

    /** Stage one: reasoning only, explicitly no diff. */
    fun planPrompt(task: String): String = buildString {
        appendLine("Create a short implementation plan for this ATROPOS job.")
        appendLine("Return reasoning only, no diff.")
        appendLine("Task:")
        appendLine(task.trim())
    }.trimEnd()

    /**
     * Stage two: the original task, with the plan appended as context.
     *
     * The task leads. The plan is supporting material, and putting it first
     * would invite the model to patch the plan rather than the repository.
     */
    fun patchTask(task: String, plan: String): String = buildString {
        appendLine(task.trim())
        val compactPlan = plan.trim().take(MAXIMUM_PLAN_CHARACTERS)
        if (compactPlan.isNotBlank()) {
            appendLine()
            appendLine("Plan context:")
            appendLine(compactPlan)
        }
    }.trimEnd()

    /**
     * The one-line record of what a successful run produced.
     *
     * Every id that exists is named, so the result line alone is enough to find
     * the artefacts afterwards without re-reading the job record.
     */
    fun successResult(
        initialPatchId: String,
        verificationId: String?,
        repairPatchId: String?
    ): String = buildString {
        append("completed")
        append(" patch=$initialPatchId")
        repairPatchId?.let { append(" repair=$it") }
        verificationId?.let { append(" verification=$it") }
    }

    private companion object {
        const val MAXIMUM_PLAN_CHARACTERS = 2_000
    }
}
