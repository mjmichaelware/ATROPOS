# Batch 13 — the Auditor guards the patch-apply boundary

**Priority advanced:** #4 — independent Auditor that cannot self-approve.
**Decision extended:** E, from DAG-node completion to the other promotion point.

## The gap

Batch 10 gave the Auditor authority at one boundary: `VerifiedCompletionGate`.
But that gate governs DAG nodes. The place where **provider-authored change
actually enters the working tree** is `AgentPatchStore.applyPatch`, and nothing
consults the Auditor there.

Today an apply passes through territory grant → policy gate → diff validation →
`git apply`. Every one of those judges the *shape* of the change — whose paths,
which action class, is the diff well-formed. None of them reads what the patch
would put in the files.

## What changes

Immediately before the mutation, the Auditor reviews the paths the patch
touches. `FAILURE` or `CRITICAL` findings refuse the apply.

The Auditor still cannot approve: it can only refuse. A clean audit does not
bypass territory, policy, or diff validation — those already ran and the apply
proceeds only if all of them allowed it. The Auditor subtracts from an
already-granted permission, exactly as at the completion gate.

`checkOnly` does **not** audit-refuse: a check mutates nothing, and its job is
to report whether the patch would apply. Auditing gates the mutation.

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/agent/AgentPatchStore.kt   (pre-mutation point only)
src/test/kotlin/atropos/core/agent/
```

## Acceptance criteria

1. A patch whose touched paths carry credential material is refused before
   `git apply` runs — nothing reaches the process seam.
2. The refusal names the Auditor and carries the finding, so the operator can
   see what blocked it.
3. A clean patch still applies — the Auditor is not a blanket refusal.
4. `checkOnly` reports as before and is not refused by the audit.
5. A clean audit does not rescue a patch that territory or policy refused: the
   audit runs after those, never instead of them.
6. Each apply gets a fresh `AuditorService`, so one patch's findings cannot
   refuse another's.

## No stub

The audit is a real refusal with a real failure mode, proven in both directions.
It is placed after the existing gates so it can only ever subtract.

## What the audit actually catches (corrected during the Worker pass)

The contract above said the Auditor "reviews the paths the patch touches". While
writing the test I found `createRecord` already refuses secret-bearing **diffs**
at persistence:

```kotlin
require(!redactionFilter.report(renderedDiff).changed) {
    "patch diff contains secret-bearing content and was refused before persistence"
}
```

So a patch whose diff carries a secret never gets stored, and the apply-time
audit could never see one. What the audit catches is complementary and is what
diff-level scanning structurally cannot see: **the current contents of the file
about to be mutated**. A clean diff editing a file that already holds an API key
is refused. The tests were retargeted onto that real property rather than the
one I first assumed.

## Regression found and fixed — `/agent apply` was refusing itself

Batch 8 grants the patch actor its **touched paths**. But
`PatchActionProposals.applyCheck` declares the **diff file** as its target path.
So every `applyPatch` territory-refused at its own apply-check step:

```
territory refusal: patch:patch-2026… holds no grant covering
  '.atropos/agent/patches/patch-2026….diff'
```

That is a live break of `/agent apply` introduced by Batch 8 and caught only
because this batch exercised the full flow. The grant now covers the touched
paths **and** the stored diff, which is what the apply genuinely reads and
writes.

## Auditor result — PASS

Compile: one end-of-batch compile, clean.

| Class | Tests | Failures |
|---|---|---|
| `atropos.core.agent.AgentPatchAuditorTest` (new) | 5 | 0 |
| `atropos.core.agent.AgentPatchBoundedAgencyTest` | 8 | 0 |
| **total** | **13** | **0** |

| # | Criterion | Evidence |
|---|---|---|
| 1 | refused before `git apply` | `a_patch_mutating_a_file_that_holds_secrets_never_reaches_git_apply` — mutation counter 0 |
| 2 | refusal names the Auditor | `the_refusal_names_the_auditor_and_carries_the_finding` |
| 3 | not a blanket refusal | `an_ordinary_patch_is_not_blocked_by_the_auditor` |
| 4 | check-only unaffected | `a_check_only_run_is_not_refused_by_the_audit` |
| 5 | audit cannot rescue | `the_auditor_runs_after_territory_and_cannot_rescue_a_refused_patch` |
| 6 | fresh auditor per apply | `auditorFactory` defaults to a new `AuditorService(repoRoot)` per call |

The spawn seam counts only `git apply` without `--check`; `git apply --check`
and `git status` run for real so the flow reaches the audit. Tests run against a
real `git init`ed repository, because `applyPatch` refuses when it cannot read
`git status` on the targets.

`git diff --check`: clean. No out-of-territory repair required.
