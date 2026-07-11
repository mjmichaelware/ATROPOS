package atropos.core.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files

class KeyDoctorServiceTest {
    @Test
    fun doctor_respects_secret_precedence_and_redacts_values() {
        val root = Files.createTempDirectory("atropos-key-doctor").toFile()
        val localRoot = root.resolve("secrets").apply { mkdirs() }
        localRoot.resolve("GROQ_API_KEY.secret").writeText("local-secret")

        val service = KeyDoctorService(
            secretSource = DefaultSecretSource.create(
                explicit = mapOf("GROQ_API_KEY" to "explicit-secret"),
                env = mapOf("OPENROUTER_API_KEY" to "env-secret"),
                localRoot = localRoot
            ),
            setupHelper = KeySetupHelper(localRoot)
        )

        val report = service.renderDoctor()
        assertTrue(report.contains("precedence: explicit > environment > local_file"))
        assertTrue(report.contains("GROQ_API_KEY"))
        assertTrue(report.contains("source=explicit"))
        assertTrue(report.contains("OPENROUTER_API_KEY"))
        assertTrue(report.contains("source=environment"))
        assertFalse(report.contains("explicit-secret"))
        assertFalse(report.contains("env-secret"))
        assertFalse(report.contains("local-secret"))
    }

    @Test
    fun setup_writes_templates_without_raw_values() {
        val root = Files.createTempDirectory("atropos-key-setup").toFile()
        val service = KeyDoctorService.create(root)
        val report = service.renderSetup()
        val template = root.resolve("secrets.template").readText()

        assertTrue(report.contains("raw values: never written by setup"))
        assertTrue(template.contains("GROQ_API_KEY="))
        assertFalse(template.contains("sk-"))
    }
}
