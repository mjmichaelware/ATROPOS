/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.parser

import kotlin.test.*

class TreeSitterGrammarBridgeExtendedTest {

    @Test
    fun `test deeper nested structures`() {
        val source = """
            package test.pkg
            class Outer {
                class Inner {
                    fun deepMethod() {}
                }
            }
        """.trimIndent()
        
        val bridge = TreeSitterGrammarBridge()
        val tree = bridge.parseTree(source)
        
        assertFalse(tree.fullAstDepthLimitReached)
        val deepMethod = tree.declarations.find { it.name == "deepMethod" }
        assertEquals(listOf("Outer", "Inner"), deepMethod?.scope)
        assertEquals(2, deepMethod?.bodyDepth)
    }

    @Test
    fun `empty and malformed sources produce parse errors instead of success`() {
        val bridge = TreeSitterGrammarBridge()

        assertTrue(bridge.parseTree("").parseErrors.contains("empty_source"))
        assertTrue(bridge.parseTree("class Broken {").parseErrors.isNotEmpty())
    }
}
