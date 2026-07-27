"use client";

import { Button } from "@/components/ui/button";
import type { PlanSummary } from "@/lib/planning/schemas";
import { PlanStatusBadge } from "./plan-status-badge";

export function PlanHistory({ plans, selectedPlanId, onSelect }: { plans: PlanSummary[]; selectedPlanId?: string; onSelect: (planId: string) => void }) {
  if (plans.length === 0) {
    return <p className="sg-muted">No plans have been synthesized yet.</p>;
  }
  return (
    <ul className="sg-plan-history" aria-label="Plan history">
      {plans.map((plan) => {
        const isSelected = plan.id === selectedPlanId;
        return (
          <li key={plan.id}>
            <Button type="button" variant={isSelected ? "verified" : "quiet"} aria-pressed={isSelected} onClick={() => onSelect(plan.id)}>
              <span className="sg-mono">{plan.id.slice(0, 8)}</span>
              <PlanStatusBadge status={plan.status} />
              {typeof plan.created_at === "string" ? <span className="sg-micro-label">{new Date(plan.created_at).toLocaleString()}</span> : null}
            </Button>
          </li>
        );
      })}
    </ul>
  );
}
