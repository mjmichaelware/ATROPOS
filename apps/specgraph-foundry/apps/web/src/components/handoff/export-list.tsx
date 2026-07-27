"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { StatusBadge } from "@/components/ui/status-badge";
import { useExportPlanMutation } from "@/lib/handoff/mutations";
import type { ExportRecord } from "@/lib/handoff/schemas";
import { ExportDetailPanel } from "./export-detail-panel";

export function ExportList({ projectId, exports }: { projectId: string; exports: ExportRecord[] }) {
  const [expanded, setExpanded] = useState<string | undefined>();
  const [planId, setPlanId] = useState("");
  const [outputRoot, setOutputRoot] = useState("");
  const mutation = useExportPlanMutation(projectId);

  async function generate(event: React.FormEvent) {
    event.preventDefault();
    if (!planId.trim()) return;
    await mutation.mutateAsync({ planId: planId.trim(), outputRoot: outputRoot.trim() || undefined }).catch(() => {});
  }

  return (
    <div className="sg-planning-form">
      {exports.length === 0 ? <p className="sg-muted">No exports have been generated for this project yet.</p> : null}
      <ul className="sg-plan-history" aria-label="Project exports">
        {exports.map((exportRecord) => (
          <li key={exportRecord.id}>
            <Button
              type="button"
              variant={expanded === exportRecord.id ? "verified" : "quiet"}
              aria-pressed={expanded === exportRecord.id}
              onClick={() => setExpanded((current) => (current === exportRecord.id ? undefined : exportRecord.id))}
            >
              <span className="sg-mono">{exportRecord.id.slice(0, 8)}</span>
              <StatusBadge tone="neutral" label={String(exportRecord.status ?? "UNKNOWN")} />
            </Button>
            {expanded === exportRecord.id ? <ExportDetailPanel projectId={projectId} exportId={exportRecord.id} /> : null}
          </li>
        ))}
      </ul>
      <form className="sg-planning-form" onSubmit={(event) => void generate(event)} aria-label="Generate export from a verified plan">
        <Field id="export-plan-id" label="Plan ID (from Graph → Plans)" value={planId} onChange={(event) => setPlanId(event.target.value)} placeholder="plan-…" required maxLength={160} />
        <Field id="export-output-root" label="Output root (optional)" value={outputRoot} onChange={(event) => setOutputRoot(event.target.value)} maxLength={160} />
        {mutation.isError ? (
          <Alert tone="danger" title="Export generation failed">
            <p>{mutation.error instanceof Error ? mutation.error.message : "The export could not be generated. No new export was created."}</p>
          </Alert>
        ) : null}
        <Button type="submit" loading={mutation.isPending} disabled={mutation.isPending}>
          Generate export
        </Button>
      </form>
    </div>
  );
}
