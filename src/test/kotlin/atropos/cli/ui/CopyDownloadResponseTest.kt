package atropos.cli.ui

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CopyDownloadResponseTest {
    @Test
    fun copy_and_download_are_redacted_and_bounded() {
        val root = Files.createTempDirectory("atropos-response-export-")
        val target = root.resolve("response.txt")
        val response = CopyDownloadResponse().download("api_key=sk-test-secret-value", target)

        assertEquals(response.text, Files.readString(target))
        assertFalse("sk-test-secret-value" in response.text)
        assertTrue(response.text.contains("<redacted"))
    }
}
