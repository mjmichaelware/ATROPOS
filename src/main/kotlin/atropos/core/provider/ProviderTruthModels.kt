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

    fun renderInventory(): String = buildString {
        appendLine("providers inventory:")
        appendLine("  selected provider: $selectedProvider")
        appendLine("  descriptors: $descriptorCount")
        appendLine("  adapters: $adapterCount")
        appendLine("  executable providers: $executableProviderCount")
        appendLine("  healthy providers: ${records.count { it.health == ProviderAvailabilityState.READY }}")
        appendLine("  ask candidates: ${askOrder.joinToString(" -> ").ifBlank { "none" }}")
        appendLine("  patch candidates: ${patchOrder.joinToString(" -> ").ifBlank { "none" }}")
        appendLine("  last actual provider: ${lastActualProvider ?: "none"}")
        appendLine("  paid lock: ${if (paidAutomaticModeLocked) "locked" else "unlocked"}")
        records.forEach { record ->
            appendLine(
                "  ${record.id}: category=${record.category} cost=${record.costMode.name.lowercase()} " +
                    "key=${present(record.keyPresent)} descriptor=${present(record.descriptorPresent)} " +
                    "adapter=${present(record.adapterPresent)} executable=${present(record.executableSupport)} " +
                    "health=${record.health.name.lowercase()} ask=${present(record.askEligible)} " +
                    "patch=${present(record.patchEligible)} paid=${if (record.paidLocked) "locked" else "free"} " +
                    "missing=${record.missingRequirements.joinToString(",").ifBlank { "none" }}"
            )
        }
    }.trimEnd()

    private fun present(value: Boolean): String = if (value) "yes" else "no"
}
