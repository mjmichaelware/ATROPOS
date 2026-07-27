"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { createProjectApiClient } from "@/lib/projects/api";
import { useRecordProviderHealthMutation } from "@/lib/routing/mutations";
import type { Provider } from "@/lib/routing/schemas";
import { ProviderForm } from "./provider-form";

const HEALTH_STATUSES = ["READY", "DEGRADED", "COOLDOWN", "UNAVAILABLE"] as const;

export function ProviderList({ projectId, providers, onChanged }: { projectId: string; providers: Provider[]; onChanged: () => void }) {
  const [editing, setEditing] = useState<Provider | "new" | undefined>();
  const healthMutation = useRecordProviderHealthMutation(projectId);

  if (editing) {
    return <ProviderForm projectId={projectId} existing={editing === "new" ? undefined : editing} onDone={() => { setEditing(undefined); onChanged(); }} />;
  }

  return (
    <div className="sg-planning-form">
      {providers.length === 0 ? <p className="sg-muted">No providers are configured yet.</p> : null}
      <ul className="sg-plan-history" aria-label="Routing providers">
        {providers.map((provider) => (
          <li key={provider.id}>
            <Card>
              <div className="sg-page-heading">
                <div>
                  <strong>{provider.name ?? "Unnamed provider"}</strong>
                  <p className="sg-micro-label">
                    {provider.provider_class ?? "unknown class"} · cost class {provider.cost_class ?? "unknown"}
                  </p>
                </div>
                <StatusBadge tone={provider.status === "READY" ? "success" : provider.status ? "warning" : "neutral"} label={String(provider.status ?? "unknown")} />
              </div>
              <div className="sg-graph-command-group">
                <Button type="button" variant="secondary" onClick={() => setEditing(provider)}>
                  Edit
                </Button>
                <select
                  className="sg-select"
                  aria-label={`Record health status for ${provider.name ?? provider.id}`}
                  defaultValue=""
                  onChange={(event) => {
                    const status = event.target.value;
                    if (!status) return;
                    void healthMutation.mutateAsync({ providerId: provider.id, input: { status }, idempotencyKey: createProjectApiClient().createIdempotencyKey() }).catch(() => {});
                    event.target.value = "";
                  }}
                >
                  <option value="">Record health…</option>
                  {HEALTH_STATUSES.map((status) => (
                    <option key={status} value={status}>
                      {status}
                    </option>
                  ))}
                </select>
              </div>
            </Card>
          </li>
        ))}
      </ul>
      <Button type="button" onClick={() => setEditing("new")}>
        Add provider
      </Button>
    </div>
  );
}
