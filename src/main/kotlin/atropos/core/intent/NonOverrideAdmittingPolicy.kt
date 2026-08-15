/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.intent

data class AdmissionRequest(
    val atomId: String,
    val operatorOverride: Boolean,
    val payloadSize: Int
)

sealed class AdmissionResult {
    object Admitted : AdmissionResult()
    data class Denied(val reason: String) : AdmissionResult()
}

object NonOverrideAdmittingPolicy {
    fun evaluate(request: AdmissionRequest): AdmissionResult {
        if (request.operatorOverride) {
            return AdmissionResult.Denied("SD5#B02 violation: Operator overrides are strictly prohibited for atom ${request.atomId}.")
        }
        if (request.payloadSize > 1024 * 1024) {
            return AdmissionResult.Denied("SD5#B01 violation: Payload exceeds admitting capacity.")
        }
        return AdmissionResult.Admitted
    }
}
