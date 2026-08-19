/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Replacing the binary, and every way that must not go wrong.
 *
 * This writes over the jar the operator runs, on a device with no toolchain to
 * rebuild one. Every refusal here is a device that keeps working.
 */
class SelfUpdateTest {

    private val workspace: Path = Files.createTempDirectory("atropos-selfupdate")

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun jarAt(contents: String): Path {
        val jar = workspace.resolve("ATROPOS.jar")
        Files.write(jar, contents.toByteArray())
        return jar
    }

    private fun updater(
        jar: Path,
        published: ByteArray,
        checksum: String? = null,
        onDownload: (String) -> Unit = {}
    ) = SelfUpdate(
        download = { url ->
            onDownload(url)
            if (url.endsWith(".sha256")) {
                (checksum ?: sha256(published)).toByteArray()
            } else {
                published
            }
        },
        installedJar = { jar }
    )

    @Test
    fun a_verified_release_replaces_the_jar_and_keeps_the_old_one() {
        val jar = jarAt("old jar bytes")
        val published = "new jar bytes".toByteArray()

        val outcome = updater(jar, published).update()

        assertTrue(outcome is SelfUpdate.Outcome.Installed, outcome.toString())
        assertEquals("new jar bytes", Files.readString(jar))
        // A replacement that boots into something broken is a device with no
        // working binary and no way to build one.
        assertEquals("old jar bytes", Files.readString((outcome as SelfUpdate.Outcome.Installed).backup))
    }

    @Test
    fun a_jar_already_current_is_left_alone() {
        val bytes = "current jar".toByteArray()
        val jar = jarAt("current jar")
        var downloads = 0

        val outcome = updater(jar, bytes, onDownload = { if (!it.endsWith(".sha256")) downloads++ }).update()

        assertTrue(outcome is SelfUpdate.Outcome.UpToDate, outcome.toString())
        assertEquals(0, downloads, "a current install downloaded the jar anyway")
    }

    @Test
    fun a_checksum_mismatch_installs_nothing() {
        val jar = jarAt("old jar bytes")

        val outcome = updater(
            jar,
            published = "tampered".toByteArray(),
            checksum = sha256("something else entirely".toByteArray())
        ).update()

        assertTrue(outcome is SelfUpdate.Outcome.Refused, outcome.toString())
        assertTrue((outcome as SelfUpdate.Outcome.Refused).reason.contains("checksum mismatch"))
        assertEquals("old jar bytes", Files.readString(jar), "a mismatched jar was installed")
    }

    @Test
    fun a_missing_checksum_is_a_refusal_rather_than_a_shrug() {
        // "The server did not offer one" is indistinguishable from "someone
        // removed it", so it cannot be treated as permission to skip the check.
        val jar = jarAt("old jar bytes")

        val outcome = SelfUpdate(
            download = { url -> if (url.endsWith(".sha256")) error("404") else "new".toByteArray() },
            installedJar = { jar }
        ).update()

        assertTrue(outcome is SelfUpdate.Outcome.Refused, outcome.toString())
        assertEquals("old jar bytes", Files.readString(jar))
    }

    @Test
    fun a_checksum_that_is_not_a_checksum_is_refused() {
        val jar = jarAt("old jar bytes")

        val outcome = updater(jar, "new".toByteArray(), checksum = "<html>404 not found</html>").update()

        assertTrue(outcome is SelfUpdate.Outcome.Refused, outcome.toString())
        assertEquals("old jar bytes", Files.readString(jar))
    }

    @Test
    fun a_failed_download_leaves_the_working_jar_untouched() {
        val jar = jarAt("old jar bytes")

        val outcome = SelfUpdate(
            download = { url ->
                if (url.endsWith(".sha256")) sha256("new".toByteArray()).toByteArray()
                else error("connection reset")
            },
            installedJar = { jar }
        ).update()

        assertTrue(outcome is SelfUpdate.Outcome.Refused, outcome.toString())
        assertEquals("old jar bytes", Files.readString(jar))
    }

    @Test
    fun an_install_it_cannot_locate_is_refused_with_a_way_forward() {
        val outcome = SelfUpdate(download = { ByteArray(0) }, installedJar = { null }).update()

        assertTrue(outcome is SelfUpdate.Outcome.Refused)
        assertTrue((outcome as SelfUpdate.Outcome.Refused).remedy.contains("ATROPOS_JAR"))
    }

    @Test
    fun every_refusal_carries_a_remedy() {
        // An update that fails and offers nothing leaves an operator with a
        // device they cannot build on and no next step.
        val jar = jarAt("old")
        val refusals = listOf(
            updater(jar, "new".toByteArray(), checksum = "nonsense").update(),
            SelfUpdate(download = { ByteArray(0) }, installedJar = { null }).update()
        )

        refusals.forEach { outcome ->
            val refused = outcome as SelfUpdate.Outcome.Refused
            assertTrue(refused.remedy.isNotBlank(), "a refusal with no way forward: ${refused.reason}")
            assertTrue(SelfUpdateText.render(outcome).contains(refused.remedy))
        }
    }

    @Test
    fun the_operator_is_told_the_running_process_is_still_the_old_code() {
        val jar = jarAt("old")
        val text = SelfUpdateText.render(updater(jar, "new".toByteArray()).update())

        assertTrue(text.contains("Restart"), text)
        assertTrue(text.contains("--version"), text)
    }
}
