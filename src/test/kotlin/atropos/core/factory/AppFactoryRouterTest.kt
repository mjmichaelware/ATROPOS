package atropos.core.factory

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppFactoryRouterTest {
    @Test
    fun plans_arbitrary_app_identity_without_classifier_names() {
        val root = Files.createTempDirectory("atropos-factory-plan-")
        val plan = AppFactoryRouter(repoRoot = root).plan(
            "Create a weather service for commuters with tests"
        )

        assertEquals("weather", plan.projectSpec.intent.name)
        assertEquals("service", plan.projectSpec.intent.kind)
        assertTrue(plan.planningDagId == null)
        assertTrue(plan.steps.any { it.kind == FactoryStepKind.CODE })
        assertTrue(plan.steps.any { it.kind == FactoryStepKind.VALIDATE })
    }

    @Test
    fun isSourceTextFile_excludes_pycache_and_binary_extensions() {
        val root = Files.createTempDirectory("atropos-factory-src-")
        val router = AppFactoryRouter(repoRoot = root)
        val target = root.resolve("project")
        Files.createDirectories(target.resolve("pkg/__pycache__"))
        val pyc = target.resolve("pkg/__pycache__/module.cpython-314.pyc")
        val py = target.resolve("pkg/module.py")
        val wal = target.resolve("data.wal")
        Files.write(pyc, byteArrayOf(0x8c.toByte(), 0xf3.toByte()))
        Files.writeString(py, "print('hello')")
        Files.write(wal, byteArrayOf(0x00, 0x01))

        assertTrue(!router.isSourceTextFile(target, pyc))
        assertTrue(!router.isSourceTextFile(target, wal))
        assertTrue(router.isSourceTextFile(target, py))
    }
}
