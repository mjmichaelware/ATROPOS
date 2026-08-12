package atropos.core.memory

import atropos.core.policy.BoundedProcessRunner
import java.nio.file.Path

/**
 * Detects which optional local memory backends this machine actually has.
 *
 * Probing rather than configuring is deliberate. ATROPOS is meant to run on
 * whatever device it is copied to, so a config flag saying "sqlite is
 * available" is a claim about the machine that the machine itself can answer.
 * A wrong flag produces a runtime failure on a device that was never asked.
 *
 * ## Availability means usable, not installed
 *
 * [sqliteVecAvailable] does not stop at finding a `sqlite3` binary — it asks
 * that binary to load the extension. A present sqlite3 without `sqlite_vec`
 * would otherwise report as available and fail on first use, which is the
 * failure this probe exists to prevent.
 *
 * Every probe fails closed: an exception, a missing shell, a sandbox that
 * forbids spawning — all report unavailable. A probe that threw would take down
 * the memory subsystem over an optional accelerator.
 */
class MemoryBackendProbe(
    private val runShell: (String) -> Int = ::runShellCommand
) {

    fun commandExists(name: String): Boolean {
        // Validated outside the fail-closed wrapper on purpose. An unusable
        // environment should report unavailable; a caller passing a name that
        // could break out of the command is a defect, and swallowing it into a
        // quiet `false` would hide the very thing this check exists to catch.
        val safeName = shellSafe(name)
        return runCatching { runShell("command -v $safeName >/dev/null 2>&1") == 0 }
            .getOrDefault(false)
    }

    fun sqliteVecAvailable(): Boolean =
        runCatching {
            runShell(
                "command -v sqlite3 >/dev/null 2>&1 && " +
                    "sqlite3 ':memory:' \"select load_extension('sqlite_vec');\" >/dev/null 2>&1"
            ) == 0
        }.getOrDefault(false)

    /**
     * Refuses a probe name that could break out of the command.
     *
     * Names reach here from configuration rather than from a prompt, but a
     * probe is still a shell invocation, and one that interpolates an
     * unchecked name is a command injection waiting for the first caller who
     * builds the name from something dynamic.
     */
    private fun shellSafe(name: String): String {
        require(name.isNotBlank() && name.all { it.isLetterOrDigit() || it in ALLOWED_PUNCTUATION }) {
            "backend probe name is not a safe command name: $name"
        }
        return name
    }

    private companion object {
        val ALLOWED_PUNCTUATION = setOf('-', '_', '.', '+')

        fun runShellCommand(script: String): Int = runCatching {
            val result = BoundedProcessRunner().run(
                command = listOf("sh", "-c", script),
                directory = Path.of("/"),
                timeoutMillis = 5_000L,
                maxOutputBytes = 4_096,
                maxOutputLines = 128
            )
            if (result.timedOut || result.launchError != null) 1 else result.exitCode ?: 1
        }.getOrDefault(1)
    }
}
