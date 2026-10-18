/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

/**
 * Android client contract binding for AtroposConfig.
 */
data class AtroposConfig(val rawConfig: String = "") {
    companion object {
        fun load(): AtroposConfig = AtroposConfig()
    }
}
