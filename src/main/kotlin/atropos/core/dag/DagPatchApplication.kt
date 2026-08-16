/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.dag

import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentPatchStore
import atropos.core.agent.AgentService

/**
 * Turns a `PROVIDER_CALL` node's attested answer into an applied patch.
 *
 * This is the seam the self-build loop was missing. [DagProviderNodeExecutor]
 * asked a provider, received an answer, recorded it as an "attested provider
 * advisory" and marked the node COMPLETE — so a self-host run reported success
 * having written nothing. Everything downstream existed and had no upstream:
 * [AgentPatchExtractor] could read a unified diff out of provider prose,
 * [AgentPatchStore] could persist and gate it, [AgentService.applyPatch] could
 * apply and verify it, and nothing on the DAG path called any of them.
 *
 * It owns no policy. Extraction is [AgentPatchExtractor], the banned-path list
 * and the redaction refusal are [AgentPatchStore], territory and bounded agency
 * are the gates already inside the apply service, and verification is the
 * verifier. What this adds is the call.
 *
 * ## An answer with no diff is not a failure
 *
 * Most provider answers to a contract-shaped atom are prose, and that is the
 * correct output for a node that was asked to describe rather than to change
 * something. [NoPatch] says so and leaves the node's own executor to decide;
 * treating "no diff" as an error would fail every planning node in the graph.
 *
 * ## check before apply
 *
 * The patch is checked against the working tree before it is applied, because
 * `git apply` is not atomic across a multi-file diff — a patch that fails
 * halfway leaves a tree that is neither the old state nor the new one, on a
 * device where the operator may have no second checkout to recover from.
 */
class DagPatchApplication(
    private val agentService: AgentService,
    private val patchStore: AgentPatchStore,
    private val extractor: AgentPatchExtractor = AgentPatchExtractor()
) {

    sealed class Outcome {
        /** The answer carried no unified diff. The normal case for a contract atom. */
        object NoPatch : Outcome()

        /** A diff was found and refused before it touched the tree. */
        data class Refused(val reason: String) : Outcome()

        /** A diff was found, checked, applied, and (when asked) verified. */
        data class Applied(
            val patchId: String,
            val changedPaths: List<String>,
            val verified: Boolean?
        ) : Outcome() {
            fun evidenceLine(): String =
                "patch=$patchId applied files=${changedPaths.size} " +
                    "verified=${verified?.toString() ?: "not-run"} " +
                    "paths=${changedPaths.joinToString(",")}"
        }
    }

    /**
     * @param verifyAfterApply whether to run verification once the patch lands.
     *   Off for a node whose own DAG has a later `COMPILE_GATE` or `RUN_TEST`
     *   node — verifying twice costs a compile on a phone and proves the same
     *   thing.
     */
    fun applyFrom(
        answerText: String,
        provider: String,
        task: String,
        contextBytes: Int,
        verifyAfterApply: Boolean = false
    ): Outcome {
        val extraction = extractor.extract(answerText) ?: return Outcome.NoPatch

        // A diff header with no hunk body is a model describing a change it did
        // not make. Applying it is a no-op that would still be recorded as an
        // applied patch, which is the most misleading possible entry in the
        // patch log.
        if (!extraction.hasHunkBody) {
            return Outcome.Refused("provider returned a diff header with no hunk body")
        }
        extractor.validate(extraction.diff)?.let { return Outcome.Refused(it) }

        val record = try {
            patchStore.createRecord(
                provider = provider,
                task = task,
                contextBytes = contextBytes,
                diff = extraction.diff
            )
        } catch (failure: IllegalArgumentException) {
            // The store refuses to persist a secret-bearing diff. Reported as a
            // refusal rather than rethrown: a leak the store caught is the gate
            // working, not the run crashing.
            return Outcome.Refused(failure.message ?: "patch refused before persistence")
        } catch (failure: Exception) {
            return Outcome.Refused("patch could not be recorded: ${failure.message ?: failure.javaClass.simpleName}")
        }

        val check = agentService.applyPatch(record.id, checkOnly = true)
        if (!check.applied && check.refusalReason != null) {
            return Outcome.Refused("patch does not apply cleanly: ${check.refusalReason}")
        }

        val applied = agentService.applyPatch(
            patchReference = record.id,
            checkOnly = false,
            verifyAfterApply = verifyAfterApply
        )
        if (!applied.applied) {
            return Outcome.Refused(
                applied.refusalReason ?: "patch apply refused (exit ${applied.applyExitCode ?: "unknown"})"
            )
        }

        return Outcome.Applied(
            patchId = applied.patchId ?: record.id,
            changedPaths = applied.changedPaths.ifEmpty { extraction.touchedPaths },
            verified = applied.verificationResult?.let { it.passed }
        )
    }
}
