"use client";

import { useState } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { SpecGraphApiError } from "@/lib/api/errors";
import { useSetRoutingPolicyMutation } from "@/lib/routing/mutations";
import { useRoutingPolicy } from "@/lib/routing/queries";
import type { RoutingPolicy } from "@/lib/routing/schemas";

function RoutingPolicyForm({
  projectId,
  policy,
  etag,
  onReload,
}: {
  projectId: string;
  policy: RoutingPolicy;
  etag: string | undefined;
  onReload: () => void;
}) {
  const mutation = useSetRoutingPolicyMutation(projectId);
  const [allowOfflineDegraded, setAllowOfflineDegraded] = useState(Boolean(policy.allow_offline_degraded));
  const [paidEmergencyEnabled, setPaidEmergencyEnabled] = useState(Boolean(policy.paid_emergency_enabled));
  const [maxPaidDecisions, setMaxPaidDecisions] = useState(Number(policy.max_paid_decisions_per_unlock ?? 0));
  const [conflict, setConflict] = useState<string | undefined>();

  async function save(event: React.FormEvent) {
    event.preventDefault();
    setConflict(undefined);
    if (!etag) {
      setConflict("A current version is required before saving. Reload and try again.");
      return;
    }
    try {
      await mutation.mutateAsync({
        input: { allow_offline_degraded: allowOfflineDegraded, paid_emergency_enabled: paidEmergencyEnabled, max_paid_decisions_per_unlock: maxPaidDecisions },
        ifMatch: etag,
      });
    } catch (thrown) {
      if (thrown instanceof SpecGraphApiError && (thrown.status === 412 || thrown.status === 428)) {
        setConflict("The routing policy changed on the server since it was loaded. Reload to see the latest version before saving again.");
      }
    }
  }

  return (
    <form className="sg-planning-form" onSubmit={(event) => void save(event)} aria-label="Routing policy">
      <label>
        <input type="checkbox" checked={allowOfflineDegraded} onChange={(event) => setAllowOfflineDegraded(event.target.checked)} /> Allow offline degraded routing
      </label>
      <label>
        <input type="checkbox" checked={paidEmergencyEnabled} onChange={(event) => setPaidEmergencyEnabled(event.target.checked)} /> Enable paid emergency routing
      </label>
      <label htmlFor="max-paid-decisions">
        Max paid decisions per unlock
        <input
          id="max-paid-decisions"
          className="sg-input"
          type="number"
          min={0}
          value={maxPaidDecisions}
          onChange={(event) => setMaxPaidDecisions(Number(event.target.value))}
        />
      </label>
      {conflict ? (
        <Alert tone="warning" title="Conflict">
          <p>{conflict}</p>
          <Button type="button" variant="secondary" onClick={onReload}>
            Reload
          </Button>
        </Alert>
      ) : null}
      {mutation.isError && !conflict ? (
        <Alert tone="danger" title="Save failed">
          <p>{mutation.error instanceof Error ? mutation.error.message : "The routing policy could not be saved. Its previous value is unchanged."}</p>
        </Alert>
      ) : null}
      <Button type="submit" loading={mutation.isPending} disabled={mutation.isPending}>
        Save policy
      </Button>
    </form>
  );
}

export function RoutingPolicyPanel({ projectId }: { projectId: string }) {
  const policy = useRoutingPolicy(projectId);

  if (policy.isLoading) {
    return <Skeleton style={{ height: "10rem" }} />;
  }
  if (policy.isError || !policy.data) {
    return (
      <Alert tone="danger" title="Routing policy unavailable">
        <p>The routing policy could not load.</p>
      </Alert>
    );
  }

  return (
    <RoutingPolicyForm
      key={policy.data.etag ?? "no-etag"}
      projectId={projectId}
      policy={policy.data.body}
      etag={policy.data.etag}
      onReload={() => void policy.refetch()}
    />
  );
}
