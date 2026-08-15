// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.provider

import java.time.Instant

sealed class ProviderFailureState {
    data class ExhaustedUntilReset(val resetAt: Instant) : ProviderFailureState()
    object BillingRequired : ProviderFailureState()
    object AuthFailed : ProviderFailureState()
    data class ModelMissing(val alternateModel: String?) : ProviderFailureState()
    data class Unknown(val code: String) : ProviderFailureState()

    companion object {
        fun fromErrorCode(code: String, resetAt: Instant? = null, alternateModel: String? = null): ProviderFailureState {
            return when (code) {
                "429" -> ExhaustedUntilReset(resetAt ?: Instant.now().plusSeconds(3600))
                "402" -> BillingRequired
                "401", "403" -> AuthFailed
                "404" -> ModelMissing(alternateModel)
                else -> Unknown(code)
            }
        }
        
        fun fromErrorCode(code: Int, resetAt: Instant? = null, alternateModel: String? = null): ProviderFailureState {
            return fromErrorCode(code.toString(), resetAt, alternateModel)
        }
    }
}
