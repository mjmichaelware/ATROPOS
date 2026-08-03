package atropos.core.agent

/**
 * Everything a provider needs to know about a failed verification to repair it.
 *
 * A data class rather than nine positional parameters threaded through three
 * call layers. The previous shape made it possible to pass stdout where stderr
 * was expected — both are `String`, so nothing would have caught it, and the
 * resulting repair would have been reasoning about the wrong output stream.
 *
 * Kept separate from [AgentPromptContract], which renders the prompt text. This
 * is the material; that is the wording.
 */
internal data class AgentRepairPromptContext(
    val patchId: String,
    val changedPaths: List<String>,
    val failedCommand: String,
    val exitCode: Int?,
    val durationMillis: Long,
    val stdout: String,
    val stderr: String,
    val context: String
) {
    /**
     * The task string the response is attested against.
     *
     * It must be identical on the initial attempt and on the retry. Attestation
     * ties a response to the task it was asked for, so deriving it from the
     * prompt — which changes between attempt and retry — would make every retry
     * fail verification for a reason unrelated to its content.
     */
    val attestationTask: String get() = "repair patch $patchId"
}
