/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitMutationCommandTest {
    @Test
    fun mutations_require_confirmation_and_emit_no_confirmation_token() {
        val parsed = GitMutationCommandParser.parse(listOf("/git", "commit", "safe change", "--confirm", "operator-1"))
        assertEquals(GitMutationParse.Accepted(listOf("git", "commit", "-m", "safe change")), parsed)
        assertTrue(GitMutationCommandParser.parse(listOf("/git", "commit", "safe change")) is GitMutationParse.Refused)
    }

    @Test
    fun add_rejects_traversal_and_rebase_accepts_only_the_exact_shape() {
        assertTrue(GitMutationCommandParser.parse(listOf("/git", "add", "../outside", "--confirm", "operator-1")) is GitMutationParse.Refused)
        assertEquals(
            GitMutationParse.Accepted(listOf("git", "rebase", "--continue")),
            GitMutationCommandParser.parse(listOf("/git", "rebase-continue", "--confirm", "operator-1"))
        )
        assertTrue(GitMutationCommandParser.parse(listOf("/git", "rebase-continue", "extra", "--confirm", "operator-1")) is GitMutationParse.Refused)
    }
}
