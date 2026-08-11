"use client";

import { useEffect, useRef } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { readPublicEnv } from "@/lib/config/client-env";
import { isDownloadLikelyExpired, openSignedDownload, safeArtifactName } from "@/lib/handoff/downloads";
import { useDownloadExportArtifactsMutation } from "@/lib/handoff/mutations";
import type { ExportDownloadArtifact } from "@/lib/handoff/schemas";

const BLUEPRINT_LABELS: Record<string, string> = {
  "implementation_blueprint.pdf": "Download build plan (PDF)",
  "implementation_blueprint.txt": "Download build plan (text)",
};

function DownloadRow({ artifact, primary }: { artifact: ExportDownloadArtifact; primary?: boolean }) {
  const expired = isDownloadLikelyExpired(artifact.expires_at);
  const label = BLUEPRINT_LABELS[artifact.name];
  return (
    <li>
      <span>{safeArtifactName(artifact.name)}</span>
      <span className="sg-micro-label"> {artifact.media_type}, {artifact.byte_length} bytes</span>
      <Button
        type="button"
        variant={primary ? "primary" : "secondary"}
        disabled={expired}
        onClick={() => openSignedDownload(artifact.signed_download_url, readPublicEnv().NEXT_PUBLIC_SUPABASE_URL, readPublicEnv().NEXT_PUBLIC_SPECGRAPH_API_URL)}
      >
        {expired ? "Link expired — refresh below" : (label ?? "Download")}
      </Button>
    </li>
  );
}

export function ExportDownloadPanel({ exportId }: { exportId: string }) {
  const mutation = useDownloadExportArtifactsMutation();
  const requestedFor = useRef<string | undefined>(undefined);

  // The download links only require a GET (no side effects, nothing to
  // confirm), so making the user press a "Request download links" button
  // before the real "Download" buttons even appear was a pointless extra
  // step - fetch them the moment this panel mounts instead.
  useEffect(() => {
    if (requestedFor.current === exportId) return;
    requestedFor.current = exportId;
    void mutation.mutateAsync(exportId).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exportId]);

  const artifacts = mutation.data?.body.artifacts ?? [];
  const blueprintArtifacts = artifacts.filter((artifact) => artifact.name in BLUEPRINT_LABELS);
  const otherArtifacts = artifacts.filter((artifact) => !(artifact.name in BLUEPRINT_LABELS));

  return (
    <div className="sg-planning-form" aria-label="Verified artifact download">
      {mutation.isPending && artifacts.length === 0 ? <Skeleton style={{ height: "4rem" }} /> : null}
      {mutation.isError ? (
        <Alert tone="danger" title="Download unavailable">
          <p>{mutation.error instanceof Error ? mutation.error.message : "The signed download could not be requested."}</p>
        </Alert>
      ) : null}
      {blueprintArtifacts.length > 0 ? (
        <ul className="sg-plan-history" aria-label="Build plan downloads">
          {blueprintArtifacts.map((artifact) => (
            <DownloadRow key={artifact.sha256} artifact={artifact} primary />
          ))}
        </ul>
      ) : null}
      {otherArtifacts.length > 0 ? (
        <ul className="sg-plan-history" aria-label="Other export artifacts">
          {otherArtifacts.map((artifact) => (
            <DownloadRow key={artifact.sha256} artifact={artifact} />
          ))}
        </ul>
      ) : null}
      {artifacts.length > 0 || mutation.isError ? (
        <Button type="button" variant="quiet" loading={mutation.isPending} onClick={() => void mutation.mutateAsync(exportId).catch(() => {})}>
          Refresh links
        </Button>
      ) : null}
      <p className="sg-micro-label">Download links are short-lived and are never stored. Use &quot;Refresh links&quot; if one expires.</p>
    </div>
  );
}
