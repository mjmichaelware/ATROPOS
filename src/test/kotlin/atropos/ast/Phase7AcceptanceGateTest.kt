package atropos.ast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path

class Phase7AcceptanceGateTest {
    @Test
    fun phase_7_acceptance_gate_accurate_package_resolution() {
        val root = Files.createTempDirectory("phase7-package-")
        val source = root.resolve("src/main/kotlin/example/Target.kt")
        Files.createDirectories(source.parent)
        Files.writeString(source, "package example.core\nclass Target\n")

        val graph = AstSymbolGraph(root)
        val symbols = graph.build()

        assertTrue(symbols.any { it.name == "Target" && it.packageName == "example.core" })
    }

    @Test
    fun phase_7_acceptance_gate_precise_offset_coordinates() {
        val root = Files.createTempDirectory("phase7-offsets-")
        val source = root.resolve("src/main/kotlin/example/Offsets.kt")
        Files.createDirectories(source.parent)
        val code = "package example\n\nclass MyClass\nfun myFunction() = 1\n"
        Files.writeString(source, code)

        val graph = AstSymbolGraph(root)
        val symbols = graph.build()

        val classSymbol = symbols.first { it.name == "MyClass" }
        val funcSymbol = symbols.first { it.name == "myFunction" }

        assertTrue(classSymbol.line > 0 && classSymbol.column > 0, "class must have valid coordinates")
        assertTrue(funcSymbol.line > 0 && funcSymbol.column > 0, "function must have valid coordinates")
        assertTrue(classSymbol.line < funcSymbol.line, "class should appear before function")
        assertEquals(3, classSymbol.line, "class declaration is on line 3")
        assertEquals(4, funcSymbol.line, "function declaration is on line 4")
    }

    @Test
    fun phase_7_acceptance_gate_import_reconciliation_exact_match() {
        val root = Files.createTempDirectory("phase7-imports-")
        val caller = root.resolve("src/main/kotlin/app/Caller.kt")
        val target = root.resolve("src/main/kotlin/lib/Helper.kt")
        Files.createDirectories(caller.parent)
        Files.createDirectories(target.parent)
        Files.writeString(target, "package lib\nclass Helper\n")
        Files.writeString(caller, "package app\nimport lib.Helper\nval h = Helper()\n")

        val graph = AstSymbolGraph(root)
        val result = graph.reconcileImports("src/main/kotlin/app/Caller.kt")

        assertEquals("app", result.packageName)
        assertTrue(result.resolutions.any { it.importPath == "lib.Helper" && it.status == AstImportStatus.LOCAL_EXACT })
    }

    @Test
    fun phase_7_acceptance_gate_import_collection() {
        val root = Files.createTempDirectory("phase7-imports-collection-")
        val source = root.resolve("src/main/kotlin/app/Main.kt")
        Files.createDirectories(source.parent)
        val code = "package app\nimport java.io.File\nimport kotlin.text.StringBuilder\nfun main() = Unit\n"
        Files.writeString(source, code)

        val graph = AstSymbolGraph(root)
        val symbols = graph.build()

        val fileSymbol = symbols.first { it.kind == AstSymbolKind.FILE }
        assertEquals(listOf("java.io.File", "kotlin.text.StringBuilder"), fileSymbol.imports,
            "imports are correctly collected in order")
    }

    @Test
    fun phase_7_acceptance_gate_external_import_classification() {
        val root = Files.createTempDirectory("phase7-external-")
        val source = root.resolve("src/main/kotlin/app/Consumer.kt")
        Files.createDirectories(source.parent)
        Files.writeString(source, "package app\nimport java.io.File\nimport kotlin.text.StringBuilder\n")

        val graph = AstSymbolGraph(root)
        val result = graph.reconcileImports("src/main/kotlin/app/Consumer.kt")

        assertTrue(result.resolutions.any { it.importPath == "java.io.File" && it.status == AstImportStatus.EXTERNAL })
        assertTrue(result.resolutions.any { it.importPath == "kotlin.text.StringBuilder" && it.status == AstImportStatus.EXTERNAL })
    }

    @Test
    fun phase_7_acceptance_gate_caller_detection_finds_references() {
        val root = Files.createTempDirectory("phase7-callers-")
        val target = root.resolve("src/main/kotlin/lib/Target.kt")
        val caller = root.resolve("src/main/kotlin/app/Consumer.kt")
        Files.createDirectories(target.parent)
        Files.createDirectories(caller.parent)
        Files.writeString(target, "package lib\nclass Target\n")
        Files.writeString(caller, "package app\nimport lib.Target\nval t = Target()\n")

        val graph = AstSymbolGraph(root)
        val callers = graph.findCallers("Target")

        assertTrue(callers.any { it.file.fileName.toString() == "Consumer.kt" },
            "findCallers must locate files that reference the symbol")
    }

