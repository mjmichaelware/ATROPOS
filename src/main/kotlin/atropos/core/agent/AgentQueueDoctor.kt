package atropos.core.agent

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Comparator

data class AgentQueueDoctorCheck(
    val name: String,
    val passed: Boolean,
    val detail: String
)

data class AgentQueueDoctorResult(
    val checks: List<AgentQueueDoctorCheck>,
    val fixtureRemoved: Boolean
) {
    val passed: Boolean
        get() = checks.all { it.passed } && fixtureRemoved

    fun render(): String = buildString {
        checks.forEach { check ->
            appendLine("${check.name}: ${if (check.passed) "PASS" else "FAIL"} - ${check.detail}")
        }
        appendLine("temporary fixtures removed: ${if (fixtureRemoved) "PASS" else "FAIL"}")
        appendLine("queue doctor: ${if (passed) "PASS" else "FAIL"}")
    }.trimEnd()
}

class AgentQueueDoctor {
    fun run(): AgentQueueDoctorResult {
        val checks = mutableListOf<AgentQueueDoctorCheck>()
        val fixture = Files.createTempDirectory("atropos-queue-doctor-")
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val store = AgentQueueStore(
            repoRoot = fixture,
            clock = { now },
            queueRootOverride = fixture.resolve("queue")
        )
        val recovery = AgentQueueRecovery(store, clock = { now })

        try {
            val persistent = store.createEntry("doctor persistence", null)
            val reopened = AgentQueueStore(fixture, clock = { now }, queueRootOverride = fixture.resolve("queue"))
            checks += check("persistence", reopened.resolve(persistent.id)?.task == "doctor persistence", persistent.id)

            val rewritten = store.update(
                persistent.copy(failureReason = "rewritten"),
                eventType = "doctor_rewrite",
                previousState = persistent.state,
                message = "atomic rewrite"
            )
            checks += check("atomic rewrite", store.resolve(rewritten.id)?.failureReason == "rewritten", rewritten.id)

            val firstLease = store.acquireLease(rewritten.id, "owner-one", 60).record
            checks += check("first lease acquisition", firstLease?.lease?.owner == "owner-one", firstLease?.id ?: "none")

            val secondLease = store.acquireLease(rewritten.id, "owner-two", 60)
            checks += check(
                "second-owner live lease refusal",
                secondLease.record == null && secondLease.refusalReason?.contains("live lease") == true,
                secondLease.refusalReason ?: "none"
            )

            now = now.plusSeconds(61)
            val stale = store.resolve(rewritten.id)
            checks += check(
                "stale lease detection",
                stale != null && AgentQueueTransitions.isRecoverableLease(stale, now),
                stale?.lease?.expiresAt?.toString() ?: "none"
            )

            val recovered = recovery.recover()
            val recoveredRecord = store.resolve(rewritten.id)
            checks += check(
                "stale recovery",
                recovered.transitions.size == 1 && recoveredRecord?.state == AgentQueueState.QUEUED,
                recovered.render()
            )

            val secondRecovery = recovery.recover()
            val afterSecond = store.resolve(rewritten.id)
            checks += check(
                "second recovery idempotency",
                secondRecovery.transitions.isEmpty() && afterSecond?.recoveryCount == recoveredRecord?.recoveryCount,
                secondRecovery.render()
            )

            val exhausted = store.createEntry("doctor exhausted", null)
            val expiredLease = AgentQueueLease(
                token = "expired-token",
                owner = "owner-one",
                acquiredAt = now.minusSeconds(120),
                heartbeatAt = now.minusSeconds(120),
                expiresAt = now.minusSeconds(60)
            )
            store.update(
                exhausted.copy(
                    state = AgentQueueState.LEASED,
                    checkpoint = AgentQueueCheckpoint.CLAIMED,
                    attempts = exhausted.maxAttempts,
                    lease = expiredLease
                ),
                eventType = "doctor_expired",
                previousState = exhausted.state,
                message = "expired exhausted lease"
            )
            recovery.recover()
            checks += check(
                "retry exhaustion",
                store.resolve(exhausted.id)?.state == AgentQueueState.FAILED,
                store.resolve(exhausted.id)?.failureReason ?: "none"
            )

            val cancellable = store.createEntry("doctor cancel", null)
            val cancelled = store.cancel(cancellable, "doctor cancellation")
            checks += check("cancellation", cancelled.state == AgentQueueState.CANCELLED, cancelled.id)

            val terminal = store.createEntry(
                "doctor completed",
                null,
                state = AgentQueueState.COMPLETED,
                checkpoint = AgentQueueCheckpoint.FINALIZED,
                provider = "none"
            )
            recovery.recover()
            checks += check(
                "terminal preservation",
                store.resolve(terminal.id)?.state == AgentQueueState.COMPLETED,
                terminal.id
            )

            val checkpoint = store.createEntry("doctor checkpoint", null)
            store.update(
                checkpoint.copy(checkpoint = AgentQueueCheckpoint.VERIFIED),
                eventType = "doctor_checkpoint",
                previousState = checkpoint.state,
                message = "checkpoint round trip"
            )
            checks += check(
                "checkpoint round-trip",
                store.resolve(checkpoint.id)?.checkpoint == AgentQueueCheckpoint.VERIFIED,
                checkpoint.id
            )
        } catch (failure: Exception) {
            checks += AgentQueueDoctorCheck("doctor exception", false, failure.message ?: failure.javaClass.simpleName)
        }

        val removed = deleteFixture(fixture)
        return AgentQueueDoctorResult(checks = checks, fixtureRemoved = removed)
    }

    private fun check(name: String, passed: Boolean, detail: String): AgentQueueDoctorCheck =
        AgentQueueDoctorCheck(name = name, passed = passed, detail = detail.replace(Regex("\\s+"), " ").take(240))

    private fun deleteFixture(path: Path): Boolean =
        runCatching {
            if (!Files.exists(path)) return@runCatching
            Files.walk(path).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }.isSuccess && !Files.exists(path)
}
