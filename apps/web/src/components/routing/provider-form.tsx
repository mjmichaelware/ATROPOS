"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { SpecGraphApiError } from "@/lib/api/errors";
import { createProjectApiClient } from "@/lib/projects/api";
import { useCreateOrUpdateProviderMutation } from "@/lib/routing/mutations";
import type { Provider } from "@/lib/routing/schemas";

function conflictMessage(status: number): string | undefined {
  if (status === 409) return "This provider conflicts with an existing provider. Reload and review before retrying.";
  if (status === 412) return "This provider changed on the server since it was loaded. Reload to see the latest version before saving again.";
  if (status === 428) return "An If-Match version is required to update this provider. Reload to obtain the current version.";
  return undefined;
}

export function ProviderForm({ projectId, existing, onDone }: { projectId: string; existing?: Provider; onDone: () => void }) {
  const [name, setName] = useState(existing?.name ?? "");
  const [providerClass, setProviderClass] = useState(existing?.provider_class ?? "");
  const [costClass, setCostClass] = useState(existing?.cost_class ?? "");
  const [territories, setTerritories] = useState((existing?.territories ?? ["*"]).join(", "));
  const [priority, setPriority] = useState(String(existing?.priority ?? 0));
  const [enabled, setEnabled] = useState(existing?.enabled ?? true);
  const [error, setError] = useState<string | undefined>();
  const [conflict, setConflict] = useState<string | undefined>();
  const mutation = useCreateOrUpdateProviderMutation(projectId);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(undefined);
    setConflict(undefined);
    const idempotencyKey = createProjectApiClient().createIdempotencyKey();
    const territoryList = territories
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean);
    try {
      await mutation.mutateAsync({
        input: {
          name,
          provider_class: providerClass,
          cost_class: costClass,
          territories: territoryList.length > 0 ? territoryList : ["*"],
          priority: Number.parseInt(priority, 10) || 0,
          enabled,
        },
        idempotencyKey,
        ifMatch: existing?.etag,
      });
      onDone();
    } catch (thrown) {
      if (thrown instanceof SpecGraphApiError) {
        const message = conflictMessage(thrown.status);
        if (message) {
          setConflict(message);
          return;
        }
      }
      setError(thrown instanceof Error ? thrown.message : "Provider save failed. The existing provider is unchanged.");
    }
  }

  return (
    <form className="sg-planning-form" onSubmit={(event) => void submit(event)} aria-label={existing ? "Update provider" : "Create provider"}>
      <Field id="provider-name" label="Name" value={name} onChange={(event) => setName(event.target.value)} required maxLength={160} />
      <Field id="provider-class" label="Provider class" value={providerClass} onChange={(event) => setProviderClass(event.target.value)} required maxLength={160} />
      <Field id="provider-cost-class" label="Cost class" value={costClass} onChange={(event) => setCostClass(event.target.value)} required maxLength={160} />
      <Field
        id="provider-territories"
        label="Territories"
        value={territories}
        onChange={(event) => setTerritories(event.target.value)}
        description="Comma-separated. Use * to match every territory."
        maxLength={500}
      />
      <Field id="provider-priority" label="Priority" type="number" min={0} value={priority} onChange={(event) => setPriority(event.target.value)} description="Lower runs first within the same provider class." />
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
          {existing ? "Save provider" : "Create provider"}
        </Button>
        <Button type="button" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
