"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Tabs } from "@/components/ui/tabs";
import { useOnlineStatus } from "@/lib/graph/connectivity";
import { useHandoffWorkspace } from "@/lib/handoff/queries";
import { BindingList } from "./binding-list";
import { ExecutionRunList } from "./execution-run-list";
import { ExportList } from "./export-list";
import { HandoffEmptyState } from "./handoff-empty-state";
import { HandoffErrorState } from "./handoff-error-state";
import { HandoffLoadingState } from "./handoff-loading-state";
import { HandoffOfflineState } from "./handoff-offline-state";
import { HandoffOverview } from "./handoff-overview";

type HandoffTab = "overview" | "bindings" | "exports" | "runs";

export function HandoffWorkspace({ projectId }: { projectId: string }) {
  const online = useOnlineStatus();
  const [tab, setTab] = useState<HandoffTab>("overview");
  const workspace = useHandoffWorkspace(projectId);

  if (!online) {
    return <HandoffOfflineState />;
  }
  if (workspace.isLoading) {
    return <HandoffLoadingState />;
  }
  if (workspace.isError) {
    return <HandoffErrorState onRetry={() => void workspace.refetch()} />;
  }

  const body = workspace.data?.body;
  const isEmpty = !body?.bindings?.length && !body?.exports?.length && !body?.execution_runs?.length;

  return (
    <section className="sg-graph-workspace" aria-label="Handoff workspace">
      <header className="sg-source-hero sg-graph-hero">
        <p className="sg-micro-label">Handoff</p>
        <h1>{String(body?.project?.name ?? "Project")}</h1>
        <p>Bindings, exports, and execution runs use only real backend-authoritative state.</p>
        <Button type="button" variant="secondary" onClick={() => void workspace.refetch()}>
          Refresh
        </Button>
      </header>
      {isEmpty ? <HandoffEmptyState /> : null}
      <Tabs
        label="Handoff workspace views"
        value={tab}
        onChange={setTab}
        tabs={[
          { value: "overview", label: "Overview", panel: <HandoffOverview workspace={body} /> },
          { value: "bindings", label: "Bindings", panel: <BindingList projectId={projectId} bindings={body?.bindings ?? []} onChanged={() => void workspace.refetch()} /> },
          { value: "exports", label: "Exports", panel: <ExportList projectId={projectId} exports={body?.exports ?? []} /> },
          { value: "runs", label: "Runs", panel: <ExecutionRunList projectId={projectId} runs={body?.execution_runs ?? []} /> },
        ]}
      />
    </section>
  );
}
