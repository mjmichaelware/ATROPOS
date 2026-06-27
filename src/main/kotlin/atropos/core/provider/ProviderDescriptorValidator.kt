package atropos.core.provider

data class ValidationViolation(val id: String, val message: String)

class ProviderDescriptorValidator(private val registry: ProviderDescriptorRegistry) {
    fun validate(): List<ValidationViolation> {
        val all = registry.getAll()
        val ids = all.map { it.id }.toSet()
        val out = mutableListOf<ValidationViolation>()
        all.forEach { d ->
            if (d.id.isBlank()) out += ValidationViolation(d.id, "blank id")
            if (d.quotaTier !in 0..10) out += ValidationViolation(d.id, "invalid quota tier")
            if (d.capabilities.isEmpty()) out += ValidationViolation(d.id, "missing capabilities")
            if (!d.isLocal && d.requiredEnv.isEmpty()) out += ValidationViolation(d.id, "missing env vars")
            d.fallbackChain.forEach { fb ->
                if (fb !in ids) out += ValidationViolation(d.id, "missing fallback $fb")
                if (fb == d.id) out += ValidationViolation(d.id, "self fallback")
            }
        }
        all.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            out += ValidationViolation(it, "duplicate id")
        }
        return out
    }
    fun isValid() = validate().isEmpty()
}
