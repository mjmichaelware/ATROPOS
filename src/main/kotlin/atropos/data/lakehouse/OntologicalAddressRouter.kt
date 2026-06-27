/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.lakehouse

data class DloiCoordinate(
    val domain: Int,
    val category: Int,
    val leaf: Int
)

class OntologicalAddressRouter {
    fun resolve(code: String): DloiCoordinate? {
        val parts = code.trim().split(".")
        if (parts.size != 3) return null

        val domain = parts[0].toIntOrNull() ?: return null
        val category = parts[1].toIntOrNull() ?: return null
        val leaf = parts[2].toIntOrNull() ?: return null

        return DloiCoordinate(domain, category, leaf)
    }

    fun format(coordinate: DloiCoordinate): String {
        return "%03d.%03d.%03d".format(
            coordinate.domain,
            coordinate.category,
            coordinate.leaf
        )
    }
}
