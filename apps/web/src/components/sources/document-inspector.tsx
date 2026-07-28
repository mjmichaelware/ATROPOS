"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { describeOperationProgress, type OperationLike } from "@/lib/api/operations";
import { describeClientError } from "@/lib/api/errors";
import { createProjectApiClient } from "@/lib/projects/api";
import { exportDocumentAtoms, extractDocument } from "@/lib/sources/api";
import { downloadBase64File } from "@/lib/sources/downloads";
import { useDocumentInspector } from "@/lib/sources/queries";
import type { FreeformRecord } from "@/lib/sources/schemas";
import { DocumentHeader } from "./document-header";
import { DocumentTabs } from "./document-tabs";
import { SourceErrorState } from "./source-error-state";

// Its caller (the source-document page route) passes key={documentId}, so
// this component fully remounts - and all local state below resets - when
// the user navigates from one document straight to another. Without that
// key, atomCursorStack/operationMessage/operationFailed would carry over
// from the previous document instead.

export function DocumentInspector({ documentId }: { projectId: string; documentId: string }) {
  const [atomCursorStack, setAtomCursorStack] = useState<string[]>([]);
  const [operationMessage, setOperationMessage] = useState<string | null>(null);
  const [operationFailed, setOperationFailed] = useState(false);
  const [extracting, setExtracting] = useState(false);
  const [downloadFormat, setDownloadFormat] = useState<"text" | "pdf" | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const atomCursor = atomCursorStack.at(-1);
  const { document, provenance, atoms } = useDocumentInspector(documentId, atomCursor, atomCursorStack.length);

  async function extract() {
    const client = createProjectApiClient();
    setOperationFailed(false);
    setExtracting(true);
    setOperationMessage("Extraction queued — waiting for a worker to pick it up.");
    try {
      // extract_document_atoms is an async operation: the worker that
      // processes it only runs on its own schedule, so this poll can
      // legitimately take minutes, not seconds. onProgress keeps the
      // message live on every poll tick instead of sitting on the same
      // static text the whole time.
      const accepted = await extractDocument(client, documentId, client.createIdempotencyKey());
      const terminal = accepted.location
        ? await client.pollOperation<{ operation: OperationLike & FreeformRecord }>(accepted.location, {
            onProgress: (operation) => setOperationMessage(describeOperationProgress("Extraction", operation)),
          })
        : accepted;
      // pollOperation resolves (does not throw) for every terminal state,
      // including FAILED/CANCELLED/TIMED_OUT - only SUCCEEDED counts as success.
      const state = terminal.body.operation.state;
      setOperationFailed(state !== "SUCCEEDED");
      if (state === "SUCCEEDED") {
        // The extractor only keeps sentence-like fragments outside
        // headings/code fences - a document made entirely of those
        // correctly yields zero atoms, but with no explanation that looks
        // identical to a broken run. Surfacing the real count here (not
        // just "SUCCEEDED") is the difference between the two.
        const result = terminal.body.operation.result as { atom_count?: number } | undefined;
        const atomCount = result?.atom_count;
        setOperationMessage(
          typeof atomCount === "number"
            ? atomCount > 0
              ? `Extraction complete — found ${atomCount} atom${atomCount === 1 ? "" : "s"}.`
              : "Extraction complete — found 0 atoms. This document has no sentence-like lines outside headings/code blocks for the extractor to pick up."
            : "Extraction SUCCEEDED.",
        );
      } else {
        setOperationMessage(`Extraction ${state}.`);
      }
      await Promise.all([document.refetch(), provenance.refetch(), atoms.refetch()]);
    } catch (error) {
      setOperationFailed(true);
      setOperationMessage(`Extraction did not complete. (${describeClientError(error)})`);
    } finally {
      setExtracting(false);
    }
  }

  async function downloadAtoms(format: "text" | "pdf") {
    const client = createProjectApiClient();
    setDownloadError(null);
    setDownloadFormat(format);
    try {
      const response = await exportDocumentAtoms(client, documentId);
      const file = response.body[format];
      const ok = downloadBase64File(file.filename, file.media_type, file.base64);
      if (!ok) {
        setDownloadError("The downloaded file could not be prepared. Nothing was saved.");
      }
    } catch (error) {
      setDownloadError(describeClientError(error));
    } finally {
      setDownloadFormat(null);
    }
  }

  if (document.isLoading || provenance.isLoading || atoms.isLoading) {
    return <Skeleton style={{ height: "24rem" }} />;
  }
  if (document.isError) {
    return <SourceErrorState title="Document not found" onRetry={() => void document.refetch()} />;
  }
  const body = document.data?.body;
  return (
    <section className="sg-document-inspector">
      <DocumentHeader document={body!} />
      <div className="sg-source-toolbar">
        <Button type="button" variant="primary" loading={extracting} onClick={() => void extract()}>Extract atoms</Button>
        <Button type="button" variant="secondary" loading={downloadFormat === "text"} disabled={downloadFormat !== null} onClick={() => void downloadAtoms("text")}>
          Download atoms (.txt)
        </Button>
        <Button type="button" variant="secondary" loading={downloadFormat === "pdf"} disabled={downloadFormat !== null} onClick={() => void downloadAtoms("pdf")}>
          Download atoms (.pdf)
        </Button>
        {operationMessage ? (
          <p role={operationFailed ? "alert" : "status"} aria-live="polite">
            {operationMessage}
          </p>
        ) : null}
        {downloadError ? (
          <Alert tone="danger" title="Download unavailable">
            <p>{downloadError}</p>
          </Alert>
        ) : null}
      </div>
      <DocumentTabs
        document={body!}
        provenance={provenance.data?.body}
        atoms={atoms.data?.body.items ?? []}
        hasMoreAtoms={atoms.data?.pagination.hasMore}
        onNextAtoms={() => {
          const next = atoms.data?.pagination.nextCursor;
          if (next) setAtomCursorStack((stack) => [...stack, next]);
        }}
      />
    </section>
  );
}
