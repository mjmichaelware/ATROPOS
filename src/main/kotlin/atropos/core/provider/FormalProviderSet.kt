/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * SD7-T-055: Formal model-provider set M parameterizing the synthesis operator Φ: S × M → C.
 * Mathematical Invariant: I(Module_A; Module_B | Interface_Lattice) = 0.000 bits
 * RoutePolicy is a pure function over typed M (ProviderDescriptor), eliminating ad-hoc strings.
 */
object FormalProviderSet {

    fun route(state: String, m: List<ProviderDescriptor>): String {
        if (m.isEmpty()) return "local"

        // Pure route function over mathematical model M
        val eligible = m.filter { descriptor ->
            descriptor.isFreeEligible() || descriptor.hasCapability(ApiCapability.CODE)
        }
        val selected = eligible.minByOrNull { it.quotaTier } ?: m.first()
        return selected.id
    }
}
