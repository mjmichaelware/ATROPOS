/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.lang.AssertionError

/**
 * AppSourceTemplate has been extirpated.
 *
 * Zero template code emission is an invariant (§2 Complete mediation:
 * Cap(P_theta) intersect Cap(A_write) = emptyset).
 *
 * Any attempt to instantiate or reflectively access the erased class
 * throws AssertionError to fail fast and loud.
 */
object AppSourceTemplateBanAssertion {
    const val ERADICATED: Boolean = true

    init {
        throw AssertionError(
            "AppSourceTemplate extirpated. " +
            "All code synthesis originates from Wave-0 ContractLatticeSynthesizer. " +
            "Static template emission is forbidden by §2 Complete mediation invariant."
        )
    }

    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(vararg args: Any): Nothing = throw AssertionError(
        "AppSourceTemplate invocation forbidden. Use ContractLatticeSynthesizer."
    )
}

/**
 * Annotation to mark call sites that must be rewired to ContractLatticeSynthesizer.
 * ArchitectureComplianceChecker scans for this.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention(AnnotationRetention.SOURCE)
annotation class RequiresContractLatticeSynthesizer(
    val reason: String = "Template path eradicated; use Wave-0 lattice synthesis"
)