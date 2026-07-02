package atropos.core.provider

import java.io.File

data class ProviderQuotaRecord(
    val providerId: String,
    val costMode: CostMode,
    val quotaWeight: Int,
    val configured: Boolean = false,
    val verified: Boolean = false,
    val state: ProviderAvailabilityState = ProviderAvailabilityState.UNKNOWN,
    val usedRequests: Int = 0,
    val usedTokens: Int = 0,
    val resetAtEpochMs: Long? = null,
    val cooldownUntilEpochMs: Long? = null,
    val lastErrorClass: String? = null,
    val lastErrorSummary: String? = null,
    val latencyMsAvg: Long? = null,
    val successScore: Double = 0.0,
    val paidLocked: Boolean = costMode == CostMode.PAID_LOCKED
) {
    fun availableAt(nowEpochMs: Long): Boolean =
        when (state) {
            ProviderAvailabilityState.READY,
            ProviderAvailabilityState.DEGRADED,
            ProviderAvailabilityState.UNKNOWN -> true
            ProviderAvailabilityState.COOLDOWN -> cooldownUntilEpochMs?.let { it <= nowEpochMs } ?: false
            ProviderAvailabilityState.EXHAUSTED_UNTIL_RESET -> resetAtEpochMs?.let { it <= nowEpochMs } ?: false
            ProviderAvailabilityState.AUTH_FAILED,
            ProviderAvailabilityState.BILLING_REQUIRED,
            ProviderAvailabilityState.OFFLINE,
            ProviderAvailabilityState.DISABLED -> false
        }
}

interface QuotaLedger {
    fun get(providerId: String): ProviderQuotaRecord?
    fun put(record: ProviderQuotaRecord)
    fun all(): List<ProviderQuotaRecord>
    fun recordSuccess(providerId: String, usage: ProviderUsage, nowEpochMs: Long = System.currentTimeMillis())
    fun recordFailure(providerId: String, failure: ProviderFailure, nowEpochMs: Long = System.currentTimeMillis())
}

class InMemoryQuotaLedger(seed: List<ProviderQuotaRecord> = emptyList()) : QuotaLedger {
    private val records = linkedMapOf<String, ProviderQuotaRecord>()
    init { seed.forEach { put(it) } }
    override fun get(providerId: String) = records[providerId]
    override fun put(record: ProviderQuotaRecord) { records[record.providerId] = record }
    override fun all() = records.values.toList()
    override fun recordSuccess(providerId: String, usage: ProviderUsage, nowEpochMs: Long) {
        val current = records[providerId] ?: return
        val oldAvg = current.latencyMsAvg ?: usage.latencyMs
        records[providerId] = current.copy(
            verified = true,
            state = ProviderAvailabilityState.READY,
            usedRequests = current.usedRequests + 1,
            usedTokens = current.usedTokens + usage.inputTokens + usage.outputTokens,
            latencyMsAvg = ((oldAvg + usage.latencyMs) / 2).coerceAtLeast(0),
            successScore = (current.successScore + 0.1).coerceAtMost(1.0),
            lastErrorClass = null,
            lastErrorSummary = null
        )
    }
    override fun recordFailure(providerId: String, failure: ProviderFailure, nowEpochMs: Long) {
        val current = records[providerId] ?: return
        val nextState = when (failure.type) {
            NormalizedProviderFailureType.AUTH_FAILED -> ProviderAvailabilityState.AUTH_FAILED
            NormalizedProviderFailureType.BILLING_REQUIRED -> ProviderAvailabilityState.BILLING_REQUIRED
            NormalizedProviderFailureType.RATE_LIMITED -> ProviderAvailabilityState.COOLDOWN
            NormalizedProviderFailureType.QUOTA_EXHAUSTED -> ProviderAvailabilityState.EXHAUSTED_UNTIL_RESET
            NormalizedProviderFailureType.TIMEOUT,
            NormalizedProviderFailureType.UNAVAILABLE -> ProviderAvailabilityState.COOLDOWN
            NormalizedProviderFailureType.MODEL_MISSING -> ProviderAvailabilityState.DEGRADED
            else -> ProviderAvailabilityState.DEGRADED
        }
        records[providerId] = current.copy(
            state = nextState,
            cooldownUntilEpochMs = failure.retryAfterMs?.let { nowEpochMs + it },
            resetAtEpochMs = failure.resetAtEpochMs,
            lastErrorClass = failure.type.name.lowercase(),
            lastErrorSummary = ProviderRedactor.redact(failure.cleanSummary),
            successScore = (current.successScore - 0.15).coerceAtLeast(0.0)
        )
    }
}

