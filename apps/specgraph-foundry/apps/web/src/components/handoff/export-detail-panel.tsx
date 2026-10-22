"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { StatusBadge } from "@/components/ui/status-badge";
import { useExportDetail } from "@/lib/handoff/queries";
import { useVerifyExportMutation } from "@/lib/handoff/mutations";
import { ExportDownloadPanel } from "./export-download-panel";

export function ExportDetailPanel({ projectId, exportId }: { projectId: string; exportId: string }) {
  const detail = useExportDetail(exportId);
  const [progressMessage, setProgressMessage] = useState<string | undefined>();
  const verify = useVerifyExportMutation(projectId, setProgressMessage);

  if (detail.isLoading) {
    return <Skeleton style={{ height: "10rem" }} />;
  }
  if (detail.isError) {
    return (
      <Alert tone="danger" title="Export unavailable">
        <p>This export could not load.</p>
      </Alert>
    );
  }

  const record = detail.data?.body;
  const manifest = record?.artifact_manifest;
  const status = String(record?.status ?? "UNKNOWN");

  return (
    <div className="sg-planning-form" aria-label="Export detail">
      <StatusBadge tone={status === "VERIFIED" ? "success" : status === "INVALID" ? "danger" : "neutral"} label={status} />
      {status === "VERIFIED" ? <ExportDownloadPanel exportId={exportId} /> : <p className="sg-muted">Downloads are available only for verified exports.</p>}
      {manifest ? (
        <dl>
          <div>
            <dt>Manifest state</dt>
            <dd>{manifest.state ?? "Unknown"}</dd>
          </div>
          <div>
            <dt>Artifacts</dt>
            <dd>{manifest.artifact_count ?? "Unknown"}</dd>
          </div>
          <div>
            <dt>Total bytes</dt>
            <dd>{manifest.total_bytes ?? "Unknown"}</dd>
          </div>
          <div>
            <dt>Aggregate SHA-256</dt>
            <dd className="sg-mono">{manifest.aggregate_sha256 ?? "Unknown"}</dd>
          </div>
        </dl>
      ) : (
        <p className="sg-muted">No artifact manifest is available yet.</p>
      )}
      <Button type="button" loading={verify.isPending} onClick={() => void verify.mutateAsync(exportId).catch(() => {})}>
        Verify export
      </Button>
      {verify.isPending && progressMessage ? (
        <p role="status" aria-live="polite" className="sg-micro-label">
          {progressMessage}
        </p>
      ) : null}
      {verify.isError ? (
        <Alert tone="danger" title="Verification failed">
          <p>{verify.error instanceof Error ? verify.error.message : "The export could not be verified. Its previous state is unchanged."}</p>
        </Alert>
      ) : null}
    </div>
  );
}
