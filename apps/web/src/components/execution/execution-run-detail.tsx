"use client";

import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/status-badge";
import { useOnlineStatus } from "@/lib/graph/connectivity";
import { useExecutionRunDetail } from "@/lib/execution/queries";
import { useVerifyExecutionRunMutation } from "@/lib/execution/mutations";
import { normalizeRunStatus, runStatusTone } from "@/lib/execution/status";
import { ExecutionErrorState } from "./execution-error-state";
import { ExecutionFindingsList } from "./execution-findings-list";
import { ExecutionLoadingState } from "./execution-loading-state";
import { ExecutionNodeList } from "./execution-node-list";
import { ExecutionOfflineState } from "./execution-offline-state";
import { ExecutionReceiptList } from "./execution-receipt-list";

export function ExecutionRunDetail({ projectId, runId }: { projectId: string; runId: string }) {
  const online = useOnlineStatus();
  const run = useExecutionRunDetail(runId);
  const verify = useVerifyExecutionRunMutation(projectId, runId);

  if (!online) {
    return <ExecutionOfflineState />;
  }
  if (run.isLoading) {
    return <ExecutionLoadingState />;
  }
  if (run.isError) {
    return <ExecutionErrorState onRetry={() => void run.refetch()} />;
  }

  const body = run.data?.body;
  const status = normalizeRunStatus(body?.status);
  const nodes = body?.nodes ?? [];
  const receipts = body?.receipts ?? [];
  const findings = body?.findings ?? [];

  return (
    <section className="sg-graph-workspace" aria-label="Execution run detail">
      <header className="sg-source-hero sg-graph-hero">
        <p className="sg-micro-label">Execution run</p>
        <h1>
          {body?.runtime_system ?? "Unknown runtime"} · <span className="sg-mono">{runId.slice(0, 8)}</span>
        </h1>
        <p>
          <StatusBadge tone={runStatusTone(status)} label={status} /> Runtime run ID: <span className="sg-mono">{body?.runtime_run_id ?? "unknown"}</span>
        </p>
        <div className="sg-graph-hero-controls">
          <Button type="button" variant="secondary" onClick={() => void run.refetch()}>
            Refresh
          </Button>
          <Button type="button" loading={verify.isPending} onClick={() => void verify.mutateAsync().catch(() => {})}>
            Verify run
          </Button>
        </div>
      </header>
      {verify.isError ? (
        <Alert tone="danger" title="Verification failed">
          <p>{verify.error instanceof Error ? verify.error.message : "The run could not be verified. Its previous state is unchanged."}</p>
        </Alert>
      ) : null}
      <section aria-labelledby="execution-nodes-title">
        <h2 id="execution-nodes-title">Stages and nodes</h2>
        <ExecutionNodeList nodes={nodes} readyNodes={body?.ready_nodes} />
      </section>
      <section aria-labelledby="execution-receipts-title">
        <h2 id="execution-receipts-title">Receipts</h2>
        <ExecutionReceiptList receipts={receipts} />
      </section>
      <section aria-labelledby="execution-findings-title">
        <h2 id="execution-findings-title">Findings</h2>
        <ExecutionFindingsList findings={findings} />
      </section>
    </section>
  );
}
