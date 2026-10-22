"use client";

import { useState } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip } from "@/components/ui/tooltip";
import { DataGrid } from "@/components/visual/data-grid";
import { HudPanel } from "@/components/visual/hud-panel";
import { useProjectCommandCenter } from "@/lib/projects/queries";
import { useDocumentPage, useSourceWorkspace } from "@/lib/sources/queries";
import { SourceCommandBar } from "./source-command-bar";
import { SourceErrorState } from "./source-error-state";
import { SourceLibrary } from "./source-library";
import { SourceOperationPanel } from "./source-operation-panel";
import { SourceTabs, type SourceTab } from "./source-tabs";
import { SourceUploadPanel } from "./source-upload-panel";

export function SourceWorkspace({ projectId }: { projectId: string }) {
  const [tab, setTab] = useState<SourceTab>("library");
  const [cursorStack, setCursorStack] = useState<string[]>([]);
  const cursor = cursorStack.at(-1);
  const workspace = useSourceWorkspace(projectId);
  const documents = useDocumentPage(projectId, cursor, cursorStack.length);
  const project = useProjectCommandCenter(projectId);
  const refresh = () => void Promise.all([workspace.refetch(), documents.refetch(), project.operations.refetch()]);

  if (workspace.isLoading || documents.isLoading) {
    return <Skeleton style={{ height: "24rem" }} />;
  }
  if (workspace.isError || documents.isError) {
    return <SourceErrorState onRetry={refresh} />;
  }

  return (
    <section className="sg-source-workspace" aria-labelledby="source-workspace-title">
      <DataGrid />
      <header className="sg-source-hero">
        <p className="sg-micro-label">Source authority</p>
        <div className="sg-hero-title-row">
          <h1 id="source-workspace-title">Your original documents, never altered</h1>
          <Tooltip label="Extraction reads a document to pull out individual factual claims (called atoms) with their exact location in the text. It never edits or replaces the file you uploaded.">
            <button type="button" className="sg-help-hint" aria-label="What does extraction mean?">
              ?
            </button>
          </Tooltip>
        </div>
        <p>Everything you upload stays exactly as you uploaded it. Extraction and research build on top of it — they never rewrite it.</p>
        <SourceCommandBar onUpload={() => setTab("upload")} onRefresh={refresh} />
      </header>
      <div className="sg-source-metrics">
        <HudPanel title="Documents" status={String(workspace.data?.body.documents_count ?? documents.data?.body.items.length ?? 0)}><p>Everything you&apos;ve uploaded to this project.</p></HudPanel>
        <HudPanel title="Uploads" status={String(workspace.data?.body.uploads_count ?? 0)}><p>In progress or recently finished.</p></HudPanel>
        <HudPanel title="Atoms" status={String(workspace.data?.body.atoms_count ?? 0)}><p>Individual claims extracted so far, ready for research.</p></HudPanel>
      </div>
      <SourceTabs
        value={tab}
        onChange={setTab}
        library={<SourceLibrary projectId={projectId} page={documents.data} canBack={cursorStack.length > 0} onBack={() => setCursorStack((stack) => stack.slice(0, -1))} onNext={() => {
          const next = documents.data?.pagination.nextCursor;
          if (next) setCursorStack((stack) => [...stack, next]);
        }} onUpload={() => setTab("upload")} />}
        upload={<SourceUploadPanel projectId={projectId} onComplete={refresh} />}
        activity={<SourceOperationPanel operations={project.operations.data?.body.items ?? []} />}
      />
    </section>
  );
}
