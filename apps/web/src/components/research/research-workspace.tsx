"use client";

import { useState } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { DataGrid } from "@/components/visual/data-grid";
import { useProjectCommandCenter } from "@/lib/projects/queries";
import type { GapMatrix as GapMatrixType } from "@/lib/research/schemas";
import { useGapMatrix, useResearchTasks, useResearchWorkspace } from "@/lib/research/queries";
import { AtomDirectory } from "./atom-directory";
import { DimensionPanel } from "./dimension-panel";
import { GapMatrix } from "./gap-matrix";
import { ResearchCommandBar } from "./research-command-bar";
import { ResearchErrorState } from "./research-error-state";
import { ResearchOverview } from "./research-overview";
import { ResearchTabs, type ResearchTab } from "./research-tabs";
import { TaskQueue } from "./task-queue";

export function ResearchWorkspace({ projectId }: { projectId: string }) {
  const [tab, setTab] = useState<ResearchTab>("overview");
  const [cursorStack, setCursorStack] = useState<string[]>([]);
  const cursor = cursorStack.at(-1);
  const workspace = useResearchWorkspace(projectId);
  const matrix = useGapMatrix(projectId);
  const tasks = useResearchTasks(projectId, cursor, cursorStack.length);
  const project = useProjectCommandCenter(projectId);
  const refresh = () => void Promise.all([workspace.refetch(), matrix.refetch(), tasks.refetch(), project.operations.refetch()]);

  if (workspace.isLoading || matrix.isLoading || tasks.isLoading) {
    return <Skeleton style={{ height: "24rem" }} />;
  }
  if (workspace.isError || matrix.isError || tasks.isError) {
    return <ResearchErrorState onRetry={refresh} />;
  }

  const matrixBody = matrix.data?.body as GapMatrixType | undefined;
  return (
    <section className="sg-research-workspace" aria-labelledby="research-workspace-title">
      <DataGrid />
      <header className="sg-source-hero sg-research-hero">
        <p className="sg-micro-label">Research workspace</p>
        <h1 id="research-workspace-title">Fill in what your sources don&apos;t say yet</h1>
        <p>Every atom gets checked against 16 dimensions. Answer them here — by hand or automatically — and each conclusion stays traceable back to real evidence.</p>
        <ResearchCommandBar onRefresh={refresh} />
      </header>
      <ResearchTabs
        value={tab}
        onChange={setTab}
        overview={<ResearchOverview projectId={projectId} workspace={workspace.data?.body} matrix={matrixBody} />}
        atoms={<div className="sg-research-split"><AtomDirectory projectId={projectId} matrix={matrixBody} /><DimensionPanel matrix={matrixBody} /></div>}
        matrix={<GapMatrix projectId={projectId} matrix={matrixBody} />}
        tasks={<TaskQueue projectId={projectId} page={tasks.data} canBack={cursorStack.length > 0} onBack={() => setCursorStack((stack) => stack.slice(0, -1))} onNext={() => {
          const next = tasks.data?.pagination.nextCursor;
          if (next) setCursorStack((stack) => [...stack, next]);
        }} />}
      />
    </section>
  );
}
