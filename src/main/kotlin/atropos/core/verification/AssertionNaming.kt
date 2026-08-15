// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.verification

object NamedAssertion {
    fun require(condition: Boolean, invariantName: String, observedValue: Any?) {
        if (!condition) {
            throw IllegalArgumentException("Invariant failed: [$invariantName]. Observed: $observedValue")
        }
    }
}
