/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.taxonomy

import atropos.cli.input.CommandEntry

/**
 * ATOM 6.3: Multi-Level Trie Navigation and Arrow-Key Hierarchy.
 *
 * Builds a multi-tier tree structure mapping domain to action to target.
 * Supports right-arrow and enter key descents, left-arrow ascents, and clean dismissal.
 *
 * ## Research: Compact Prefix Trie Data Compression
 * Research compact radix trie (PATRICIA tree) and double-array trie (DAT) compression
 * structures to index several thousand command definitions with sub-millisecond prefix lookup
 * latency and minimal memory footprint on constrained mobile runtime environments.
 */
class CommandTrieNavigator(
    val root: MutableMap<String, TaxonomyNode> = mutableMapOf()
) {
    data class TaxonomyNode(
        val name: String,
        val description: String = "",
        val children: MutableMap<String, TaxonomyNode> = mutableMapOf(),
        val commandEntry: CommandEntry? = null,
        val parent: TaxonomyNode? = null
    ) {
        val isLeaf: Boolean get() = children.isEmpty() || commandEntry != null
    }

    data class NavigationCursor(
        val path: List<String> = emptyList(),
        val selectedIndex: Int = 0
    )

    fun register(path: List<String>, entry: CommandEntry, description: String = entry.description) {
        if (path.isEmpty()) return
        var currentMap = root
        var parentNode: TaxonomyNode? = null

        path.forEachIndexed { index, segment ->
            val isLast = index == path.size - 1
            val node = currentMap.getOrPut(segment) {
                TaxonomyNode(
                    name = segment,
                    description = if (isLast) description else "Subcategory $segment",
                    commandEntry = if (isLast) entry else null,
                    parent = parentNode
                )
            }
            parentNode = node
            currentMap = node.children
        }
    }

    fun stepInto(cursor: NavigationCursor): NavigationCursor {
        val currentChildren = getChildrenAt(cursor.path)
        if (currentChildren.isEmpty()) return cursor
        val selectedChild = currentChildren.getOrNull(cursor.selectedIndex) ?: return cursor
        return NavigationCursor(path = cursor.path + selectedChild.name, selectedIndex = 0)
    }

    fun descend(cursor: NavigationCursor): NavigationCursor = stepInto(cursor)

    fun ascend(cursor: NavigationCursor): NavigationCursor {
        if (cursor.path.isEmpty()) return cursor
        return NavigationCursor(path = cursor.path.dropLast(1), selectedIndex = 0)
    }

    fun canDescend(cursor: NavigationCursor): Boolean {
        val children = getChildrenAt(cursor.path)
        return children.isNotEmpty()
    }

    fun resolveLeaf(path: List<String>): TaxonomyNode? {
        var current: TaxonomyNode? = null
        var currentMap = root
        for (segment in path) {
            current = currentMap[segment] ?: return null
            currentMap = current.children
        }
        return current
    }

    fun cheatsheet(): Map<String, List<String>> {
        val sheet = mutableMapOf<String, MutableList<String>>()
        root.forEach { (domain, node) ->
            val list = sheet.getOrPut(domain) { mutableListOf() }
            collectLeafCommands(node, list)
        }
        return sheet
    }

    private fun getChildrenAt(path: List<String>): List<TaxonomyNode> {
        var currentMap = root
        for (segment in path) {
            val node = currentMap[segment] ?: return emptyList()
            currentMap = node.children
        }
        return currentMap.values.toList()
    }

    private fun collectLeafCommands(node: TaxonomyNode, destination: MutableList<String>) {
        if (node.commandEntry != null) {
            destination.add("${node.commandEntry.command} — ${node.commandEntry.description}")
        }
        node.children.values.forEach { collectLeafCommands(it, destination) }
    }
}
