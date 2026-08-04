package atropos.core.agent

/** Resolves bounded operator proof controls without changing normal defaults. */
object SelfHostRuntimeRunLimits {
    private const val DEFAULT_MAX_ADVANCES = 25
    private const val MAX_ALLOWED_ADVANCES = 100

    fun maxAdvances(
        environment: Map<String, String> = System.getenv(),
        properties: (String) -> String? = System::getProperty
    ): Int = (environment["ATROPOS_SELF_HOST_MAX_ADVANCES"]
        ?: properties("atropos.selfHost.maxAdvances"))
        ?.toIntOrNull()
        ?.coerceIn(1, MAX_ALLOWED_ADVANCES)
        ?: DEFAULT_MAX_ADVANCES
}
