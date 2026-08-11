"use client";

import { useEffect, useRef, useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { describeClientError } from "@/lib/api/errors";
import type { OperationLike } from "@/lib/api/operations";
import { createProjectApiClient } from "@/lib/projects/api";
import { addResearchEvidence, claimResearchTask, completeResearchTask, heartbeatResearchTask } from "@/lib/research/api";
import { createResearchWorkerId } from "@/lib/research/security";
import type { ConclusionInput, EvidenceInput, EvidenceRecord, ResearchTask } from "@/lib/research/schemas";
import { useResearchTask } from "@/lib/research/queries";
import { ResearchErrorState } from "./research-error-state";
import { EvidenceLedger } from "./evidence-ledger";
import { EvidenceForm } from "./evidence-form";
import { ConclusionForm } from "./conclusion-form";
import { TaskLeasePanel } from "./task-lease-panel";
import { TaskOperation } from "./task-operation";
import { ResearchTimeline } from "./research-timeline";
import { AuthoritySeparation } from "./authority-separation";

export function TaskInspector({ projectId, taskId }: { projectId: string; taskId: string }) {
  const taskQuery = useResearchTask(taskId);
  const workerId = useRef(createResearchWorkerId());
  const [claimed, setClaimed] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [messageTone, setMessageTone] = useState<"info" | "danger">("info");
  const [operation, setOperation] = useState<Record<string, unknown> | undefined>();

  useEffect(() => {
    if (!claimed) return undefined;
    const timer = window.setInterval(() => {
      heartbeatResearchTask(createProjectApiClient(), taskId, workerId.current)
        .then(() => setMessage("Lease heartbeat accepted."))
        .catch(() => {
          setClaimed(false);
          setMessage("Lease heartbeat failed. Refresh or reclaim before continuing.");
        });
    }, 120_000);
    return () => window.clearInterval(timer);
  }, [claimed, taskId]);

  if (taskQuery.isLoading) {
    return <Skeleton style={{ height: "24rem" }} />;
  }
  if (taskQuery.isError) {
    return <ResearchErrorState title="Research task not found" onRetry={() => void taskQuery.refetch()} />;
  }
  const task = taskQuery.data?.body as ResearchTask;
  const evidence = (task.evidence ?? []) as EvidenceRecord[];
  const evidenceIds = evidence.map((item) => String(item.id ?? "")).filter(Boolean);

  async function claim() {
    const client = createProjectApiClient();
    try {
      const result = await claimResearchTask(client, projectId, workerId.current, client.createIdempotencyKey());
      setClaimed(true);
      setMessageTone("info");
      setMessage(`Claim accepted for ${String(result.body.task.id ?? taskId)}.`);
      await taskQuery.refetch();
    } catch (error) {
      setMessageTone("danger");
      setMessage(`Claim did not complete. (${describeClientError(error)})`);
    }
  }

  async function addEvidence(input: EvidenceInput) {
    const client = createProjectApiClient();
    try {
      await addResearchEvidence(client, taskId, workerId.current, input, client.createIdempotencyKey());
      setMessageTone("info");
      setMessage("Evidence recorded. Refreshing authoritative task state.");
      await taskQuery.refetch();
    } catch (error) {
      setMessageTone("danger");
      setMessage(`Evidence was not recorded. (${describeClientError(error)})`);
    }
  }

  async function complete(input: ConclusionInput) {
    const client = createProjectApiClient();
    try {
      // complete_research_task is an async operation: the worker that
      // processes it only runs on its own schedule, so this poll can
      // legitimately take minutes, not seconds.
      const accepted = await completeResearchTask(client, taskId, workerId.current, input, client.createIdempotencyKey());
      setOperation(accepted.body.operation);
      const terminal = accepted.location ? await client.pollOperation<{ operation: OperationLike & Record<string, unknown> }>(accepted.location) : accepted;
      setOperation(terminal.body.operation);
      // pollOperation resolves (does not throw) for every terminal state,
      // including FAILED/CANCELLED/TIMED_OUT - only SUCCEEDED counts as success.
      setMessageTone(terminal.body.operation.state === "SUCCEEDED" ? "info" : "danger");
      setMessage(`Completion operation ${String(terminal.body.operation.state ?? "finished")}.`);
      await taskQuery.refetch();
    } catch (error) {
      setMessageTone("danger");
      setMessage(`Completion did not finish. (${describeClientError(error)})`);
    }
  }

  return (
    <section className="sg-task-inspector" aria-labelledby="research-task-title">
      <header className="sg-source-hero sg-research-hero">
        <p className="sg-micro-label">Research task</p>
        <h1 id="research-task-title">{String(task.dimension ?? "Research task")}</h1>
        <p>Evidence, conclusions, and lease state are separate from immutable source authority.</p>
        <Button type="button" variant="primary" disabled={claimed} onClick={() => void claim()}>{claimed ? "Claimed in this tab" : "Claim task"}</Button>
      </header>
      {task.canonical_statement ? (
        <section className="sg-hud-panel" aria-label="Atom under research">
          {task.kind || task.modality ? (
            <p className="sg-micro-label">{[task.kind, task.modality].filter(Boolean).join(" · ")}</p>
          ) : null}
          <p>{task.canonical_statement}</p>
        </section>
      ) : null}
      {message ? <Alert tone={messageTone}>{message}</Alert> : null}
      <div className="sg-research-detail-grid">
        <div>
          <ResearchTimeline task={task} />
          <EvidenceLedger evidence={evidence} />
          <EvidenceForm disabled={!claimed} onSubmit={addEvidence} />
        </div>
        <aside>
          <TaskLeasePanel task={task} claimed={claimed} />
          <TaskOperation operation={operation} />
          <ConclusionForm disabled={!claimed} evidenceIds={evidenceIds} onSubmit={complete} />
          <AuthoritySeparation />
        </aside>
      </div>
    </section>
  );
}
