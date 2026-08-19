/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The binary saying what it is.
 *
 * This exists because an operator who cannot rebuild — every install on a
 * phone comes from a release asset — had no way to tell whether the jar they
 * were running matched the source they had just pulled. The symptom was a fix
 * that appeared not to work, and the cause was invisible.
 */
class BuildStampTest {

    @Test
    fun the_stamp_is_baked_into_the_build_rather_than_read_from_the_tree() {
        // Under test the resource is on the classpath because the build wrote
        // it, which is the same path the jar takes.
        assertEquals("2.0.0", BuildStamp.version)
        assertTrue(BuildStamp.commit.isNotBlank())
    }

    @Test
    fun the_line_names_the_version_and_the_commit_it_came_from() {
        val line = BuildStamp.line()

        assertTrue(line.startsWith("ATROPOS "), line)
        assertTrue(line.contains(BuildStamp.version), line)
        assertTrue(line.contains(BuildStamp.commit), line)
    }

    @Test
    fun a_commit_is_short_enough_to_compare_by_eye() {
        // The point is a value an operator can check against a release page in
        // one glance, not a forty-character string they will not read.
        assertTrue(
            BuildStamp.commit == BuildStamp.UNKNOWN || BuildStamp.commit.length <= 12,
            "commit '${BuildStamp.commit}' is too long to compare at a glance"
        )
    }
}
