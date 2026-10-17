/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.cloud

/**
 * SD7-T-002: Sealed deployment result hierarchy.
 * Mathematical Invariant: False-Green Tolerance = 0.0000%
 * Forces callers to handle Success(url) vs Failed(error). Nullable URL prohibited.
 */
sealed class DeploymentResult {
    data class Success(val url: String) : DeploymentResult()
    data class Failed(val error: String) : DeploymentResult()
}
