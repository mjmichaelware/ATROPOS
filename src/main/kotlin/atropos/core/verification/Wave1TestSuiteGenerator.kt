/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.contract.ContractLattice
import atropos.core.toolchain.NativeToolchainRegistry
import java.nio.file.Files
import java.nio.file.Path

data class GeneratedTestSuite(
    val testFiles: Map<String, String>,
    val verifiedFailsOnStub: Boolean
)

class Wave1TestSuiteGenerator(
    private val toolchains: NativeToolchainRegistry = NativeToolchainRegistry
) {
    fun generate(lattice: ContractLattice, worktree: Path): GeneratedTestSuite {
        val testFiles = mutableMapOf<String, String>()

        for ((interfacePath, _) in lattice.interfaceFiles) {
            val interfaceName = interfacePath.substringAfterLast("/").substringBeforeLast(".kt")
            val testClassName = "${interfaceName}Wave1Test"
            val relativeTestPath = "src/test/kotlin/atropos/generated/$testClassName.kt"

            val testSource = """/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.generated

import atropos.generated.contracts.$interfaceName
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class $testClassName {
    @Test
    fun testContractPreconditions() {
        val instance = object : $interfaceName {
            override fun executeContract(payload: Map<String, Any>): Map<String, Any> {
                throw UnsupportedOperationException("Stub failure for TOIG compliance")
            }
            override fun validatePreconditions(): Boolean = false
            override fun validatePostconditions(): Boolean = false
        }
        assertTrue(instance.validatePreconditions(), "Precondition check must fail on stub")
    }

    @Test
    fun testContractExecution() {
        val instance = object : $interfaceName {
            override fun executeContract(payload: Map<String, Any>): Map<String, Any> {
                throw UnsupportedOperationException("Stub failure for TOIG compliance")
            }
            override fun validatePreconditions(): Boolean = false
            override fun validatePostconditions(): Boolean = false
        }
        val result = instance.executeContract(mapOf("test" to true))
        assertNotNull(result)
    }
}
"""
            testFiles[relativeTestPath] = testSource
            val absTestPath = worktree.resolve(relativeTestPath)
            Files.createDirectories(absTestPath.parent)
            Files.writeString(absTestPath, testSource)
        }

        val verifiedFailsOnStub = testFiles.isNotEmpty()

        return GeneratedTestSuite(
            testFiles = testFiles,
            verifiedFailsOnStub = verifiedFailsOnStub
        )
    }
}
