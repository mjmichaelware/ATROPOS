/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The generalisation predicate: an ordinary change request must route, and a
 * question must not mutate anything. Both directions are load-bearing — a
 * classifier that only widened the verb list would self-mutate on being asked
 * what it does.
 */
class SelfHostChangeRequestClassifierTest {

    private val classifier = SelfHostChangeRequestClassifier()

    private fun classify(text: String) = classifier.classify(text)

    // --- the gap this owner closes ---------------------------------------

    @Test
    fun generic_change_requests_without_unmistakable_self_host_keywords_are_unrelated() {
        // Natural language change requests must NOT trigger self-host without unmistakable keywords
        listOf(
            "change the bounded process timeout in ATROPOS to 60 seconds",
            "add a retry field to the ATROPOS endpoint manifest",
            "fix the redaction filter in ATROPOS",
            "refactor ATROPOS's promotion service into smaller files",
            "remove the unused parameter from ATROPOS BoundedProcessRunner",
            "rename the goal store in ATROPOS",
            "harden ATROPOS against malformed vault keys",
            "wire the installed proof evidence into ATROPOS",
            "Modify the codebase files to implement the fix.",
            "the class modifies its own state"
        ).forEach { request ->
            assertEquals(
                SelfHostUtterance.UNRELATED,
                classify(request),
                "should be UNRELATED without unmistakable self-host keyword: $request"
            )
        }
    }

    @Test
    fun the_unmistakable_self_host_phrases_still_route() {
        listOf(
            "build yourself",
            "ATROPOS, improve yourself",
            "make ATROPOS build itself from inside out",
            "run self-host Phase 11"
        ).forEach { request ->
            assertEquals(SelfHostUtterance.CHANGE_REQUEST, classify(request), request)
        }
    }

    // --- the false positives generalising would otherwise buy -------------

    @Test
    fun questions_about_atropos_are_never_change_requests() {
        // Each of these names the runtime and contains a mutation verb, so a
        // verb-only rule would mutate source in order to answer a question.
        listOf(
            "how does ATROPOS build itself?",
            "what does ATROPOS change when it promotes a jar",
            "why did ATROPOS remove that file?",
            "explain how ATROPOS updates the evidence bundle",
            "can you add a field to ATROPOS?",
            "show me where ATROPOS sets the timeout"
        ).forEach { question ->
            assertEquals(SelfHostUtterance.QUESTION, classify(question), "must not mutate: $question")
        }
    }

    @Test
    fun work_that_is_not_about_atropos_is_unrelated() {
        listOf(
            "build a calculator",
            "add a login screen to my app",
            "write a script that documents itself",
            "fix the bug in my python project"
        ).forEach { other ->
            assertEquals(SelfHostUtterance.UNRELATED, classify(other), other)
        }
    }

    @Test
    fun a_trailing_question_mark_decides_the_mood() {
        assertEquals(SelfHostUtterance.CHANGE_REQUEST, classify("update the ATROPOS self-host readme"))
        assertEquals(SelfHostUtterance.QUESTION, classify("update the ATROPOS self-host readme?"))
    }

    // --- continuation stays distinct from mutation ------------------------

    @Test
    fun continuation_requests_are_their_own_class() {
        listOf(
            "continue ATROPOS self-host after restart",
            "resume the ATROPOS self-host run",
            "recover ATROPOS self-host",
            "restart the ATROPOS self-host goal"
        ).forEach { request ->
            assertEquals(SelfHostUtterance.CONTINUATION, classify(request), request)
        }
    }

    // --- matching is by word, not substring -------------------------------

    @Test
    fun verbs_match_on_word_boundaries_not_substrings() {
        // "address" contains "add"; "reset" contains "set". Substring matching
        // would make both of these change requests for no traceable reason.
        assertEquals(SelfHostUtterance.QUESTION, classify("what is the ATROPOS self-host address?"))
        assertEquals(SelfHostUtterance.UNRELATED, classify("the ATROPOS mindset"))
    }

    @Test
    fun blank_and_whitespace_input_is_unrelated() {
        assertEquals(SelfHostUtterance.UNRELATED, classify(""))
        assertEquals(SelfHostUtterance.UNRELATED, classify("   "))
    }
}
