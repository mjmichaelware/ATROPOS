import { MetricOrbit } from "@/components/visual/metric-orbit";

// Keys match ProjectWorkspaceService.get()'s `counts` dict exactly
// (src/specgraph_foundry/http_api/workspace.py) - the workspace response
// nests every count under `workspace.counts`, not as flat `*_count` keys.
const COUNT_LABELS: Record<string, string> = {
  documents: "Sources",
  sections: "Sections",
  chunks: "Chunks",
  atoms: "Atoms",
  dimensions: "Dimensions",
  open_dimensions: "Open dimensions",
  resolved_dimensions: "Resolved dimensions",
  not_applicable_dimensions: "Not applicable dimensions",
  research_tasks: "Research tasks",
  pending_research_tasks: "Pending research tasks",
  completed_research_tasks: "Completed research tasks",
  authority_relations: "Relations",
  plans: "Plans",
  verified_plans: "Verified plans",
  bindings: "Bindings",
  enabled_bindings: "Enabled bindings",
  exports: "Exports",
  verified_exports: "Verified exports",
  execution_runs: "Execution runs",
  verified_execution_runs: "Verified execution runs",
  providers: "Providers",
  renderers: "Renderers",
  route_decisions: "Route decisions",
};

function num(counts: Record<string, unknown>, key: string): number {
  const value = counts[key];
  return typeof value === "number" ? value : 0;
}

function percentOf(numerator: number, denominator: number): number {
  if (denominator <= 0) return 0;
  return Math.round((numerator / denominator) * 100);
}

export function ProjectCounts({ workspace }: { workspace: Record<string, unknown> }) {
  const counts = (workspace.counts as Record<string, unknown> | undefined) ?? {};
  const dimensions = num(counts, "dimensions");
  const resolvedDimensions = num(counts, "resolved_dimensions") + num(counts, "not_applicable_dimensions");
  const plans = num(counts, "plans");
  const verifiedPlans = num(counts, "verified_plans");
  const exportsTotal = num(counts, "exports");
  const verifiedExports = num(counts, "verified_exports");
  const runsTotal = num(counts, "execution_runs");
  const verifiedRuns = num(counts, "verified_execution_runs");

  return (
    <section className="sg-card" aria-labelledby="counts-title">
      <h2 id="counts-title">Progress overview</h2>
      <div className="sg-research-summary" aria-label="Project progress at a glance">
        <MetricOrbit label="Research closed" value={percentOf(resolvedDimensions, dimensions)} />
        <MetricOrbit label="Plans verified" value={percentOf(verifiedPlans, plans)} />
        <MetricOrbit label="Exports verified" value={percentOf(verifiedExports, exportsTotal)} />
        <MetricOrbit label="Runs verified" value={percentOf(verifiedRuns, runsTotal)} />
      </div>
      <dl className="sg-counts">
        {Object.entries(COUNT_LABELS).map(([key, label]) => (
          <div key={key}>
            <dt>{label}</dt>
            <dd>{typeof counts[key] === "number" ? String(counts[key]) : "0"}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