class FileQuotaLedger(private val file: File, seed: List<ProviderQuotaRecord> = emptyList()) : QuotaLedger {
    private val memory = InMemoryQuotaLedger(load(file).ifEmpty { seed })
    init { persist() }
    override fun get(providerId: String) = memory.get(providerId)
    override fun put(record: ProviderQuotaRecord) { memory.put(record); persist() }
    override fun all() = memory.all()
    override fun recordSuccess(providerId: String, usage: ProviderUsage, nowEpochMs: Long) { memory.recordSuccess(providerId, usage, nowEpochMs); persist() }
    override fun recordFailure(providerId: String, failure: ProviderFailure, nowEpochMs: Long) { memory.recordFailure(providerId, failure, nowEpochMs); persist() }
    private fun persist() {
        file.parentFile?.mkdirs()
        file.writeText(memory.all().joinToString("\n") { r ->
            listOf(r.providerId, r.costMode.name, r.quotaWeight, r.configured, r.verified, r.state.name, r.usedRequests, r.usedTokens, r.cooldownUntilEpochMs ?: "", r.resetAtEpochMs ?: "", r.lastErrorClass ?: "", r.lastErrorSummary ?: "", r.successScore, r.paidLocked).joinToString("\t")
        } + "\n")
    }
    companion object {
        fun seedFromDescriptors(registry: ProviderDescriptorRegistry) =
            registry.getAll().map { d ->
                ProviderQuotaRecord(
                    providerId = d.id,
                    costMode = d.costMode,
                    quotaWeight = d.quotaTier,
                    configured = d.isLocal,
                    verified = d.isLocal,
                    state = if (d.isLocal) ProviderAvailabilityState.READY else ProviderAvailabilityState.UNKNOWN,
                    paidLocked = d.isPaidLocked()
                )
            }
        private fun load(file: File): List<ProviderQuotaRecord> {
            if (!file.exists()) return emptyList()
            return file.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
                val p = line.split("\t")
                if (p.size < 14) return@mapNotNull null
                runCatching {
                    ProviderQuotaRecord(
                        p[0], CostMode.valueOf(p[1]), p[2].toInt(), p[3].toBoolean(), p[4].toBoolean(),
                        ProviderAvailabilityState.valueOf(p[5]), p[6].toInt(), p[7].toInt(),
                        p[9].toLongOrNull(), p[8].toLongOrNull(), p[10].ifBlank { null },
                        p[11].ifBlank { null }, null, p[12].toDoubleOrNull() ?: 0.0, p[13].toBoolean()
                    )
                }.getOrNull()
            }
        }
    }
}


data class QuotaBackupResult(
    val file: File,
    val records: Int
)

class QuotaLedgerBackup(
    private val registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val root: File = File(".atropos/quota-backups")
) {
    fun backup(target: File = defaultBackupFile()): QuotaBackupResult {
        target.parentFile?.mkdirs()
        val rows = FileQuotaLedger.seedFromDescriptors(registry)
        target.writeText(
            rows.joinToString("\n") { record ->
                listOf(
                    record.providerId,
                    record.costMode.name,
                    record.quotaWeight,
                    record.configured,
                    record.verified,
                    record.state.name,
                    record.usedRequests,
                    record.usedTokens,
                    record.cooldownUntilEpochMs ?: "",
                    record.resetAtEpochMs ?: "",
                    record.lastErrorClass ?: "",
                    record.lastErrorSummary ?: "",
                    record.successScore,
                    record.paidLocked
                ).joinToString("\t")
            } + "\n"
        )
        return QuotaBackupResult(target, rows.size)
    }

    fun restore(source: File): QuotaBackupResult {
        require(source.isFile) { "quota backup not found: ${source.path}" }
        val records = source.readLines().count { it.isNotBlank() }
        val restored = File(root, "restored-quota-ledger.tsv")
        restored.parentFile?.mkdirs()
        restored.writeText(source.readText())
        return QuotaBackupResult(restored, records)
    }

    private fun defaultBackupFile(): File =
        File(root, "quota-ledger-${System.currentTimeMillis()}.tsv")
}
