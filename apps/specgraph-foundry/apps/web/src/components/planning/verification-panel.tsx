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
    return <p className="sg-muted">Select or synthesize a plan to verify it.</p>;
  }
  const findings = (plan.findings ?? []) as PlanFinding[];
  return (
    <div className="sg-planning-form" aria-label="Plan verification">
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
