"use client";

import { Button } from "@/components/ui/button";
import type { FindingFilter } from "@/lib/planning/findings";
import type { PlanDetail, PlanFinding } from "@/lib/planning/schemas";
import { FindingsList } from "./findings-list";
import { PlanStatusBadge } from "./plan-status-badge";

export function VerificationPanel({
  plan,
  pending,
  onVerify,
  filter,
  onFilterChange,
  loadedNodeIds,
  onFocusNode,
}: {
  plan: PlanDetail | undefined;
  pending: boolean;
  onVerify: () => Promise<void>;
  filter: FindingFilter;
  onFilterChange: (filter: FindingFilter) => void;
  loadedNodeIds: ReadonlySet<string>;
  onFocusNode: (nodeId: string) => void;
}) {
  if (!plan) {
    return <p className="sg-muted">Build a plan on the Plans tab first — you&apos;ll be able to double-check it here once it exists.</p>;
  }
  const findings = (plan.findings ?? []) as PlanFinding[];
  return (
    <div className="sg-planning-form" aria-label="Plan verification">
      <p className="sg-muted">
        Verifying checks the plan&apos;s execution graph for real problems — cycles, missing dependencies, steps still blocked on open research — before it&apos;s trusted enough to export or hand
        off. A plan only reaches VERIFIED status once every finding below is resolved.
      </p>
      <div className="sg-graph-command-group">
        <PlanStatusBadge status={plan.status} />
        <Button type="button" loading={pending} disabled={pending} onClick={() => void onVerify()}>
          Verify plan
        </Button>
      </div>
      <FindingsList findings={findings} filter={filter} onFilterChange={onFilterChange} loadedNodeIds={loadedNodeIds} onFocusNode={onFocusNode} />
    </div>
  );
}
