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

export function ExecutionRunList({ projectId, runs }: { projectId: string; runs: ExecutionRunSummary[] }) {
  const [planId, setPlanId] = useState("");
  const [runtimeSystem, setRuntimeSystem] = useState("");
  const [runtimeRunId, setRuntimeRunId] = useState("");
  const mutation = useStartExecutionRunMutation(projectId);

  async function start(event: React.FormEvent) {
    event.preventDefault();
    if (!planId.trim() || !runtimeSystem.trim() || !runtimeRunId.trim()) return;
    await mutation.mutateAsync({ planId: planId.trim(), input: { runtime_system: runtimeSystem.trim(), runtime_run_id: runtimeRunId.trim() } }).catch(() => {});
  }

  return (
    <div className="sg-planning-form">
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
        <Field id="run-plan-id" label="Plan ID (from Graph → Plans)" value={planId} onChange={(event) => setPlanId(event.target.value)} placeholder="plan-…" required maxLength={160} />
        <Field id="run-runtime-system" label="Runtime system" value={runtimeSystem} onChange={(event) => setRuntimeSystem(event.target.value)} required maxLength={160} />
        <Field id="run-runtime-run-id" label="Runtime run ID" value={runtimeRunId} onChange={(event) => setRuntimeRunId(event.target.value)} required maxLength={160} />
        {mutation.isError ? (
          <Alert tone="danger" title="Execution start failed">
            <p>{mutation.error instanceof Error ? mutation.error.message : "The run could not be started from real server-eligible state. No new run was created."}</p>
          </Alert>
        ) : null}
        <Button type="submit" loading={mutation.isPending} disabled={mutation.isPending}>
          Start execution run
        </Button>
      </form>
    </div>
  );
}
