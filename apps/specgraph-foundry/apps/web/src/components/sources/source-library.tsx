"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { SourceEmptyState } from "./source-empty-state";
import { SourceTable } from "./source-table";
import type { DocumentPage } from "@/lib/sources/api";

export function SourceLibrary({ projectId, page, onNext, onBack, canBack, onUpload }: { projectId: string; page?: DocumentPage; onNext: () => void; onBack: () => void; canBack: boolean; onUpload: () => void }) {
  const [mode, setMode] = useState<"cards" | "table">("cards");
  const documents = page?.body.items ?? [];
  if (documents.length === 0) {
    return <SourceEmptyState onUpload={onUpload} />;
  }
  return (
    <section className="sg-source-library" aria-labelledby="source-library-title">
      <div className="sg-source-toolbar">
        <h2 id="source-library-title">Source library</h2>
        <SegmentedControl label="Library density" value={mode} options={[{ value: "cards", label: "Cards" }, { value: "table", label: "Table" }]} onChange={setMode} />
      </div>
      <SourceTable projectId={projectId} documents={documents} />
      <div className="sg-pagination">
        <Button type="button" variant="secondary" disabled={!canBack} onClick={onBack}>Previous</Button>
        <span>{page?.pagination.count ?? documents.length} loaded · {page?.pagination.hasMore ? "more available" : "final page"}</span>
        <Button type="button" variant="secondary" disabled={!page?.pagination.hasMore} onClick={onNext}>Next</Button>
      </div>
    </section>
  );
}
