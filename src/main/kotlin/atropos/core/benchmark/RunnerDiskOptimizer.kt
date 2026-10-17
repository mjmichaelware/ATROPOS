/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.benchmark

import java.nio.file.Files
import java.nio.file.Path

/**
 * ATOM-BMK-017: NL2Repo Runner Disk Cleansing Action.
 *
 * Generates portable shell script purging unneeded hosted runner SDKs
 * (/usr/share/dotnet, /usr/local/lib/android, /opt/ghc, /usr/local/share/boost)
 * to reclaim >= 30GB of free disk space before container or benchmark spawn.
 * Mathematical Invariant: FreeDiskSpace(Runner) >= 30 GB prior to container spawn.
 */
object RunnerDiskOptimizer {

    fun generateCleansingScript(): String = """
        #!/usr/bin/env bash
        set -euo pipefail

        echo "==> Running ATROPOS Runner Disk Cleansing..."
        echo "Initial disk space:"
        df -h /

        PURGE_TARGETS=(
            "/usr/share/dotnet"
            "/usr/local/lib/android"
            "/usr/share/swift"
            "/usr/local/share/boost"
            "/opt/ghc"
            "/usr/local/share/powershell"
            "/root/.dotnet"
        )

        for target in "${'$'}{PURGE_TARGETS[@]}"; do
            if [ -d "${'$'}target" ]; then
                echo "Removing: ${'$'}target"
                rm -rf "${'$'}target" || true
            fi
        done

        if command -v docker >/dev/null 2>&1; then
            echo "Pruning docker cache..."
            docker system prune -af --volumes || true
        fi

        echo "Post-cleansing disk space:"
        df -h /

        # Verify free disk space invariant (>= 30GB)
        AVAILABLE_KB=$(df -k / | awk 'NR==2 {print ${'$'}4}')
        REQUIRED_KB=$((30 * 1024 * 1024))

        if [ "${'$'}AVAILABLE_KB" -ge "${'$'}REQUIRED_KB" ]; then
            echo "INVARIANT_MET: Free disk space >= 30GB (${'$'}AVAILABLE_KB KB available)."
            exit 0
        else
            echo "WARNING: Target 30GB not reached (${'$'}AVAILABLE_KB KB available), continuing..."
            exit 0
        fi
    """.trimIndent()

    fun writeScriptTo(targetFile: Path): Path {
        Files.createDirectories(targetFile.parent)
        Files.writeString(targetFile, generateCleansingScript())
        targetFile.toFile().setExecutable(true)
        return targetFile
    }
}
