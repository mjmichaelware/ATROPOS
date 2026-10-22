"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { SpecGraphApiError } from "@/lib/api/errors";
import { createProjectApiClient } from "@/lib/projects/api";
import { useCreateOrUpdateBindingMutation } from "@/lib/handoff/mutations";
import { isBoundedBindingConfig } from "@/lib/handoff/security";
import type { Binding, BindingInput } from "@/lib/handoff/schemas";

function conflictMessage(status: number): string | undefined {
  if (status === 409) return "This binding conflicts with an existing binding. Reload and review before retrying.";
  if (status === 412) return "This binding changed on the server since it was loaded. Reload to see the latest version before saving again.";
  if (status === 428) return "An If-Match version is required to update this binding. Reload to obtain the current version.";
  return undefined;
}

const EMPTY: BindingInput = { system_name: "", binding_type: "", config: {}, enabled: true };

export function BindingForm({ projectId, existing, onDone }: { projectId: string; existing?: Binding; onDone: () => void }) {
  const [systemName, setSystemName] = useState(existing?.system_name ?? EMPTY.system_name);
  const [bindingType, setBindingType] = useState(existing?.binding_type ?? EMPTY.binding_type);
  const [configText, setConfigText] = useState(existing ? JSON.stringify(existing.config ?? {}, null, 2) : "{}");
  const [enabled, setEnabled] = useState(existing?.enabled ?? true);
  const [error, setError] = useState<string | undefined>();
  const [conflict, setConflict] = useState<string | undefined>();
  const mutation = useCreateOrUpdateBindingMutation(projectId);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(undefined);
    setConflict(undefined);
    let config: Record<string, unknown>;
    try {
      config = JSON.parse(configText) as Record<string, unknown>;
    } catch {
      setError("Configuration must be valid JSON.");
      return;
    }
    if (!isBoundedBindingConfig(config)) {
      setError("Configuration is too large.");
      return;
    }
    const input: BindingInput = { system_name: systemName, binding_type: bindingType, config, enabled };
    const idempotencyKey = createProjectApiClient().createIdempotencyKey();
    try {
      await mutation.mutateAsync({ input, idempotencyKey, ifMatch: existing?.etag });
      onDone();
    } catch (thrown) {
      if (thrown instanceof SpecGraphApiError) {
        const message = conflictMessage(thrown.status);
        if (message) {
          setConflict(message);
          return;
        }
      }
      setError(thrown instanceof Error ? thrown.message : "Binding save failed. The existing binding is unchanged.");
    }
  }

  return (
    <form className="sg-planning-form" onSubmit={(event) => void submit(event)} aria-label={existing ? "Update integration binding" : "Create integration binding"}>
      <Field id="binding-system-name" label="System name" value={systemName} onChange={(event) => setSystemName(event.target.value)} required maxLength={160} />
      <Field id="binding-type" label="Binding type" value={bindingType} onChange={(event) => setBindingType(event.target.value)} required maxLength={160} />
      <div className="sg-field">
        <Label htmlFor="binding-config">Configuration (JSON)</Label>
        <Textarea id="binding-config" value={configText} onChange={(event) => setConfigText(event.target.value)} />
      </div>
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
          {existing ? "Save binding" : "Create binding"}
        </Button>
        <Button type="button" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
