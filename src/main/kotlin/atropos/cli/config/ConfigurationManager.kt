/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.config

import java.nio.file.Path
import java.util.Locale

class ConfigurationManager(
    private val envProvider: (String) -> String? = System::getenv,
    private val propertyProvider: (String) -> String? = System::getProperty,
    private val hasConsole: Boolean = System.console() != null
) {
    val workspace: String = propertyProvider("user.dir") ?: "."
    var model: String = envProvider("ATROPOS_MODEL") ?: "llama3.2"
    val ollamaHost: String =
        envProvider("OLLAMA_HOST") ?: "http://127.0.0.1:11434"

    val isInteractiveTerminal: Boolean
        get() = hasConsole &&
            !envProvider("TERM").equals("dumb", ignoreCase = true)

    val isColorEnabled: Boolean
        get() = isInteractiveTerminal &&
            envProvider("NO_COLOR").isNullOrEmpty()

    fun homePath(): String {
        return Path.of(workspace)
            .toAbsolutePath()
            .normalize()
            .toString()
    }

    fun inputUsdPerToken(provider: String): Double {
        val name = provider
            .uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9]"), "_")

        val perMillion =
            envProvider("ATROPOS_${name}_INPUT_USD_PER_MILLION")
                ?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?: return 0.0

        return perMillion / 1_000_000.0
    }
}
