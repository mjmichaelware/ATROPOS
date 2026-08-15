/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

object AdmissionController {
    private val immutableKeys = setOf(
        "sourceAuthorityIsImmutable",
        "executionGraphMustBeAcyclic",
        "implementationRequiresVerification"
    )

    fun validateConfigUpdate(updatedConfig: Map<String, Any>): Boolean {
        for (key in immutableKeys) {
            val value = updatedConfig[key]
            if (value != null && value is Boolean && !value) {
                return false // attempt to disable core invariant
            }
        }
        return true
    }
}
