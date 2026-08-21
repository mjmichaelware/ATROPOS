package atropos.core.provider

data class ProviderTruthRecord(
    val id: String,
    val category: String,
    val costMode: CostMode,
    val keyPresent: Boolean,
    val descriptorPresent: Boolean,
    val adapterPresent: Boolean,
    val executableSupport: Boolean,
    val health: ProviderAvailabilityState,
    val askEligible: Boolean,
    val patchEligible: Boolean,
    val paidLocked: Boolean,
    val missingRequirements: List<String>
)

data class ProviderTruthSnapshot(
    val selectedProvider: String,
    val records: List<ProviderTruthRecord>,
    val askOrder: List<String>,
    val patchOrder: List<String>,
    val lastActualProvider: String?,
    val paidAutomaticModeLocked: Boolean
) {
    val descriptorCount: Int get() = records.count { it.descriptorPresent }
    val adapterCount: Int get() = records.count { it.adapterPresent }
    val executableProviderCount: Int get() = records.count { it.executableSupport }

    /**
     * Dense by default, with details available on demand. The old rendering
     * repeated nine boolean fields on every row, so the useful facts were
     * pushed past the terminal edge and the active provider was hard to find.
     */
    fun renderInventory(expanded: Boolean = false): String = buildString {
        val healthy = records.count { it.health == ProviderAvailabilityState.READY }
        val grouped = records.groupBy { it.category }.toSortedMap()
        appendLine("PROVIDERS")
        appendLine("  active: $selectedProvider")
        appendLine(
            "  ${records.size} descriptors · $adapterCount adapters · " +
                "$executableProviderCount executable · $healthy healthy · " +
                "paid ${if (paidAutomaticModeLocked) "locked" else "available"}"
        )
        appendLine("  ask: ${askOrder.joinToString(" -> ").ifBlank { "none" }}")
        appendLine("  patch: ${patchOrder.joinToString(" -> ").ifBlank { "none" }}")
        lastActualProvider?.let { appendLine("  last actual: $it") }
        appendLine("")
        appendLine("  legend: > active  [READY] [NO-KEY] [OFFLINE] [LOCKED]  K key  A adapter  X executable  Q ask  P patch")
        grouped.forEach { (category, categoryRecords) ->
            appendLine("")
            appendLine(category.uppercase())
            categoryRecords.sortedWith(compareBy({ it.id != selectedProvider }, { it.id })).forEach { record ->
                appendLine("  ${compactRow(record)}")
                if (expanded) {
                    appendLine("      caps: ${recordCapabilities(record)}")
                    appendLine("      requirements: ${record.missingRequirements.joinToString(", ").ifBlank { "none" }}")
                }
            }
        }
        if (!expanded) appendLine("")
        appendLine(if (expanded) "  end of provider inventory" else "  details: /providers --full")
    }.trimEnd()

    private fun compactRow(record: ProviderTruthRecord): String {
        val active = if (record.id == selectedProvider) ">" else " "
        val badge = when {
            record.paidLocked -> "[LOCKED]"
            record.health == ProviderAvailabilityState.READY -> "[READY]"
            record.health == ProviderAvailabilityState.AUTH_FAILED -> "[NO-KEY]"
            record.health == ProviderAvailabilityState.OFFLINE -> "[OFFLINE]"
            else -> "[${record.health.name.replace('_', '-').uppercase()}]"
        }
        val flags = buildString {
            if (record.keyPresent) append("K")
            if (record.adapterPresent) append("A")
            if (record.executableSupport) append("X")
            if (record.askEligible) append("Q")
            if (record.patchEligible) append("P")
        }.ifBlank { "-" }
        return "$active ${record.id.padEnd(20)} ${badge.padEnd(10)} cost=${record.costMode.name.lowercase().padEnd(13)} q=${record.category.padEnd(8)} [$flags]"
    }

    private fun recordCapabilities(record: ProviderTruthRecord): String =
        listOf(
            "key=${present(record.keyPresent)}",
            "descriptor=${present(record.descriptorPresent)}",
            "adapter=${present(record.adapterPresent)}",
            "executable=${present(record.executableSupport)}",
            "ask=${present(record.askEligible)}",
            "patch=${present(record.patchEligible)}",
            "paid=${if (record.paidLocked) "locked" else "free"}"
        ).joinToString(" ")

    private fun present(value: Boolean): String = if (value) "yes" else "no"
}
