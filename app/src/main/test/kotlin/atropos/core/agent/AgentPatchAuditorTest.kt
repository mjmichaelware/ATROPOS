/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.TypedToolExecutor
import atropos.core.territory.TerritoryGrantService
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Batch 13 — the Auditor guards the point where provider-authored change enters
 * the working tree.
 *
 * Territory, policy and diff validation all judge the shape of a change. None
 * of them reads what the patch would put in the files.
 */
class AgentPatchAuditorTest {

    /**
     * Counts only *mutating* spawns.
     *
     * `git apply --check` and `git status` legitimately run before the audit
     * and mutate nothing; they are let through so the flow reaches the point
     * under test. Only `git apply` without `--check` is the mutation, and it
     * must never happen once the auditor has refused.
     */
    private class SpawnCounter {
        val mutations = AtomicInteger(0)
        val seam: (List<String>, Path) -> Process = { command, directory ->
            val isMutation = command.getOrNull(1) == "apply" && !command.contains("--check")
            if (!isMutation) {
                ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start()
            } else {
                mutations.incrementAndGet()
                error("the auditor's refusal leaked: git apply was reached")
            }
        }
    }

    /**
     * A real git repository: `applyPatch` consults `git status` on the target
     * paths and refuses when it cannot read them, so the audit is only reached
     * inside an actual repo.
     */
    private fun repo(): Path {
        val root = Files.createTempDirectory("atropos-patch-audit-")
        listOf(
            listOf("git", "init", "-q"),
            listOf("git", "config", "user.email", "t@example.invalid"),
            listOf("git", "config", "user.name", "t")
        ).forEach { ProcessBuilder(it).directory(root.toFile()).start().waitFor() }
        return root
    }

    private fun commitAll(root: Path) {
        listOf(listOf("git", "add", "-A"), listOf("git", "commit", "-q", "-m", "base"))
            .forEach { ProcessBuilder(it).directory(root.toFile()).start().waitFor() }
    }

    private fun storeOver(root: Path, spawns: SpawnCounter? = null): AgentPatchStore {
        val grants = TerritoryGrantService(TerritoryService(TerritoryStore(root)))
        val gate = BoundedAgencyGate(ExecutionPolicyEngine(root), grants)
        return if (spawns == null) {
            AgentPatchStore(repoRoot = root, territoryGrants = grants, agencyGate = gate, agency = TypedToolExecutor(gate))
        } else {
            AgentPatchStore(
                repoRoot = root, territoryGrants = grants, agencyGate = gate,
                agency = TypedToolExecutor(gate), spawn = spawns.seam
            )
        }
    }

