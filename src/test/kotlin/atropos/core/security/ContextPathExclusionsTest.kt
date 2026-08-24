package atropos.core.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rules that keep credentials out of a provider context.
 *
 * These used to exist as two hand-synchronised copies — one for directly
 * collected context, one for packed source bindings — that had drifted apart in
 * both directions. Both paths now run through these assertions.
 */
class ContextPathExclusionsTest {

    private fun excluded(path: String) =
        assertTrue(ContextPathExclusions.isExcluded(path), "$path must never enter a provider context")

    private fun allowed(path: String) =
        assertFalse(ContextPathExclusions.isExcluded(path), "$path should be readable context")

    @Test
    fun `secret directories are excluded with everything beneath them`() {
        excluded(".atropos/secrets")
        excluded(".atropos/secrets/vault.json")
        excluded(".atropos/source-bindings/binding-1/file.kt")
        excluded(".atropos/agent/patches/patch-1.diff")
    }

    @Test
    fun `build and vcs directories are excluded`() {
        excluded(".git")
        excluded(".git/config")
        excluded(".gradle/caches/thing.bin")
        excluded("build/libs/ATROPOS.jar")
    }

    @Test
    fun `env files are excluded on both paths`() {
        excluded(".env")
        excluded(".env.local")
        excluded(".env.production")
        excluded("config/.env")
        excluded("atropos-provider.env")
        excluded("config/provider.env")
    }

    @Test
    fun `credential extensions are excluded`() {
        listOf("id.key", "server.pem", "client.crt", "store.p12", "api.token", "x.secret", "aws.credentials")
            .forEach { excluded("config/$it") }
    }

    @Test
    fun `binary payloads are excluded so they cannot eat the context budget`() {
        listOf("a.jar", "a.class", "a.zip", "a.tar", "a.gz", "a.png", "a.jpg", "a.jpeg", "a.gif")
            .forEach { excluded("assets/$it") }
    }

    @Test
    fun `credential-suggesting names are excluded regardless of extension`() {
        excluded("config/tokens.json")
        excluded("config/my-secret.yaml")
        excluded("config/credentials.ini")
        excluded("config/apikeys.txt")
    }

    @Test
    fun `kotlin source is never excluded for its name alone`() {
        allowed("src/main/kotlin/atropos/core/security/TokenIsolationVault.kt")
        allowed("src/main/kotlin/atropos/core/secret/SecretStore.kt")
        allowed("src/main/kotlin/atropos/core/ApiKeys.kt")
        allowed("build.gradle.kts")
        allowed("src/main/kotlin/ProviderEnv.kt")
    }

    @Test
    fun `a kotlin file with a credential extension is still excluded`() {
        excluded("src/main/kotlin/leaked.key")
    }

    @Test
    fun `ordinary source and docs are readable`() {
        allowed("src/main/kotlin/atropos/Main.kt")
        allowed("docs/README.md")
        allowed("AGENTS.md")
    }

    @Test
    fun `backslash separators cannot slip a path past a prefix rule`() {
        excluded(".atropos\\secrets\\vault.json")
        excluded(".git\\config")
    }

    @Test
    fun `a prefix that merely starts with a excluded name is not excluded`() {
        allowed("buildSrc/Versions.kt")
        allowed(".gitignore")
    }

    @Test
    fun `an empty path is not excluded`() {
        allowed("")
    }
}
