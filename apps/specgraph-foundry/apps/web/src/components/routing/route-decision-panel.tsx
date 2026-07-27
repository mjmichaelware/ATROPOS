"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { StatusBadge } from "@/components/ui/status-badge";
import { createProjectApiClient } from "@/lib/projects/api";
import { useCreateRouteDecisionMutation } from "@/lib/routing/mutations";
import { useRouteDecisionLookup } from "@/lib/routing/queries";
import type { RouteDecision } from "@/lib/routing/schemas";

function DecisionSummary({ decision }: { decision: RouteDecision }) {
  return (
    <dl>
      <div>
        <dt>Status</dt>
        <dd>
          <StatusBadge tone="neutral" label={String(decision.status ?? "UNKNOWN")} />
        </dd>
      </div>
      <div>
        <dt>Selected provider</dt>
        <dd className="sg-mono">{decision.selected_provider_id ?? "Unknown"}</dd>
      </div>
      <div>
        <dt>Selected renderer</dt>
        <dd className="sg-mono">{decision.selected_renderer_id ?? "Unknown"}</dd>
      </div>
      <div>
        <dt>Reason code</dt>
        <dd>{decision.reason_code ?? "Unknown"}</dd>
      </div>
      <div>
        <dt>Cost / risk</dt>
        <dd>
          {decision.cost_class ?? "Unknown"} / {decision.risk_level ?? "Unknown"}
        </dd>
      </div>
      {decision.created_at ? (
        <div>
          <dt>Created</dt>
          <dd>{decision.created_at}</dd>
        </div>
      ) : null}
    </dl>
  );
}

export function RouteDecisionPanel({ projectId }: { projectId: string }) {
  const [territory, setTerritory] = useState("");
  const [offlineCapable, setOfflineCapable] = useState(false);
  const [lookupId, setLookupId] = useState("");
  const [activeLookupId, setActiveLookupId] = useState<string | undefined>();
  const create = useCreateRouteDecisionMutation(projectId);
  const lookup = useRouteDecisionLookup(activeLookupId);

  return (
    <div className="sg-planning-form">
      <form
        className="sg-planning-form"
        aria-label="Create route decision"
        onSubmit={(event) => {
          event.preventDefault();
          if (!territory.trim()) return;
          void create.mutateAsync({ input: { territory: territory.trim(), offline_capable: offlineCapable }, idempotencyKey: createProjectApiClient().createIdempotencyKey() }).catch(() => {});
        }}
      >
        <Field id="route-decision-territory" label="Territory" value={territory} onChange={(event) => setTerritory(event.target.value)} required maxLength={160} />
        <label>
          <input type="checkbox" checked={offlineCapable} onChange={(event) => setOfflineCapable(event.target.checked)} /> Offline-capable only
        </label>
        <Button type="submit" loading={create.isPending} disabled={create.isPending}>
          Create route decision
        </Button>
      </form>
      {create.isError ? (
        <Alert tone="danger" title="Route decision failed">
          <p>{create.error instanceof Error ? create.error.message : "No route decision could be created."}</p>
        </Alert>
      ) : null}
      {create.data ? (
        <div>
          <p className="sg-micro-label">Decision created — no client-side routing authority; this is the server&apos;s real decision.</p>
          <DecisionSummary decision={create.data.body} />
        </div>
      ) : null}
      <form
        className="sg-planning-form"
        aria-label="Look up a route decision by ID"
        onSubmit={(event) => {
          event.preventDefault();
          setActiveLookupId(lookupId.trim() || undefined);
        }}
      >
        <Field id="route-decision-lookup" label="Look up decision by ID" value={lookupId} onChange={(event) => setLookupId(event.target.value)} maxLength={160} />
        <Button type="submit">Look up</Button>
      </form>
      {activeLookupId && lookup.isLoading ? <p role="status">Loading…</p> : null}
      {activeLookupId && lookup.isError ? (
        <Alert tone="danger" title="Decision not found">
          <p>No accessible route decision matches that ID.</p>
        </Alert>
      ) : null}
      {lookup.data ? <DecisionSummary decision={lookup.data.body} /> : null}
    </div>
  );
}
