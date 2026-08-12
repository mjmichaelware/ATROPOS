package atropos.core.security

/**
 * The files that may never be read into a provider context.
 *
 * This is the last line before repository bytes leave the process. Everything
 * here is excluded for one of two reasons: it is a credential, or it is binary
 * noise that would consume the context budget without informing anything.
 *
 * ## Why this file exists
 *
 * The rules were maintained in two places — `AgentContextCollector.isExcluded`
 * for directly collected context, and `ContentAddressedTreeWriter.excluded` for
 * packed source bindings — and the two lists had drifted apart in both
 * directions. The tree writer did not exclude `.env`; the collector did not
 * exclude `.atropos/source-bindings`. Two hand-synchronised copies of a secret
 * list means the path that missed a rule is the path that leaks, and nothing
 * fails loudly when they diverge.
 *
 * The two lists are unioned here rather than one being chosen. Every rule on
 * either side was added because someone judged that content unsafe or useless
 * to send; dropping either side's rules to make the merge tidy would be a
 * regression in exactly the direction that matters.
 *
 * ## Matching is by name and prefix, never by content
 *
 * A path is judged before it is opened. Deciding by content would mean reading
 * the secret in order to determine that it should not be read.
 *
 * The `.kt`/`.kts` exemption on [NAME_FRAGMENTS] is deliberate: source files
 * legitimately carry names like `ApiKeyStore.kt`, and excluding the code that
 * handles credentials would blind the agent to the very subsystem it is most
 * often asked to work on. The exemption applies only to the fragment rules —
 * a file actually ending in `.key` is excluded whatever it is called.
 */
object ContextPathExclusions {

    /**
     * @param relativePath repository-relative, `/`-separated. Backslashes are
     *   folded, so a Windows-style separator cannot slip a path past a prefix rule.
     */
    fun isExcluded(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/').trim()
        if (normalized.isEmpty()) return false
        val name = normalized.substringAfterLast('/')

        if (DIRECTORIES.any { normalized == it || normalized.startsWith("$it/") }) return true
        if (normalized == ENV_FILE || normalized.startsWith("$ENV_FILE.")) return true
        if (name == ENV_FILE || name.startsWith("$ENV_FILE.")) return true
        if (SUFFIXES.any { name.endsWith(it) }) return true
        if (isSourceFile(name)) return false
        return NAME_FRAGMENTS.any { name.contains(it, ignoreCase = true) }
    }

    private fun isSourceFile(name: String): Boolean =
        SOURCE_SUFFIXES.any { name.endsWith(it, ignoreCase = true) }

    /** Directory roots excluded wholesale, along with everything beneath them. */
    private val DIRECTORIES = listOf(
        ".git",
        ".gradle",
        "build",
        ".atropos/secrets",
        ".atropos/source-bindings",
        ".atropos/agent/patches"
    )

    /**
     * Extensions that are either credential material or binary.
     *
     * Binaries are excluded for budget rather than secrecy: a jar rendered into
     * a text context is megabytes of noise that displaces the source the agent
     * was actually asked about.
     */
    private val SUFFIXES = listOf(
        ".jar", ".class", ".zip", ".tar", ".gz",
        ".png", ".jpg", ".jpeg", ".gif",
        ".key", ".pem", ".crt", ".p12",
        ".token", ".secret", ".credentials"
    )

    /** Substrings that mark a file as credential-bearing regardless of extension. */
    private val NAME_FRAGMENTS = listOf("token", "secret", "credential", "keys")

    /** Source files are never excluded by [NAME_FRAGMENTS]. */
    private val SOURCE_SUFFIXES = listOf(".kt", ".kts")

    private const val ENV_FILE = ".env"
}
