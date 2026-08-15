/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.testing

import kotlin.test.assertEquals

/**
 * The two assertions `kotlin.test` does not carry.
 *
 * This project asserts with `kotlin.test`, not JUnit's `Assertions`. Thirty-six
 * test files had drifted onto JUnit's Jupiter API — which is not on the test
 * classpath at all, so none of them compiled, and a broken `compileTestKotlin`
 * takes the *entire* suite down with it, not just those files. Converting them
 * needed homes for the two helpers that have no `kotlin.test` equivalent, and
 * writing those out inline at each call site would have lost what the assertion
 * was saying.
 */

/**
 * Runs [block] and states that completing without throwing is the assertion.
 *
 * `kotlin.test` has no such function because an escaping exception already
 * fails a test. That is true and still worth naming: a bare call in a test body
 * reads as setup, and a reader cannot tell that *not throwing* was the point
 * being made until it starts throwing and the failure lands on a line nobody
 * marked as a check.
 */
inline fun <T> assertDoesNotThrow(message: String? = null, block: () -> T): T =
    try {
        block()
    } catch (failure: Throwable) {
        throw AssertionError(
            (message ?: "expected no exception") +
                ", but ${failure.javaClass.simpleName} was thrown: ${failure.message}",
            failure
        )
    }

/**
 * Byte-array equality by content.
 *
 * `assertEquals` on two `ByteArray`s compares identity, so it passes only when
 * both names point at the same array — an assertion that cannot fail for the
 * reason the test cares about. Compared as lists so a failure prints the bytes
 * that differ rather than two object addresses.
 */
fun assertArrayEquals(expected: ByteArray, actual: ByteArray, message: String? = null) {
    assertEquals(expected.toList(), actual.toList(), message)
}
