package atropos.core.agent

object SelfHostCradleRuntimeState {
    const val LAST_SELF_HOST_GOAL: String = "shg-7abcea5c-417"
    const val LAST_SELF_HOST_PHASE: String = "11"

    /** Canonical source template used by the self-host DAG marker node. */
    fun sourceFor(goal: String, phase: String): String = """
        package atropos.core.agent

        object SelfHostCradleRuntimeState {
            const val LAST_SELF_HOST_GOAL: String = "${escape(goal)}"
            const val LAST_SELF_HOST_PHASE: String = "${escape(phase)}"
        }
    """.trimIndent()

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("$", "\\$")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
}
