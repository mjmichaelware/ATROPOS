package atropos.core.provider.adapter

data class AdapterFixtureResult(
    val providerId: String,
    val fixture: String,
    val passed: Boolean,
    val detail: String
)
