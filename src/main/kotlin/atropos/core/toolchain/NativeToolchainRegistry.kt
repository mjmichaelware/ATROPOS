/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain

import atropos.core.toolchain.drivers.AndroidSdkDriver
import atropos.core.toolchain.drivers.DatabaseContainerMockSandboxDriver
import atropos.core.toolchain.drivers.GoToolchainDriver
import atropos.core.toolchain.drivers.KotlinGradleDriver
import atropos.core.toolchain.drivers.PythonFastApiDriver
import atropos.core.toolchain.drivers.RustCargoDriver
import atropos.core.toolchain.drivers.TypescriptNodeDriver
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

object NativeToolchainRegistry {
    private val drivers = ConcurrentHashMap<String, LanguageToolchainDriver>()

    init {
        register(RustCargoDriver())
        register(TypescriptNodeDriver())
        register(GoToolchainDriver())
        register(PythonFastApiDriver())
        register(KotlinGradleDriver())
        register(AndroidSdkDriver())
        register(DatabaseContainerMockSandboxDriver())
    }

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

        val resolved = atropos.core.platform.PlatformAdapterRegistry.current().resolveToolPath(binary)
        if (Files.isExecutable(resolved) || Files.exists(resolved)) return resolved

        return null
    }
}