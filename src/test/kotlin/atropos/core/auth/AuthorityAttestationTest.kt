/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.auth

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `SUP.AUTH.HASH-ATTEST` and `SUP.AUTH.AGENTS-MD`, asserted at the boundary
 * that matters: a document whose bytes changed must not be believed.
 */
class AuthorityAttestationTest {

    private fun workspace(): Path = Files.createTempDirectory("atropos-auth-test")

    private fun attestorIn(root: Path): AuthorityAttestor =
        AuthorityAttestor(FingerprintStore(root.resolve(".atropos/fp.tsv")), root)

    @Test
    fun `first sight records a fingerprint and attests`() {
        val root = workspace()
        Files.writeString(root.resolve("AGENTS.md"), "territoryAtDispatch: required\n")

        val result = attestorIn(root).attest("AGENTS.md", 1)

        assertTrue(result is AttestationResult.Attested)
        assertTrue(result.document.sha256.isNotBlank())
    }

    @Test
    fun `unchanged bytes stay attested across loads`() {
        val root = workspace()
        Files.writeString(root.resolve("AGENTS.md"), "secretPolicy: vault\n")
        val attestor = attestorIn(root)

        attestor.attest("AGENTS.md", 1)
        val second = attestor.attest("AGENTS.md", 1)

        assertTrue(second is AttestationResult.Attested)
    }

    @Test
    fun `edited bytes are a mismatch, not a new baseline`() {
        val root = workspace()
        val file = root.resolve("AGENTS.md")
        Files.writeString(file, "boundedAgencyGate: on\n")
        val attestor = attestorIn(root)
        attestor.attest("AGENTS.md", 1)

        Files.writeString(file, "boundedAgencyGate: off\n")
        val result = attestor.attest("AGENTS.md", 1)

        assertTrue(result is AttestationResult.Mismatch)
        assertTrue(result.reason().contains("changed since it was recorded"))
    }

    @Test
    fun `explicit acceptance re-establishes the baseline`() {
        val root = workspace()
        val file = root.resolve("AGENTS.md")
        Files.writeString(file, "a: 1\n")
        val attestor = attestorIn(root)
        attestor.attest("AGENTS.md", 1)
        Files.writeString(file, "a: 2\n")

        assertTrue(attestor.reattest("AGENTS.md"))

        assertTrue(attestor.attest("AGENTS.md", 1) is AttestationResult.Attested)
    }

    @Test
    fun `a path escaping the repository is never read`() {
        val root = workspace()
        val outside = root.parent.resolve("outside-${System.nanoTime()}.md")
        Files.writeString(outside, "humanAuthority: none\n")

        val result = attestorIn(root).attest("../${outside.fileName}", 1)

        assertTrue(result is AttestationResult.Missing)
        assertNull(attestorIn(root).readText("../${outside.fileName}"))
    }

    @Test
    fun `a tampered agents file refuses the boot and holds dispatch`() {
        val root = workspace()
        val file = root.resolve("AGENTS.md")
        Files.writeString(file, "territoryAtDispatch: required\n")
        val store = FingerprintStore(root.resolve(".atropos/fp.tsv"))
        val bootstrap = AuthBootstrap(root, store, AuthorityAttestor(store, root))

        assertTrue(bootstrap.boot().permitted)
        Files.writeString(file, "territoryAtDispatch: optional\n")

        val refused = bootstrap.boot()
        assertFalse(refused.permitted)
        assertTrue(refused is AuthBootResult.Refused)
        assertTrue(refused.cause.remedy.contains("atropos auth accept"))

        val announcement = AuthorityBootGate(bootstrap).evaluate()
        assertFalse(announcement.dispatchPermitted)
        assertTrue(announcement.error!!.contains("dispatch is held"))
    }

    @Test
    fun `a repository with no authority documents still boots`() {
        val root = workspace()
        val store = FingerprintStore(root.resolve(".atropos/fp.tsv"))

        val boot = AuthBootstrap(root, store, AuthorityAttestor(store, root)).boot()

        assertTrue(boot.permitted)
        assertEquals(emptyList(), (boot as AuthBootResult.Booted).layers)
    }

    @Test
    fun `a fingerprint round-trips through the store`() {
        val root = workspace()
        val store = FingerprintStore(root.resolve(".atropos/fp.tsv"))
        val record = AuthorityFingerprint("AGENTS.md", "abc123", 42, 99, 1)

        assertTrue(store.record(record))

        assertEquals(record, store.read("AGENTS.md"))
    }

    @Test
    fun `a touched but unedited file still matches`() {
        val a = AuthorityFingerprint("AGENTS.md", "abc", 10, 1000, 1)
        val touched = a.copy(modifiedEpochMillis = 2000)

        assertTrue(a.matches(touched))
    }

    @Test
    fun `a loader version change invalidates the record`() {
        val a = AuthorityFingerprint("AGENTS.md", "abc", 10, 1000, 1)

        assertFalse(a.matches(a.copy(loaderVersion = 2)))
    }
}
