package atropos.core.artifact

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SafeJarSwapGateTest {
    @Test
    fun refuses_to_promote_without_independent_verification_evidence() {
        val root = Files.createTempDirectory("atropos-jar-swap-no-evidence-")
        val candidate = root.resolve("candidate.jar")
        val target = root.resolve("atropos.jar")
        Files.writeString(candidate, "new jar")
        Files.writeString(target, "old jar")

        val result = SafeJarSwapGate().promote(candidate, target, emptyList())

        assertTrue(!result.promoted)
        assertTrue(result.message.contains("verification_evidence"), result.message)
        assertEquals("old jar", Files.readString(target))
    }

    @Test
    fun refuses_to_promote_when_verification_evidence_failed() {
        val root = Files.createTempDirectory("atropos-jar-swap-refuse-")
        val candidate = root.resolve("candidate.jar")
        val target = root.resolve("atropos.jar")
        Files.writeString(candidate, "new jar")
        Files.writeString(target, "old jar")
        val gate = SafeJarSwapGate(clock = { Instant.parse("2026-07-27T10:00:00Z") })

        val result = gate.promote(
            candidate,
            target,
            listOf(JarSwapEvidence(false, "compile", "compile failed"))
        )

        assertTrue(!result.promoted)
        assertTrue(result.message.contains("refused"), result.message)
        assertEquals("old jar", Files.readString(target))
    }

    @Test
    fun promotes_verified_candidate_and_preserves_previous_jar() {
        val root = Files.createTempDirectory("atropos-jar-swap-promote-")
        val candidate = root.resolve("candidate.jar")
        val target = root.resolve("atropos.jar")
        Files.writeString(candidate, "new jar")
        Files.writeString(target, "old jar")
        val gate = SafeJarSwapGate(clock = { Instant.parse("2026-07-27T10:05:00Z") })

        val result = gate.promote(
            candidate,
            target,
            listOf(
                JarSwapEvidence(true, "compile", "compile passed"),
                JarSwapEvidence(true, "smoke", "jar smoke passed")
            )
        )

        assertTrue(result.promoted, result.message)
        assertEquals("new jar", Files.readString(target))
        val backup = result.backupJar ?: error("missing backup")
        assertEquals("old jar", Files.readString(backup))
        assertTrue(result.evidence.any { it.kind == "candidate_exists" && it.passed })
        assertTrue(result.evidence.any { it.kind == "candidate_sha256" && it.passed })
        assertTrue(result.evidence.any { it.kind == "backup_sha256" && it.passed })
        assertTrue(result.evidence.any { it.kind == "target_sha256" && it.passed })
    }

    @Test
    fun successful_promotion_reports_a_real_non_empty_backup_and_target() {
        val root = Files.createTempDirectory("atropos-jar-swap-postconditions-")
        val candidate = root.resolve("candidate.jar")
        val target = root.resolve("atropos.jar")
        Files.writeString(candidate, "new jar bytes")
        Files.writeString(target, "old jar bytes")

        val result = SafeJarSwapGate(clock = { Instant.parse("2026-07-27T10:06:00Z") })
            .promote(candidate, target, listOf(JarSwapEvidence(true, "compile", "compile passed")))

        val backup = result.backupJar ?: error("missing backup")
        assertTrue(result.promoted)
        assertTrue(Files.size(target) > 0L)
        assertTrue(Files.isRegularFile(backup))
        assertTrue(Files.size(backup) > 0L)
    }

    @Test
    fun backup_creation_failure_returns_refusal_and_preserves_previous_jar() {
        val root = Files.createTempDirectory("atropos-jar-swap-backup-failure-")
        val candidate = root.resolve("candidate.jar")
        val target = root.resolve("atropos.jar")
        Files.writeString(candidate, "new jar")
        Files.writeString(target, "old jar")
        val backupPath = root.resolve("atropos.jar.backup-1000")
        Files.createDirectory(backupPath)

        val result = SafeJarSwapGate(clock = { Instant.ofEpochMilli(1000) }).promote(
            candidate,
            target,
            listOf(JarSwapEvidence(true, "compile", "compile passed"))
        )

        assertTrue(!result.promoted)
        assertTrue(result.message.contains("jar promote failed"), result.message)
        assertEquals("old jar", Files.readString(target))
        assertTrue(result.evidence.any { !it.passed && it.kind == "promote_copy" })
    }
}
