"use client";

import { useReducer } from "react";
import { Button } from "@/components/ui/button";
import { createProjectApiClient } from "@/lib/projects/api";
import { createUploadIntent, finalizeUpload } from "@/lib/sources/api";
import { validateSourceFile } from "@/lib/sources/file-validation";
import { sha256Hex } from "@/lib/sources/hash";
import { uploadToSignedUrl } from "@/lib/sources/upload";
import { uploadReducer, type UploadItem } from "@/lib/sources/upload-machine";
import { SourceDropZone } from "./source-drop-zone";
import { SourceUploadQueue } from "./source-upload-queue";

export function SourceUploadPanel({ projectId, onComplete }: { projectId: string; onComplete: () => void }) {
  const [items, dispatch] = useReducer(uploadReducer, []);

  async function start(files: File[]) {
    const client = createProjectApiClient();
    for (const file of files.slice(0, 5)) {
      const validation = validateSourceFile(file, items.map((item) => new File([], item.filename)));
      const id = crypto.randomUUID();
      const item: UploadItem = {
        id,
        filename: validation.filename,
        mediaType: validation.mediaType,
        size: file.size,
        phase: validation.ok ? "SELECTED" : "FAILED",
        progress: 0,
        message: validation.reason,
      };
      dispatch({ type: "add", item });
      if (!validation.ok || !validation.mediaType) {
        continue;
      }
      try {
        dispatch({ type: "transition", id, phase: "VALIDATING" });
        dispatch({ type: "transition", id, phase: "HASHING" });
        const sha256 = await sha256Hex(file);
        dispatch({ type: "transition", id, phase: "INTENT_CREATING" });
        const intent = await createUploadIntent(
          client,
          projectId,
          { filename: validation.filename, media_type: validation.mediaType, byte_size: file.size, sha256 },
          client.createIdempotencyKey(),
        );
        dispatch({ type: "transition", id, phase: "UPLOADING" });
        await uploadToSignedUrl(intent.body.signed_upload_url, file, intent.body.required_upload_headers, (progress) => {
          dispatch({ type: "progress", id, progress: progress.percent });
        });
        dispatch({ type: "transition", id, phase: "UPLOAD_COMPLETE" });
        dispatch({ type: "transition", id, phase: "FINALIZE_QUEUED" });
        const finalize = await finalizeUpload(client, intent.body.id, client.createIdempotencyKey());
        dispatch({ type: "transition", id, phase: "FINALIZING", message: String(finalize.body.operation.phase ?? "queued") });
        const operation = finalize.location ? await client.pollOperation(finalize.location) : finalize;
        dispatch({
          type: "transition",
          id,
          phase: operation.body.operation.state === "SUCCEEDED" ? "COMPLETE" : "FAILED",
          message: operation.body.operation.state,
        });
        onComplete();
      } catch {
        dispatch({ type: "transition", id, phase: "FAILED", message: "Upload did not complete. Server authority remains unchanged." });
      }
    }
  }

  return (
    <section className="sg-upload-panel" aria-labelledby="upload-title">
      <div className="sg-source-toolbar">
        <div>
          <p className="sg-micro-label">Private direct upload</p>
          <h2 id="upload-title">Authority intake bay</h2>
        </div>
        <Button type="button" variant="quiet" onClick={() => onComplete()}>Refresh after operation</Button>
      </div>
      <SourceDropZone onFiles={(files) => void start(files)} />
      <SourceUploadQueue items={items} />
    </section>
  );
}
