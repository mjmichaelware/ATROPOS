package atropos.core.factory

data class FactoryStep(
    val kind: FactoryStepKind,
    val route: String,
    val localFirst: Boolean,
    val description: String
)
