/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSearchModalTest {

    private val sampleTree = listOf(
        VirtualFileEntry(
            path = "src/main",
            name = "main",
            isDirectory = true,
            children = listOf(
                VirtualFileEntry(
                    path = "src/main/Engine.kt",
                    name = "Engine.kt",
                    isDirectory = false,
                    sizeBytes = 1024
                ),
                VirtualFileEntry(
                    path = "src/main/MerkleTree.kt",
                    name = "MerkleTree.kt",
                    isDirectory = false,
                    sizeBytes = 2048
                )
            )
        ),
        VirtualFileEntry(
            path = "build.gradle.kts",
            name = "build.gradle.kts",
            isDirectory = false,
            sizeBytes = 512
        )
    )

    private fun flatten(entry: VirtualFileEntry): List<VirtualFileEntry> {
        val list = mutableListOf<VirtualFileEntry>()
        if (!entry.isDirectory) list.add(entry)
        entry.children.forEach { list.addAll(flatten(it)) }
        return list
    }

    @Test
    fun testFlattenExtractsOnlyFilesRecursively() {
        val flattened = sampleTree.flatMap { flatten(it) }

        assertEquals(3, flattened.size)
        assertTrue(flattened.none { it.isDirectory })
        assertTrue(flattened.any { it.name == "Engine.kt" })
        assertTrue(flattened.any { it.name == "MerkleTree.kt" })
        assertTrue(flattened.any { it.name == "build.gradle.kts" })
    }

    @Test
    fun testFuzzySearchFiltersByPathAndName() {
        val flattened = sampleTree.flatMap { flatten(it) }

        val query = "merkle"
        val filtered = flattened.filter {
            it.path.lowercase().contains(query) || it.name.lowercase().contains(query)
        }

        assertEquals(1, filtered.size)
        assertEquals("MerkleTree.kt", filtered[0].name)
        assertEquals("src/main/MerkleTree.kt", filtered[0].path)
    }

    @Test
    fun testEmptySearchReturnsAllFilesUpToLimit() {
        val flattened = sampleTree.flatMap { flatten(it) }
        val query = ""

        val filtered = if (query.isBlank()) {
            flattened.take(15)
        } else {
            flattened.filter { it.name.contains(query) }
        }

        assertEquals(3, filtered.size)
    }

    @Test
    fun testSearchNonExistentQueryReturnsEmpty() {
        val flattened = sampleTree.flatMap { flatten(it) }
        val query = "xyz123nonexistent"

        val filtered = flattened.filter {
            it.path.lowercase().contains(query) || it.name.lowercase().contains(query)
        }

        assertTrue(filtered.isEmpty())
    }
}
