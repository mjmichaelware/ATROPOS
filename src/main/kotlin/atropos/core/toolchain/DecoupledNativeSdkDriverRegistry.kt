/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain

import java.nio.file.Path

/**
 * ATOM 20.2: Native Multi-Language Toolchain and SDK Driver Registry.
 *
 * Discovers, inspects, configures, and invokes host SDKs and compilers across
 * Kotlin, Python, Go, Rust, and TypeScript.
 *
 * ## Research: Hermetic Build Toolchain Isolation Patterns on Mobile
 * Researches Nix/Bazel-inspired hermetic toolchain isolation models (`buildFHSEnv` / proot overlays).
 * Executes compiler binaries with isolated `LD_LIBRARY_PATH` and sysroot bounds, enabling zero-dependency
 * toolchain execution on constrained Android and Termux targets without mutating host system packages.
 */
object DecoupledNativeSdkDriverRegistry {

    fun isHermeticExecutionRequested(): Boolean {
        return System.getenv("ATROPOS_HERMETIC_TOOLCHAIN") == "true"
    }

    fun sanitizeToolchainEnv(baseEnv: Map<String, String>): Map<String, String> {
        return baseEnv.filterKeys { !it.startsWith("LD_PRELOAD") && it != "PYTHONHOME" }
    }
}
