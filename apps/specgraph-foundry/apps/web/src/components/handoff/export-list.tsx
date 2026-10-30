"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { StatusBadge } from "@/components/ui/status-badge";
import { useExportPlanMutation } from "@/lib/handoff/mutations";
import type { ExportRecord } from "@/lib/handoff/schemas";
import { usePlanList } from "@/lib/planning/queries";
import { ExportDetailPanel } from "./export-detail-panel";

function formatPlanOption(plan: { id: string; status?: string; created_at?: string }): string {
  const when = plan.created_at ? new Date(plan.created_at).toLocaleString() : "unknown date";
  return `${plan.status ?? "UNKNOWN"} — ${when} (${plan.id.slice(0, 8)})`;
}

export function ExportList({ projectId, exports }: { projectId: string; exports: ExportRecord[] }) {
  const firstVerifiedId = exports.find((e) => e.status === "VERIFIED")?.id;
  const [expanded, setExpanded] = useState<string | undefined>(firstVerifiedId);
  const searchParams = useSearchParams();
  const [planId, setPlanId] = useState("");
  const [outputRoot, setOutputRoot] = useState("");
  const [progressMessage, setProgressMessage] = useState<string | undefined>();
  const mutation = useExportPlanMutation(projectId, setProgressMessage);
  const plans = usePlanList(projectId);
  const planItems = plans.data?.body.items ?? [];

  const requestedPlanId = searchParams.get("plan");
  const requestedPlanIsValid = requestedPlanId !== null && planItems.some((plan) => plan.id === requestedPlanId);
  const effectivePlanId = planId || (requestedPlanIsValid ? requestedPlanId! : "");

  async function generate(event: React.FormEvent) {
    event.preventDefault();
    if (!effectivePlanId.trim()) return;
    await mutation.mutateAsync({ planId: effectivePlanId.trim(), outputRoot: outputRoot.trim() || undefined }).catch(() => {});
  }

  return (
    <div className="sg-planning-form">
      <p className="sg-muted">
        An export packages a plan into a signed, checksummed artifact you can download and hand off outside SpecGraph entirely — only a VERIFIED plan can be exported. Click an export below to see
        and verify its checksums, or generate a new one from the form.
      </p>
      {exports.length === 0 ? <p className="sg-muted">No exports have been generated for this project yet.</p> : null}
      <ul className="sg-plan-history" aria-label="Project exports">
        {exports.map((exportRecord) => {
          const isVerified = exportRecord.status === "VERIFIED";
          return (
            <li key={exportRecord.id}>
              <Button
                type="button"
                variant={expanded === exportRecord.id ? "verified" : isVerified ? "primary" : "quiet"}
                aria-pressed={expanded === exportRecord.id}
                onClick={() => setExpanded((current) => (current === exportRecord.id ? undefined : exportRecord.id))}
              >
                {isVerified ? (
                  <>
                    <span>Build plan ready to download</span>
                    <StatusBadge tone="success" label="VERIFIED" />
                    <span className="sg-mono sg-micro-label">{exportRecord.id.slice(0, 8)}</span>
                  </>
                ) : (
                  <>
                    <span className="sg-mono">{exportRecord.id.slice(0, 8)}</span>
                    <StatusBadge tone="neutral" label={String(exportRecord.status ?? "UNKNOWN")} />
                  </>
                )}
              </Button>
              {expanded === exportRecord.id ? <ExportDetailPanel projectId={projectId} exportId={exportRecord.id} /> : null}
            </li>
          );
        })}
      </ul>
      <form className="sg-planning-form" onSubmit={(event) => void generate(event)} aria-label="Generate export from a verified plan">
        {planItems.length > 0 ? (
          <label className="sg-field">
            Plan to export
            <select className="sg-select" aria-label="Plan to export" value={effectivePlanId} onChange={(event) => setPlanId(event.target.value)} required>
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
        <Field id="export-output-root" label="Output root (optional)" value={outputRoot} onChange={(event) => setOutputRoot(event.target.value)} maxLength={160} />
        {mutation.isError ? (
          <Alert tone="danger" title="Export generation failed">
            <p>{mutation.error instanceof Error ? mutation.error.message : "The export could not be generated. No new export was created."}</p>
          </Alert>
        ) : null}
        <Button type="submit" loading={mutation.isPending} disabled={mutation.isPending || !effectivePlanId}>
          Generate export
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
