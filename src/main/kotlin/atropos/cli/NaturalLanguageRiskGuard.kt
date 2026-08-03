/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

/** Classifies only natural-language requests that need an explicit operator check. */
class NaturalLanguageRiskGuard {
    enum class Risk { DESTRUCTIVE_GIT, PAID_UNLOCK, SECRET_ACCESS, FORCE_PUSH, MASS_DELETE, JAR_SWAP }

    fun classify(text: String): Risk? {
        val value = text.lowercase()
        return when {
            listOf("force push", "--force", "git push -f").any(value::contains) -> Risk.FORCE_PUSH
            listOf("paid unlock", "unlock paid", "enable paid", "spend money").any(value::contains) -> Risk.PAID_UNLOCK
            listOf("show secret", "print api key", "read secret", "dump credentials").any(value::contains) -> Risk.SECRET_ACCESS
            listOf("jar swap", "promote jar", "replace installed jar").any(value::contains) -> Risk.JAR_SWAP
            listOf("mass delete", "delete everything", "wipe repository", "rm -rf").any(value::contains) -> Risk.MASS_DELETE
            value.contains("git reset --hard") || value.contains("delete git history") -> Risk.DESTRUCTIVE_GIT
            else -> null
        }
    }
}
