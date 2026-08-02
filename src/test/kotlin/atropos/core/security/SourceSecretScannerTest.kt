package atropos.core.security

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceSecretScannerTest {
    @Test
    fun real_credentials_fail_with_redacted_classified_findings() {
        val root = Files.createTempDirectory("source-secret-")
        val file = root.resolve("src/credentials.kt")
        Files.createDirectories(file.parent)
        Files.writeString(file, "val token = super-secret-value-12345\n")

        val findings = SourceSecretScanner().scan(root, listOf("src/credentials.kt"))

        assertEquals(SourceSecretClassification.REAL_SECRET, findings.single().classification)
        assertEquals(1, findings.single().line)
        assertTrue(findings.single().redactedSpan.contains("redacted"))
        assertTrue(!findings.single().redactedSpan.contains("super-secret-value-12345"))
    }

    @Test
    fun strict_test_placeholders_pass_but_ui_and_migration_words_do_not_find() {
        val root = Files.createTempDirectory("source-safe-")
        val testFile = root.resolve("src/auth.test.ts")
        val migration = root.resolve("infra/migrations/auth.sql")
        Files.createDirectories(testFile.parent)
        Files.createDirectories(migration.parent)
        Files.writeString(testFile, "const token = 'test-token'\n<label>Password</label>\n")
        Files.writeString(migration, "create table auth_users (password text, token text);\n")

        val scanner = SourceSecretScanner()
        val testFindings = scanner.scan(root, listOf("src/auth.test.ts"))
        val migrationFindings = scanner.scan(root, listOf("infra/migrations/auth.sql"))

        assertEquals(SourceSecretClassification.PLACEHOLDER_TEST_VALUE, testFindings.single().classification)
        assertTrue(migrationFindings.isEmpty())
    }

    @Test
    fun detector_definition_does_not_flag_itself_and_actual_token_formats_do() {
        val root = Files.createTempDirectory("source-detector-")
        val detector = root.resolve("src/main/kotlin/atropos/core/security/KnownSecretRegistry.kt")
        val actual = root.resolve("src/main/kotlin/atropos/core/security/Leaked.kt")
        Files.createDirectories(detector.parent)
        Files.writeString(detector, "private val TOKEN_PATTERN = Regex(\"(?i)token\\\\s*[:=]\\\\s*value\")\n")
        Files.writeString(actual, "val bearer = \"Bearer abcdefghijklmnop\"\nval jwt = \"eyJabcdefgh.eyJabcdefgh.eyJabcdefgh\"\n")

        val scanner = SourceSecretScanner()

        assertTrue(scanner.scan(root, listOf("src/main/kotlin/atropos/core/security/KnownSecretRegistry.kt")).isEmpty())
        assertEquals(2, scanner.scan(root, listOf("src/main/kotlin/atropos/core/security/Leaked.kt")).size)
    }

    @Test
    fun deleted_paths_are_ignored_but_untracked_current_files_are_scanned() {
        val root = Files.createTempDirectory("source-paths-")
        val current = root.resolve("src/new.kt")
        Files.createDirectories(current.parent)
        Files.writeString(current, "val apiKey = 'sk-live-value-12345'\n")

        val findings = SourceSecretScanner().scan(
            root,
            listOf("apps/obsolete/auth-card.tsx", "src/new.kt")
        )

        assertTrue(findings.isNotEmpty())
        assertTrue(findings.all { it.path == "src/new.kt" })
        assertTrue(findings.any { it.ruleId == "api-key" })
    }
}
