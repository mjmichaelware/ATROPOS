/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.benchmark

import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertTrue

class RunnerDiskOptimizerTest {

    @Test
    fun `generateCleansingScript generates script purging dotnet and android`() {
        val script = RunnerDiskOptimizer.generateCleansingScript()
        assertTrue(script.contains("/usr/share/dotnet"))
        assertTrue(script.contains("/usr/local/lib/android"))
        assertTrue(script.contains("INVARIANT_MET"))
        assertTrue(script.contains("30"))
    }

    @Test
    fun `writeScriptTo produces executable file on disk`() {
        val tempDir = Files.createTempDirectory("disk_clean_test")
        val scriptPath = tempDir.resolve("clean.sh")

        RunnerDiskOptimizer.writeScriptTo(scriptPath)
        assertTrue(Files.isRegularFile(scriptPath))
        assertTrue(Files.isExecutable(scriptPath))
    }
}
