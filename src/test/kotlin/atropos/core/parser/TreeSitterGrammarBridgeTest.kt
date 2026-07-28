package atropos.core.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeSitterGrammarBridgeTest {
    private val bridge = TreeSitterGrammarBridge()

    @Test
    fun parse_tree_extracts_package_imports_and_declarations_with_offsets() {
        val code = """
            package sample.demo

            import java.io.File
            import sample.lib.Helper

            class Example
            object Singleton
            interface Contract
            val topLevel = 1
            fun runTask(input: String) = input.length
        """.trimIndent()

        val tree = bridge.parseTree(code)

        assertEquals("sample.demo", tree.packageName)
        assertEquals(listOf("java.io.File", "sample.lib.Helper"), tree.imports)
        assertEquals(
            listOf(
                KotlinDeclarationKind.CLASS,
                KotlinDeclarationKind.OBJECT,
                KotlinDeclarationKind.INTERFACE,
                KotlinDeclarationKind.PROPERTY,
                KotlinDeclarationKind.FUNCTION
            ),
            tree.declarations.map { it.kind }
        )
        assertEquals("Example", tree.declarations[0].name)
        assertEquals("Singleton", tree.declarations[1].name)
        assertEquals("Contract", tree.declarations[2].name)
        assertEquals("topLevel", tree.declarations[3].name)
        assertEquals("runTask", tree.declarations[4].name)
        assertTrue(tree.declarations.all { it.line > 0 })
        assertTrue(tree.declarations.all { it.column > 0 })
        assertTrue(tree.declarations.all { it.offset >= 0 })
    }

    @Test
    fun parse_tree_handles_modifiers_type_parameters_and_multiple_declarations_per_line() {
        val code = """
                package sample.modifiers
                import kotlin.collections.List

                data class Box<T>(val item: T)
                sealed interface Event
                private object Holder { const val ID = "x"; fun <T> map(input: T) = input }
                fun String.asBox() = Box(this)
        """.trimIndent()

        val tree = bridge.parseTree(code)

        assertEquals("sample.modifiers", tree.packageName)
        assertTrue(tree.declarations.any { it.kind == KotlinDeclarationKind.CLASS && it.name == "Box" })
        assertTrue(tree.declarations.any { it.kind == KotlinDeclarationKind.INTERFACE && it.name == "Event" })
        assertTrue(tree.declarations.any { it.kind == KotlinDeclarationKind.OBJECT && it.name == "Holder" })
        assertTrue(tree.declarations.any { it.kind == KotlinDeclarationKind.PROPERTY && it.name == "ID" })
        assertTrue(tree.declarations.any { it.kind == KotlinDeclarationKind.FUNCTION && it.name == "map" })
        assertTrue(tree.declarations.any { it.kind == KotlinDeclarationKind.FUNCTION && it.name == "asBox" })
    }
}
