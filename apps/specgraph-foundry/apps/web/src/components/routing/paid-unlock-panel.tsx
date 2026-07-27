"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { createProjectApiClient } from "@/lib/projects/api";
import { canConfirmPaidUnlock, costSummary, riskWarning } from "@/lib/routing/cost";
import { useGrantPaidUnlockMutation } from "@/lib/routing/mutations";
import type { Provider } from "@/lib/routing/schemas";

export function PaidUnlockPanel({ projectId, providers }: { projectId: string; providers: Provider[] }) {
  const [providerId, setProviderId] = useState("");
  const [actorId, setActorId] = useState("");
  const [reason, setReason] = useState("");
  const [ttlSeconds, setTtlSeconds] = useState(3600);
  const [confirming, setConfirming] = useState(false);
  const mutation = useGrantPaidUnlockMutation(projectId);
  const selectedProvider = providers.find((provider) => provider.id === providerId);
  const canConfirm = canConfirmPaidUnlock(selectedProvider);
  const risk = riskWarning(selectedProvider);

  async function confirmUnlock() {
    try {
      await mutation.mutateAsync({
        input: { actor_id: actorId, reason, ttl_seconds: ttlSeconds, provider_id: providerId || undefined },
        idempotencyKey: createProjectApiClient().createIdempotencyKey(),
      });
      setConfirming(false);
    } catch {
      // Preserve the confirmation state; mutation.isError already surfaces the failure.
    }
  }

  return (
    <div className="sg-planning-form" aria-label="Grant paid route unlock">
      <Alert tone="warning" title="Paid routing requires explicit confirmation">
        <p>Unlocks are never purchased or granted automatically. Select a real provider to see its cost class before confirming.</p>
      </Alert>
      <div className="sg-field">
        <label htmlFor="paid-unlock-provider">Provider</label>
        <select id="paid-unlock-provider" className="sg-select" value={providerId} onChange={(event) => { setProviderId(event.target.value); setConfirming(false); }}>
          <option value="">Select a provider</option>
          {providers.map((provider) => (
            <option key={provider.id} value={provider.id}>
              {provider.name ?? provider.id}
            </option>
          ))}
        </select>
      </div>
      <p role="status">{costSummary(selectedProvider)}</p>
      {risk ? (
        <Alert tone="warning" title="Risk warning">
          <p>{risk}</p>
        </Alert>
      ) : null}
      <Field id="paid-unlock-actor" label="Actor ID" value={actorId} onChange={(event) => setActorId(event.target.value)} required maxLength={160} />
      <Field id="paid-unlock-reason" label="Reason" value={reason} onChange={(event) => setReason(event.target.value)} required maxLength={500} />
      <label htmlFor="paid-unlock-ttl">
        TTL (seconds)
        <input id="paid-unlock-ttl" className="sg-input" type="number" min={1} value={ttlSeconds} onChange={(event) => setTtlSeconds(Number(event.target.value))} />
      </label>
      {!confirming ? (
        <Button type="button" disabled={!canConfirm || !actorId.trim() || !reason.trim()} onClick={() => setConfirming(true)}>
          Review unlock
        </Button>
      ) : (
        <Alert tone="danger" title="Confirm paid route unlock">
          <p>
            Grant provider <strong>{selectedProvider?.name ?? providerId}</strong> ({costSummary(selectedProvider)}) a paid unlock for {ttlSeconds} seconds. This action is not reversible from
            this screen.
          </p>
          <div className="sg-graph-command-group">
            <Button type="button" loading={mutation.isPending} onClick={() => void confirmUnlock()}>
              Confirm and grant unlock
            </Button>
            <Button type="button" variant="secondary" onClick={() => setConfirming(false)}>
              Cancel
            </Button>
          </div>
        </Alert>
      )}
      {mutation.isError ? (
        <Alert tone="danger" title="Unlock failed">
          <p>{mutation.error instanceof Error ? mutation.error.message : "The paid unlock could not be granted."}</p>
        </Alert>
      ) : null}
      {mutation.data ? (
        <Alert tone="info" title="Unlock granted">
          <dl>
            <div>
              <dt>Unlock ID</dt>
              <dd className="sg-mono">{mutation.data.body.id}</dd>
            </div>
            {mutation.data.body.expires_at ? (
              <div>
                <dt>Expires</dt>
                <dd>{String(mutation.data.body.expires_at)}</dd>
              </div>
            ) : null}
          </dl>
        </Alert>
      ) : null}
    </div>
  );
}
