# ATROPOS Provider Context Trace

## Current State (2026-07-26)

### Provider Entry Points

| Entry Point | File | Prompt Builder | Provider Adapter | Context Includes | Identity Included | Output Validated |
|---|---|---|---|---|---|---|
| /agent ask | AgentService.kt:141 | AgentPromptContract.build() | ProviderCascadeRouter.completeWithCascade() → ScaffoldAdapters | repo root, git status, shallow tree, file snapshots | "ATROPOS" in system text only | No attestation check |
| /agent patch | AgentService.kt:182 | AgentPromptContract.buildPatch() | ProviderCascadeRouter.runPatchCascade() | repo root, git status, file snapshots, shallow tree | "ATROPOS" in system text only | Patch diff parsed, no attestation |
| /agent repair | AgentRepairService.kt:38 | AgentPromptContract.buildRepair() | ProviderCascadeRouter.runPatchCascade() | patch id, paths, failed command, stderr, file snapshots | "ATROPOS" in system text only | No attestation |
| /agent plan | AgentCommand.kt (via AgentService) | AgentPromptContract.build() | ProviderCascadeRouter.completeWithCascade() | repo root, git status, shallow tree, file snapshots | "ATROPOS" in system text only | No attestation |
| daemon jobs | AgentDaemonService.kt | AgentPromptContract.build() | ProviderCascadeRouter.completeWithCascade() | collector context | "ATROPOS" in system text only | No attestation |
| DAG nodes | DagExecutionService.kt → AgentService | AgentPromptContract.build() | ProviderCascadeRouter | collector context | "ATROPOS" in system text only | No attestation |
| Provider failover | ProviderCascadeRouter | Same prompt builder | Next provider in cascade order | Same context | "ATROPOS" in system text only | No attestation |
| Direct adapters | ScaffoldAdapters.kt:815 | N/A (receives prompt text) | HttpClient POST to provider API | No context envelope | Not included | No attestation |
| Provider fixtures | ProviderFixtureMatrixService.kt | N/A (mock responses) | N/A | N/A | N/A | No attestation |

### Current Prompt Contract (AgentPromptContract.kt)

```
SYSTEM_TEXT: "You are an ATROPOS reasoning provider. ATROPOS has read the local repo
and supplied bounded context. You cannot directly access files. Use the provided context.
Do not ask for API keys. Return direct answers, plans, or diffs only."
```

**Problems:**
1. "ATROPOS" is ambiguous — could be the software engine OR Greek mythology
2. No structured envelope (no goal, run, DAG, node, task, role, territory, policy)
3. No machine-readable attestation required from provider
4. No typed failures for context violations
5. No rejection of mythology answers
6. No state awareness (goal, daemon, queue status)

### Available Context (not currently used in prompts)

| Context Source | Available At | Currently In Prompt? |
|---|---|---|
| GoalRunRecord | GoalContinuationService, SelfHostGoalService | No |
| DagNode | DagExecutionService, DagStore | No |
| DagDefinition | DagStore | No |
| Territory (list of paths) | DagNode.territory, IsolatedWorktreeService | No |
| Active policy | AutonomyPolicyEngine | No |
| Role/hierarchy | AutonomyActionClass, AutonomyPolicyEngine | No |
| Provider descriptor | StaticProviderDescriptorRegistry | No |
| ProviderSessionState | SupervisedSessionStore | No |
| Queue state | AgentQueueService | No |

### Current Identity Handling

The system text currently says "You are an ATROPOS reasoning provider." but:
- No identity field prevents Greek mythology interpretation
- No short-input rules ("ATROPOS" could return mythology)
- No response attestation requirement
- No failure types for context mismatch

### Required Changes

1. Create `ContextEnvelope` with all required identity/context fields
2. Replace `AgentPromptContract.build()` to wrap context in structured envelope
3. Add `ProviderContextInjector` to attach envelope before provider dispatch
4. Add `ContextAttestation` and `ContextDriftDetector` for response validation
5. Add `TypedContextFailure` hierarchy with 12 typed failures
6. Wire into `AgentService.ask()`, `.patch()`, `.repair()`
7. Provide short-input handler for "ATROPOS" queries
