/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.platform

import java.io.File

/** Platform abstraction interfaces (AUD035) */
interface LocalToolchain {
    fun executeCommand(command: String): Int
}

interface Renderer {
    fun renderUi()
}

interface InputSystem {
    fun readInput(): String
}

/** Stubs and configurations for multiplatform targets (AUD030, AUD031, AUD033, AUD034) */
object ComposeDesktopStub : Renderer {
    override fun renderUi() {
        println("Compose Desktop: Render")
    }
}

object ComposeIosStub : Renderer {
    override fun renderUi() {
        println("Compose iOS: Render")
    }
}

object GraalVmConfig {
    val isNativeImage: Boolean get() = System.getProperty("org.graalvm.nativeimage.imagecode") != null
}

object KtorBackendConfig {
    const val DEFAULT_PORT = 8080
    fun getBackendUrl(): String = "http://localhost:$DEFAULT_PORT"
}

/** Swarm Loader and Progress Aggregators (AUD271, AUD272) */
object SwarmMdLoader {
    fun loadSwarmConfig(swarmFile: File): String {
        if (!swarmFile.exists()) {
            return "REFUSED: Swarm.md does not exist"
        }
        return swarmFile.readText()
    }
}

object AggregateProgressCalculator {
    fun calculatePercentage(registryFile: File): Double {
        if (!registryFile.exists()) return 0.0
        val text = registryFile.readText()
        val total = text.split("\"obligationId\"").size - 1
        val written = text.split("\"status\": \"WRITTEN\"").size - 1
        if (total == 0) return 0.0
        return (written.toDouble() / total.toDouble()) * 100.0
    }
}
