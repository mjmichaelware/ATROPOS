"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { SpecGraphApiError } from "@/lib/api/errors";
import { createProjectApiClient } from "@/lib/projects/api";
import { useCreateOrUpdateRendererMutation } from "@/lib/routing/mutations";
import type { Renderer } from "@/lib/routing/schemas";

function conflictMessage(status: number): string | undefined {
  if (status === 409) return "This renderer conflicts with an existing renderer. Reload and review before retrying.";
  if (status === 412) return "This renderer changed on the server since it was loaded. Reload to see the latest version before saving again.";
  if (status === 428) return "An If-Match version is required to update this renderer. Reload to obtain the current version.";
  return undefined;
}

export function RendererForm({ projectId, existing, onDone }: { projectId: string; existing?: Renderer; onDone: () => void }) {
  const [name, setName] = useState(existing?.name ?? "");
  const [rendererType, setRendererType] = useState(existing?.renderer_type ?? "");
  const [enabled, setEnabled] = useState(existing?.enabled ?? true);
  const [error, setError] = useState<string | undefined>();
  const [conflict, setConflict] = useState<string | undefined>();
  const mutation = useCreateOrUpdateRendererMutation(projectId);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(undefined);
    setConflict(undefined);
    const idempotencyKey = createProjectApiClient().createIdempotencyKey();
    try {
      await mutation.mutateAsync({ input: { name, renderer_type: rendererType, enabled }, idempotencyKey, ifMatch: existing?.etag });
      onDone();
    } catch (thrown) {
      if (thrown instanceof SpecGraphApiError) {
        const message = conflictMessage(thrown.status);
        if (message) {
          setConflict(message);
          return;
        }
      }
      setError(thrown instanceof Error ? thrown.message : "Renderer save failed. The existing renderer is unchanged.");
    }
  }

  return (
    <form className="sg-planning-form" onSubmit={(event) => void submit(event)} aria-label={existing ? "Update renderer" : "Create renderer"}>
      <Field id="renderer-name" label="Name" value={name} onChange={(event) => setName(event.target.value)} required maxLength={160} />
      <Field id="renderer-type" label="Renderer type" value={rendererType} onChange={(event) => setRendererType(event.target.value)} required maxLength={160} />
      <label>
        <input type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} /> Enabled
      </label>
      {conflict ? (
        <Alert tone="warning" title="Conflict">
          <p>{conflict}</p>
          <Button type="button" variant="secondary" onClick={onDone}>
            Reload
          </Button>
        </Alert>
      ) : null}
      {error ? (
        <Alert tone="danger" title="Save failed">
          <p>{error}</p>
        </Alert>
      ) : null}
      <div className="sg-graph-command-group">
        <Button type="submit" loading={mutation.isPending} disabled={mutation.isPending}>
          {existing ? "Save renderer" : "Create renderer"}
        </Button>
        <Button type="button" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