    /**
     * A clean-looking diff against a file that already carries secret material.
     *
     * `createRecord` scans the diff text and refuses secret-bearing diffs
     * outright, so the diff itself must be innocuous. What the Auditor sees is
     * what diff-level scanning structurally cannot: the current contents of the
     * file about to be mutated.
     */
    private fun patchTouchingAFileThatHoldsSecrets(root: Path): AgentPatchRecord {
        val target = root.resolve("settings.conf")
        Files.writeString(
            target,
            """
            mode=fast
            api_key = "sk-live-abcdefghijklmnopqrstuvwxyz0123456789"
            retries=2
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8
        )
        commitAll(root)
        return storeOver(root).createRecord(
            provider = "test",
            task = "bump retries",
            contextBytes = 0,
            diff = "--- a/settings.conf\n+++ b/settings.conf\n@@ -3 +3 @@\n-retries=2\n+retries=3\n"
        )
    }

    private fun ordinaryPatch(root: Path): AgentPatchRecord {
        val target = root.resolve("notes.txt")
        Files.writeString(target, "old\n", StandardCharsets.UTF_8)
        commitAll(root)
        return storeOver(root).createRecord(
            provider = "test",
            task = "edit notes",
            contextBytes = 0,
            diff = "--- a/notes.txt\n+++ b/notes.txt\n@@ -1 +1 @@\n-old\n+new\n"
        )
    }

    @Test
    fun a_patch_mutating_a_file_that_holds_secrets_never_reaches_git_apply() {
        val root = repo()
        val record = patchTouchingAFileThatHoldsSecrets(root)
        val spawns = SpawnCounter()

        val result = storeOver(root, spawns).applyPatch(record.id, checkOnly = false)

        assertEquals(0, spawns.mutations.get(), "the mutation must not be attempted")
        assertTrue(!result.applied)
    }

    @Test
    fun the_refusal_names_the_auditor_and_carries_the_finding() {
        val root = repo()
        val record = patchTouchingAFileThatHoldsSecrets(root)

        val result = storeOver(root, SpawnCounter()).applyPatch(record.id, checkOnly = false)

        assertTrue(!result.applied)
        assertTrue(
            result.refusalReason.orEmpty().contains("auditor blocked apply"),
            "the operator must see what blocked it: ${result.refusalReason}"
        )
        assertEquals(AgencyDisposition.POLICY_BLOCKED, result.disposition)
    }

    @Test
    fun a_tampered_stored_patch_with_secret_text_never_reaches_git_apply() {
        val root = repo()
        val record = ordinaryPatch(root)
        Files.writeString(
            record.diffFile,
            "--- a/notes.txt\n+++ b/notes.txt\n@@ -1 +1 @@\n-old\n+api_key = \"sk-live-abcdefghijklmnopqrstuvwxyz0123456789\"\n",
            StandardCharsets.UTF_8
        )
        val spawns = SpawnCounter()

        val result = storeOver(root, spawns).applyPatch(record.id, checkOnly = false)

        assertEquals(0, spawns.mutations.get(), "secret-bearing stored diff must not be applied")
        assertTrue(!result.applied)
        assertTrue(result.refusalReason.orEmpty().contains("auditor blocked apply"))
        assertEquals(AgencyDisposition.POLICY_BLOCKED, result.disposition)
    }

    @Test
    fun a_check_only_run_is_not_refused_by_the_audit() {
        val root = repo()
        val record = patchTouchingAFileThatHoldsSecrets(root)

        val result = storeOver(root).applyPatch(record.id, checkOnly = true)

        // A check mutates nothing; its job is to report whether the patch applies.
        assertTrue(result.checkOnly)
        assertNull(
            result.refusalReason?.takeIf { it.contains("auditor blocked") },
            "check-only must not be audit-refused: ${result.refusalReason}"
        )
    }

    @Test
    fun an_ordinary_patch_is_not_blocked_by_the_auditor() {
        val root = repo()
        val record = ordinaryPatch(root)

        val result = storeOver(root).applyPatch(record.id, checkOnly = false)

        assertTrue(
            !result.refusalReason.orEmpty().contains("auditor blocked"),
            "the auditor must not be a blanket refusal: ${result.refusalReason}"
        )
    }

    @Test
    fun the_auditor_runs_after_territory_and_cannot_rescue_a_refused_patch() {
        val root = repo()
        val record = ordinaryPatch(root)
        val spawns = SpawnCounter()

        // A store whose gate has no territory grants at all: territory refuses
        // first, and a clean audit does not undo that.
        // A root grant scoped somewhere the patch does not touch. HumanOwner
        // always holds a root grant, so "no territory" has to mean "territory
        // that does not cover this", not "no grant service".
        val emptyGrants = TerritoryGrantService(
            TerritoryService(TerritoryStore(Files.createTempDirectory("atropos-audit-elsewhere-"))),
            rootPrefix = "somewhere/else"
        )
        val gate = BoundedAgencyGate(ExecutionPolicyEngine(root), emptyGrants)
        val store = AgentPatchStore(
            repoRoot = root,
            territoryGrants = emptyGrants,
            agencyGate = gate,
            agency = TypedToolExecutor(gate),
            spawn = spawns.seam
        )

        val result = store.applyPatch(record.id, checkOnly = false)

        assertTrue(!result.applied, "a clean audit must not rescue a refused patch")
        assertEquals(0, spawns.mutations.get())
    }
}