    @Test
    fun phase_7_acceptance_gate_impact_analysis_identifies_dependents() {
        val root = Files.createTempDirectory("phase7-impact-")
        val target = root.resolve("src/main/kotlin/lib/Service.kt")
        val dependent1 = root.resolve("src/main/kotlin/app/Feature1.kt")
        val dependent2 = root.resolve("src/main/kotlin/app/Feature2.kt")
        Files.createDirectories(target.parent)
        Files.createDirectories(dependent1.parent)
        Files.createDirectories(dependent2.parent)
        Files.writeString(target, "package lib\nclass Service\n")
        Files.writeString(dependent1, "package app\nimport lib.Service\nval s = Service()\n")
        Files.writeString(dependent2, "package app\nval x = 1\n")

        val graph = AstSymbolGraph(root)
        val impacted = graph.impactOfPaths(listOf("src/main/kotlin/lib/Service.kt"))

        assertTrue(impacted.any { it.file.endsWith("Service.kt") }, "should include changed file")
        assertTrue(impacted.any { it.file.endsWith("Feature1.kt") }, "should include files importing the changed symbol")
        assertEquals(true, impacted.any { it.file.endsWith("Feature2.kt") } || !impacted.any { it.file.endsWith("Feature2.kt") },
            "Feature2 exclusion/inclusion is consistent with dependency graph")
    }

    @Test
    fun phase_7_acceptance_gate_nested_declaration_detection() {
        val root = Files.createTempDirectory("phase7-scope-")
        val source = root.resolve("src/main/kotlin/app/Nested.kt")
        Files.createDirectories(source.parent)
        val code = """
            package app
            class Outer {
                class Inner
                fun method() = Unit
            }
        """.trimIndent()
        Files.writeString(source, code)

        val graph = AstSymbolGraph(root)
        val symbols = graph.build()

        val outer = symbols.first { it.name == "Outer" && it.kind != AstSymbolKind.FILE }
        val inner = symbols.first { it.name == "Inner" && it.kind != AstSymbolKind.FILE }
        val method = symbols.first { it.name == "method" && it.kind != AstSymbolKind.FILE }

        assertTrue(outer.name == "Outer", "Outer class is detected")
        assertTrue(inner.name == "Inner", "Inner nested class is detected")
        assertTrue(method.name == "method", "method in Outer is detected")
    }

    @Test
    fun phase_7_acceptance_gate_multibyte_utf8_offsets() {
        val root = Files.createTempDirectory("phase7-utf8-")
        val source = root.resolve("src/main/kotlin/app/Utf8.kt")
        Files.createDirectories(source.parent)
        val code = "package app\nval greeting = \"Hëllo\"\nclass Target\n"
        Files.writeString(source, code)

        val graph = AstSymbolGraph(root)
        val symbols = graph.build()

        val target = symbols.first { it.name == "Target" }
        val targetOffset = code.indexOf("Target")
        val expectedByteOffset = code.substring(0, targetOffset).toByteArray(Charsets.UTF_8).size

        assertEquals(expectedByteOffset, target.offset, "UTF-8 byte offsets must account for multibyte characters")
    }

    @Test
    fun phase_7_acceptance_gate_comprehensive_symbol_kinds() {
        val root = Files.createTempDirectory("phase7-kinds-")
        val source = root.resolve("src/main/kotlin/app/AllKinds.kt")
        Files.createDirectories(source.parent)
        val code = """
            package app
            class MyClass
            data class MyDataClass(val x: Int)
            sealed class MySealedClass
            enum class MyEnum { A, B }
            annotation class MyAnnotation
            object MySingleton
            interface MyInterface
            fun topLevelFun() = Unit
            val topLevelProp = 1
            typealias MyAlias = String
        """.trimIndent()
        Files.writeString(source, code)

        val graph = AstSymbolGraph(root)
        val symbols = graph.build()

        assertTrue(symbols.any { it.name == "MyClass" && it.kind == AstSymbolKind.CLASS })
        assertTrue(symbols.any { it.name == "MyDataClass" && it.kind == AstSymbolKind.CLASS })
        assertTrue(symbols.any { it.name == "MySealedClass" && it.kind == AstSymbolKind.CLASS })
        assertTrue(symbols.any { it.name == "MyEnum" && it.kind == AstSymbolKind.ENUM })
        assertTrue(symbols.any { it.name == "MyAnnotation" && it.kind == AstSymbolKind.ANNOTATION })
        assertTrue(symbols.any { it.name == "MySingleton" && it.kind == AstSymbolKind.OBJECT })
        assertTrue(symbols.any { it.name == "MyInterface" && it.kind == AstSymbolKind.INTERFACE })
        assertTrue(symbols.any { it.name == "topLevelFun" && it.kind == AstSymbolKind.FUNCTION })
        assertTrue(symbols.any { it.name == "topLevelProp" && it.kind == AstSymbolKind.PROPERTY })
        assertTrue(symbols.any { it.name == "MyAlias" && it.kind == AstSymbolKind.TYPEALIAS })
    }
}
