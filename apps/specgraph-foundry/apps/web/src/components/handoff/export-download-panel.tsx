"use client";

import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { readPublicEnv } from "@/lib/config/client-env";
import { isDownloadLikelyExpired, openSignedDownload, safeArtifactName } from "@/lib/handoff/downloads";
import { useDownloadExportArtifactsMutation } from "@/lib/handoff/mutations";

export function ExportDownloadPanel({ exportId }: { exportId: string }) {
  const mutation = useDownloadExportArtifactsMutation();

  async function requestDownload() {
    await mutation.mutateAsync(exportId).catch(() => {});
  }

  const artifacts = mutation.data?.body.artifacts ?? [];

  return (
    <div className="sg-planning-form" aria-label="Verified artifact download">
      <Button type="button" loading={mutation.isPending} onClick={() => void requestDownload()}>
        Request download links
      </Button>
      {mutation.isError ? (
        <Alert tone="danger" title="Download unavailable">
          <p>{mutation.error instanceof Error ? mutation.error.message : "The signed download could not be requested."}</p>
        </Alert>
      ) : null}
      {artifacts.length > 0 ? (
        <ul className="sg-plan-history" aria-label="Verified export artifacts">
          {artifacts.map((artifact) => {
            const expired = isDownloadLikelyExpired(artifact.expires_at);
            return (
              <li key={artifact.sha256}>
                <span>{safeArtifactName(artifact.name)}</span>
                <span className="sg-micro-label"> {artifact.media_type}, {artifact.byte_length} bytes</span>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={expired}
                  onClick={() => openSignedDownload(artifact.signed_download_url, readPublicEnv().NEXT_PUBLIC_SUPABASE_URL)}
                >
                  {expired ? "Link expired — request again" : "Download"}
                </Button>
              </li>
            );
          })}
        </ul>
      ) : null}
      <p className="sg-micro-label">Download links are short-lived and are never stored. Request a new link if one expires.</p>
    </div>
  );
}
