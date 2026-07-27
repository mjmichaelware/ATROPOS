"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { createProjectApiClient } from "@/lib/projects/api";
import { extractDocument } from "@/lib/sources/api";
import { useDocumentInspector } from "@/lib/sources/queries";
import { DocumentHeader } from "./document-header";
import { DocumentTabs } from "./document-tabs";
import { SourceErrorState } from "./source-error-state";

export function DocumentInspector({ documentId }: { projectId: string; documentId: string }) {
  const [atomCursorStack, setAtomCursorStack] = useState<string[]>([]);
  const [operationMessage, setOperationMessage] = useState<string | null>(null);
  const atomCursor = atomCursorStack.at(-1);
  const { document, provenance, atoms } = useDocumentInspector(documentId, atomCursor, atomCursorStack.length);

  async function extract() {
    const client = createProjectApiClient();
    setOperationMessage("Extraction queued.");
    const accepted = await extractDocument(client, documentId, client.createIdempotencyKey());
    const terminal = accepted.location ? await client.pollOperation(accepted.location) : accepted;
    setOperationMessage(`Extraction ${terminal.body.operation.state}.`);
    await Promise.all([document.refetch(), provenance.refetch(), atoms.refetch()]);
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
        <Button type="button" variant="primary" onClick={() => void extract()}>Extract atoms</Button>
        {operationMessage ? <p role="status">{operationMessage}</p> : null}
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
