"use client";

import { Tabs } from "@/components/ui/tabs";
import type { FindingFilter } from "@/lib/planning/findings";
import type { AuthorityRelation, PlanDetail, PlanningWorkspace, PlanSummary, RelationInput } from "@/lib/planning/schemas";
import { PlanHistory } from "./plan-history";
import { PlanStatusBadge } from "./plan-status-badge";
import { RelationForm } from "./relation-form";
import { SynthesisPanel } from "./synthesis-panel";
import { VerificationPanel } from "./verification-panel";

export type PlanningTab = "overview" | "relations" | "plans" | "verify";

function PlanningOverview({ workspace }: { workspace: PlanningWorkspace | undefined }) {
  if (!workspace) {
    return <p className="sg-muted">Planning summary is unavailable.</p>;
  }
  const counts = workspace.counts ?? {};
  return (
    <div className="sg-planning-overview">
      <dl>
        <div>
          <dt>Authority relations</dt>
          <dd>{counts.authority_relations ?? "unknown"}</dd>
        </div>
        <div>
          <dt>Plans</dt>
          <dd>
            {counts.plans ?? "unknown"} ({counts.draft_plans ?? 0} draft, {counts.blocked_plans ?? 0} blocked, {counts.verified_plans ?? 0} verified)
          </dd>
        </div>
        <div>
          <dt>Authority graph</dt>
          <dd>
            {counts.authority_nodes ?? "unknown"} nodes, {counts.authority_edges ?? "unknown"} edges
          </dd>
        </div>
        <div>
          <dt>Execution graph</dt>
          <dd>
            {counts.execution_nodes ?? "unknown"} nodes, {counts.execution_edges ?? "unknown"} edges ({counts.ready_nodes ?? 0} server-ready, {counts.blocked_nodes ?? 0} blocked)
          </dd>
        </div>
      </dl>
      {workspace.latest_plan ? (
        <p>
          Latest plan: <span className="sg-mono">{String(workspace.latest_plan.id).slice(0, 8)}</span> <PlanStatusBadge status={workspace.latest_plan.status} />
        </p>
      ) : (
        <p className="sg-muted">No plan has been synthesized for this project yet.</p>
      )}
      <p className="sg-muted">
        Unresolved research dimensions block synthesis unless open research is explicitly allowed. Allowing open research does not resolve those dimensions — resolve them from the
        Research workspace, or make an explicit informed choice under the Plans tab. Readiness and blocking behavior remain server-authoritative.
      </p>
    </div>
  );
}

export function PlanningRail({
  projectId,
  tab,
  onTabChange,
  workspace,
  atomOptions,
  relations,
  createRelationPending,
  onCreateRelation,
  plans,
  selectedPlanId,
  onSelectPlan,
  synthesizePending,
  onSynthesize,
  selectedPlan,
  verifyPending,
  onVerify,
  findingFilter,
  onFindingFilterChange,
  loadedNodeIds,
  onFocusNode,
}: {
  projectId: string;
  tab: PlanningTab;
  onTabChange: (tab: PlanningTab) => void;
  workspace: PlanningWorkspace | undefined;
  atomOptions: Array<{ id: string; label: string }>;
  relations: AuthorityRelation[];
  createRelationPending: boolean;
  onCreateRelation: (input: RelationInput) => Promise<void>;
  plans: PlanSummary[];
  selectedPlanId: string | undefined;
  onSelectPlan: (planId: string) => void;
  synthesizePending: boolean;
  onSynthesize: (allowOpenResearch: boolean) => Promise<void>;
  selectedPlan: PlanDetail | undefined;
  verifyPending: boolean;
  onVerify: () => Promise<void>;
  findingFilter: FindingFilter;
  onFindingFilterChange: (filter: FindingFilter) => void;
  loadedNodeIds: ReadonlySet<string>;
  onFocusNode: (nodeId: string) => void;
}) {
  return (
    <section className="sg-planning-rail" aria-label="Planning">
      <Tabs
        label="Planning views"
        value={tab}
        onChange={onTabChange}
        tabs={[
          { value: "overview", label: "Overview", panel: <PlanningOverview workspace={workspace} /> },
          {
            value: "relations",
            label: "Relations",
            panel: (
              <RelationForm
                atomOptions={atomOptions}
                relations={relations}
                pending={createRelationPending}
                onSubmit={onCreateRelation}
                onCancel={() => onTabChange("overview")}
              />
            ),
          },
          {
            value: "plans",
            label: "Plans",
            panel: (
              <div className="sg-planning-form">
                <SynthesisPanel pending={synthesizePending} onSynthesize={onSynthesize} />
                <PlanHistory projectId={projectId} plans={plans} selectedPlanId={selectedPlanId} onSelect={onSelectPlan} />
              </div>
            ),
          },
          {
            value: "verify",
            label: "Verification",
            panel: (
              <VerificationPanel
                plan={selectedPlan}
                pending={verifyPending}
                onVerify={onVerify}
                filter={findingFilter}
                onFilterChange={onFindingFilterChange}
                loadedNodeIds={loadedNodeIds}
                onFocusNode={onFocusNode}
              />
            ),
          },
        ]}
      />
    </section>
  );
}
