/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

object NativeToolchainRegistry {
    private val drivers = ConcurrentHashMap<String, LanguageToolchainDriver>()

    fun register(driver: LanguageToolchainDriver) {
        drivers[driver.languageId] = driver
    }

    fun getDriver(lang: String): LanguageToolchainDriver? {
        return drivers[lang]
    }

    fun requireDriver(lang: String): LanguageToolchainDriver {
        val driver = drivers[lang]
        if (driver == null) {
            throw IllegalStateException("No toolchain driver registered for language: $lang")
        }
        return driver
    }

    fun locateBinary(binary: String): Path? {
        val atroposToolchainEnv = System.getenv("ATROPOS_TOOLCHAIN_${binary.uppercase()}")
        if (atroposToolchainEnv != null) {
            val path = Paths.get(atroposToolchainEnv)
            if (Files.isExecutable(path)) return path
        }

        val pathEnv = System.getenv("PATH") ?: ""
        for (dir in pathEnv.split(":")) {
            val candidate = Paths.get(dir, binary)
            if (Files.isExecutable(candidate)) return candidate
        }

        val knownPaths = when {
            File("/data/data/com.termux/files/usr/bin").exists() -> listOf(
                "/data/data/com.termux/files/usr/bin",
                "/usr/bin",
                "/usr/local/bin"
            )
            File("/opt/homebrew/bin").exists() -> listOf(
                "/opt/homebrew/bin",
                "/usr/local/bin",
                "/usr/bin"
            )
            else -> listOf(
                "/usr/bin",
                "/usr/local/bin"
            )
        }

        for (dir in knownPaths) {
            val candidate = Paths.get(dir, binary)
            if (Files.isExecutable(candidate)) return candidate
        }

        return null
    }
}