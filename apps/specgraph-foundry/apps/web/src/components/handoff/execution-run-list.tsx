"use client";

import { useState } from "react";
import Link from "next/link";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { StatusBadge } from "@/components/ui/status-badge";
import { projectExecutionRoute } from "@/components/navigation/routes";
import { useStartExecutionRunMutation } from "@/lib/handoff/mutations";
import type { ExecutionRunSummary } from "@/lib/handoff/schemas";
import { usePlanList } from "@/lib/planning/queries";

function formatPlanOption(plan: { id: string; status?: string; created_at?: string }): string {
  const when = plan.created_at ? new Date(plan.created_at).toLocaleString() : "unknown date";
  return `${plan.status ?? "UNKNOWN"} — ${when} (${plan.id.slice(0, 8)})`;
}

export function ExecutionRunList({ projectId, runs }: { projectId: string; runs: ExecutionRunSummary[] }) {
  const [planId, setPlanId] = useState("");
  const [runtimeSystem, setRuntimeSystem] = useState("");
  const [runtimeRunId, setRuntimeRunId] = useState("");
  const [progressMessage, setProgressMessage] = useState<string | undefined>();
  const mutation = useStartExecutionRunMutation(projectId, setProgressMessage);
  const plans = usePlanList(projectId);
  const planItems = plans.data?.body.items ?? [];

  async function start(event: React.FormEvent) {
    event.preventDefault();
    if (!planId.trim() || !runtimeSystem.trim() || !runtimeRunId.trim()) return;
    await mutation.mutateAsync({ planId: planId.trim(), input: { runtime_system: runtimeSystem.trim(), runtime_run_id: runtimeRunId.trim() } }).catch(() => {});
  }

  return (
    <div className="sg-planning-form">
      <p className="sg-muted">
        An execution run is a real record of a verified plan being carried out by a connected system, tracked through to independent verification — SpecGraph doesn&apos;t run anything itself, it
        records and checks what the connected system reports. Click a run below to see its stages, receipts, and findings.
      </p>
      {runs.length === 0 ? <p className="sg-muted">No execution runs exist for this project yet.</p> : null}
      <ul className="sg-plan-history" aria-label="Execution runs">
        {runs.map((run) => (
          <li key={run.id}>
            <Link href={projectExecutionRoute(projectId, run.id)}>
              <span className="sg-mono">{run.id.slice(0, 8)}</span> <StatusBadge tone="neutral" label={String(run.status ?? "UNKNOWN")} />
            </Link>
          </li>
        ))}
      </ul>
      <form className="sg-planning-form" onSubmit={(event) => void start(event)} aria-label="Start execution run from a verified plan">
        {planItems.length > 0 ? (
          <label className="sg-field">
            Plan to run
            <select className="sg-select" aria-label="Plan to run" value={planId} onChange={(event) => setPlanId(event.target.value)} required>
              <option value="" disabled>
                Choose a plan…
              </option>
              {planItems.map((plan) => (
                <option key={plan.id} value={plan.id}>
                  {formatPlanOption(plan)}
                </option>
              ))}
            </select>
          </label>
        ) : (
          <p className="sg-muted">No plans exist yet — synthesize one from Graph → Plans first.</p>
        )}
        <Field
          id="run-runtime-system"
          label="Runtime system"
          description="The name of the external system that's actually going to run this plan — a CI pipeline, an orchestrator, your own deployment tool. SpecGraph doesn't execute anything itself; this just labels which system you're recording a run for, e.g. github-actions or internal-deploy-bot."
          value={runtimeSystem}
          onChange={(event) => setRuntimeSystem(event.target.value)}
          required
          maxLength={160}
        />
        <Field
          id="run-runtime-run-id"
          label="Runtime run ID"
          description="The run or job ID that system assigns on its own side, so this record can be matched back to the real run later — e.g. a GitHub Actions run number or a deployment ID from your own tooling."
          value={runtimeRunId}
          onChange={(event) => setRuntimeRunId(event.target.value)}
          required
          maxLength={160}
        />
        {mutation.isError ? (
          <Alert tone="danger" title="Execution start failed">
            <p>{mutation.error instanceof Error ? mutation.error.message : "The run could not be started from real server-eligible state. No new run was created."}</p>
          </Alert>
        ) : null}
        <Button type="submit" loading={mutation.isPending} disabled={mutation.isPending || !planId}>
          Start execution run
        </Button>
        {mutation.isPending && progressMessage ? (
          <p role="status" aria-live="polite" className="sg-micro-label">
            {progressMessage}
          </p>
        ) : null}
      </form>
    </div>
  );
}
